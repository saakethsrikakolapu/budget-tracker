package com.saaketh.budget.imports;

import com.saaketh.budget.account.Account;
import com.saaketh.budget.account.AccountService;
import com.saaketh.budget.imports.parser.ParsedTransaction;
import com.saaketh.budget.imports.parser.StatementParser;
import com.saaketh.budget.transaction.Transaction;
import com.saaketh.budget.transaction.TransactionRepository;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Validates an uploaded statement, parses it, and saves its transactions. */
@Service
public class ImportService {

    /** Also enforced earlier by spring.servlet.multipart.max-file-size; this is the backstop. */
    static final long MAX_FILE_BYTES = 2 * 1024 * 1024;

    public record ImportResult(Long importBatchId, Long accountId, String fileName, int importedCount) {
    }

    private final AccountService accountService;
    private final StatementParser parser;
    private final ImportBatchRepository importBatchRepository;
    private final TransactionRepository transactionRepository;

    public ImportService(AccountService accountService, StatementParser parser,
            ImportBatchRepository importBatchRepository, TransactionRepository transactionRepository) {
        this.accountService = accountService;
        this.parser = parser;
        this.importBatchRepository = importBatchRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * All-or-nothing: @Transactional means that if anything fails partway, every insert in this
     * method is rolled back and the database looks as if the upload never happened.
     */
    @Transactional
    public ImportResult importStatement(Long userId, Long accountId, MultipartFile file) {
        Account account = accountService.getOwned(userId, accountId);
        String fileName = validateFile(file);
        List<ParsedTransaction> parsed = parser.parse(readUtf8(file));

        ImportBatch batch = importBatchRepository.save(new ImportBatch(userId, account.getId(), fileName, parsed.size()));
        List<Transaction> transactions = parsed.stream()
                .map(row -> new Transaction(userId, account.getId(), batch.getId(), row.transactionDate(),
                        row.postedDate(), row.description(), row.amount(), row.bankCategory()))
                .toList();
        transactionRepository.saveAll(transactions);

        return new ImportResult(batch.getId(), account.getId(), fileName, transactions.size());
    }

    /** Returns a safe file name to store. */
    private static String validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidUploadException("The file is empty.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidUploadException("The file is larger than 2 MB.");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        // Browsers may send a full path ("C:\fakepath\x.csv"); keep only the last part.
        String name = original.replaceAll(".*[/\\\\]", "").trim();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new InvalidUploadException("Only .csv files can be imported.");
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    /** Decodes strictly, so a binary file renamed to .csv is rejected instead of turning into garbage. */
    private static String readUtf8(MultipartFile file) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new InvalidUploadException("The file is not a text CSV file (expected UTF-8).");
        } catch (IOException e) {
            throw new InvalidUploadException("The file could not be read.");
        }
    }
}
