package com.saaketh.budget.imports;

import com.saaketh.budget.account.Account;
import com.saaketh.budget.account.AccountService;
import com.saaketh.budget.category.CategoryAssigner;
import com.saaketh.budget.common.NotFoundException;
import com.saaketh.budget.imports.parser.ParsedTransaction;
import com.saaketh.budget.imports.parser.StatementParser;
import com.saaketh.budget.transaction.Transaction;
import com.saaketh.budget.transaction.TransactionFingerprint;
import com.saaketh.budget.transaction.TransactionRepository;
import com.saaketh.budget.transaction.TransactionRepository.FingerprintStats;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Validates an uploaded statement, skips already-imported rows, and saves the rest. Also lists and undoes imports. */
@Service
public class ImportService {

    /** Also enforced earlier by spring.servlet.multipart.max-file-size; this is the backstop. */
    static final long MAX_FILE_BYTES = 2 * 1024 * 1024;

    /** importBatchId is null when every row was a duplicate (no import record is created). */
    public record ImportResult(Long importBatchId, Long accountId, String fileName, int importedCount, int skippedCount) {
    }

    public record ImportSummary(Long id, Long accountId, String accountName, String fileName,
            int importedCount, int skippedCount, Instant createdAt) {
    }

    private final AccountService accountService;
    private final StatementParser parser;
    private final ImportBatchRepository importBatchRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryAssigner categoryAssigner;

    public ImportService(AccountService accountService, StatementParser parser,
            ImportBatchRepository importBatchRepository, TransactionRepository transactionRepository,
            CategoryAssigner categoryAssigner) {
        this.accountService = accountService;
        this.parser = parser;
        this.importBatchRepository = importBatchRepository;
        this.transactionRepository = transactionRepository;
        this.categoryAssigner = categoryAssigner;
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

        List<NewRow> newRows = findNewRows(account.getId(), parsed);
        int skipped = parsed.size() - newRows.size();
        if (newRows.isEmpty()) {
            // Everything was already imported: nothing to save, and no empty import record to undo.
            return new ImportResult(null, account.getId(), fileName, 0, skipped);
        }

        ImportBatch batch = importBatchRepository.save(
                new ImportBatch(userId, account.getId(), fileName, newRows.size(), skipped));
        CategoryAssigner.Context categories = categoryAssigner.contextFor(userId);
        List<Transaction> transactions = newRows.stream()
                .map(n -> {
                    Transaction t = new Transaction(userId, account.getId(), batch.getId(), n.row().transactionDate(),
                            n.row().postedDate(), n.row().description(), n.row().amount(), n.row().bankCategory(),
                            n.occurrence());
                    CategoryAssigner.Assignment a =
                            categoryAssigner.assign(categories, n.row().description(), n.row().bankCategory());
                    t.setCategory(a.categoryId(), a.source());
                    return t;
                })
                .toList();
        try {
            transactionRepository.saveAllAndFlush(transactions);
        } catch (DataIntegrityViolationException e) {
            // The unique (account, fingerprint, occurrence) constraint fired: another upload into this
            // account saved the same rows a moment ago (e.g. a double-click). This one is rolled back.
            throw new ImportConflictException();
        }
        return new ImportResult(batch.getId(), account.getId(), fileName, transactions.size(), skipped);
    }

    private record NewRow(ParsedTransaction row, int occurrence) {
    }

    /**
     * Count-based duplicate detection. If the account already has k copies of a purchase and the
     * file has m, the first k in the file are "already imported" and the remaining m - k are new.
     * This keeps genuinely repeated purchases (two identical coffees) while skipping overlap
     * between exports.
     */
    private List<NewRow> findNewRows(Long accountId, List<ParsedTransaction> parsed) {
        List<String> fingerprints = parsed.stream()
                .map(row -> TransactionFingerprint.of(row.transactionDate(), row.amount(), row.description()))
                .toList();

        Map<String, FingerprintStats> existing = transactionRepository
                .findFingerprintStats(accountId, fingerprints.stream().distinct().toList())
                .stream()
                .collect(Collectors.toMap(FingerprintStats::fingerprint, Function.identity()));

        Map<String, Integer> seenInFile = new HashMap<>();
        List<NewRow> newRows = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            String fingerprint = fingerprints.get(i);
            int seen = seenInFile.merge(fingerprint, 1, Integer::sum); // 1 for the first copy in this file
            FingerprintStats stats = existing.get(fingerprint);
            long alreadyImported = stats == null ? 0 : stats.count();
            if (seen > alreadyImported) {
                // Number new copies after the highest existing occurrence. (After an undo there can be
                // gaps, so "count + 1" could collide with an occurrence that's still in use.)
                int maxOccurrence = stats == null ? 0 : stats.maxOccurrence();
                newRows.add(new NewRow(parsed.get(i), maxOccurrence + (int) (seen - alreadyImported)));
            }
        }
        return newRows;
    }

    @Transactional(readOnly = true)
    public List<ImportSummary> list(Long userId) {
        Map<Long, String> accountNames = accountService.list(userId).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
        return importBatchRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(batch -> new ImportSummary(batch.getId(), batch.getAccountId(),
                        accountNames.get(batch.getAccountId()), batch.getFileName(),
                        batch.getRowCount(), batch.getSkippedCount(), batch.getCreatedAt()))
                .toList();
    }

    /** Undo an import: the database's ON DELETE CASCADE removes exactly the transactions it added. */
    @Transactional
    public void delete(Long userId, Long importBatchId) {
        ImportBatch batch = importBatchRepository.findByIdAndUserId(importBatchId, userId)
                .orElseThrow(() -> new NotFoundException("Import not found"));
        importBatchRepository.delete(batch);
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
