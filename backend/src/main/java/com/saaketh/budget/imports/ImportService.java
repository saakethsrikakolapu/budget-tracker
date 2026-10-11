package com.saaketh.budget.imports;

import com.saaketh.budget.account.Account;
import com.saaketh.budget.account.AccountService;
import com.saaketh.budget.category.CategoryAssigner;
import com.saaketh.budget.common.NotFoundException;
import com.saaketh.budget.imports.csv.ColumnMapping;
import com.saaketh.budget.imports.csv.MappedCsvParser;
import com.saaketh.budget.imports.parser.ParsedTransaction;
import com.saaketh.budget.imports.parser.StatementParseException;
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
import java.time.LocalDate;
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

    /**
     * importBatchId is null when every row was a duplicate (no import record is created).
     *
     * @param uncategorizedCount new transactions that no rule, bank label, or known merchant could
     *     categorize (the page offers to review them)
     */
    public record ImportResult(Long importBatchId, Long accountId, String fileName, int importedCount, int skippedCount,
            int uncategorizedCount) {
    }

    public record ImportSummary(Long id, Long accountId, String accountName, String fileName,
            int importedCount, int skippedCount, Instant createdAt) {
    }

    /** One column of the file, for the preview's column pickers. */
    public record PreviewColumn(int index, String name, List<String> samples) {
    }

    /**
     * What the app would import, without saving anything.
     *
     * @param mapping null if the columns couldn't be worked out (then errors explains why)
     * @param transactions the first few rows as they would be imported
     * @param skippedRows rows with no amount (e.g. "Beginning balance" lines)
     * @param firstDate earliest transaction date in the file (null if nothing could be read)
     * @param lastDate latest transaction date in the file
     * @param alreadyImported rows already in the chosen account, which an import would skip (null
     *     when no account was given)
     */
    public record Preview(StatementReader.Source source, ColumnMapping mapping, List<PreviewColumn> columns,
            List<ParsedTransaction> transactions, int transactionCount, int skippedRows,
            List<String> warnings, List<String> errors, LocalDate firstDate, LocalDate lastDate,
            Integer alreadyImported) {

        static Preview failed(StatementReader.Source source, ColumnMapping mapping, List<PreviewColumn> columns,
                List<String> warnings, List<String> errors) {
            return new Preview(source, mapping, columns, List.of(), 0, 0, warnings, errors, null, null, null);
        }
    }

    static final int PREVIEW_ROWS = 8;

    private final AccountService accountService;
    private final StatementReader statementReader;
    private final ImportBatchRepository importBatchRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryAssigner categoryAssigner;

    public ImportService(AccountService accountService, StatementReader statementReader,
            ImportBatchRepository importBatchRepository, TransactionRepository transactionRepository,
            CategoryAssigner categoryAssigner) {
        this.accountService = accountService;
        this.statementReader = statementReader;
        this.importBatchRepository = importBatchRepository;
        this.transactionRepository = transactionRepository;
        this.categoryAssigner = categoryAssigner;
    }

    /**
     * All-or-nothing: @Transactional means that if anything fails partway, every insert in this
     * method is rolled back and the database looks as if the upload never happened.
     */
    @Transactional
    public ImportResult importStatement(Long userId, Long accountId, MultipartFile file, String mappingJson,
            boolean rememberFormat) {
        Account account = accountService.getOwned(userId, accountId);
        String fileName = validateFile(file);
        StatementReader.Plan plan = statementReader.plan(userId, readUtf8(file), mappingJson);
        if (plan.mapping() == null) {
            throw new StatementParseException(plan.failure() + " Use the preview to choose the columns.");
        }
        List<ParsedTransaction> parsed = MappedCsvParser.parse(plan.table(), plan.mapping()).transactions();
        if (rememberFormat && plan.source() != StatementReader.Source.SAVED) {
            statementReader.remember(userId, plan.signature(), plan.mapping());
        }

        List<NewRow> newRows = findNewRows(account.getId(), parsed);
        int skipped = parsed.size() - newRows.size();
        if (newRows.isEmpty()) {
            // Everything was already imported: nothing to save, and no empty import record to undo.
            return new ImportResult(null, account.getId(), fileName, 0, skipped, 0);
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
                            categoryAssigner.assign(categories, n.row().description(), n.row().bankCategory(),
                                    n.row().amount());
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
        int uncategorized = (int) transactions.stream().filter(t -> t.getCategoryId() == null).count();
        return new ImportResult(batch.getId(), account.getId(), fileName, transactions.size(), skipped, uncategorized);
    }

    /**
     * Reads the file the same way an import would and reports what it found, without saving.
     * Never throws for a bad file: problems come back in errors so the page can show them.
     */
    @Transactional(readOnly = true)
    public Preview preview(Long userId, MultipartFile file, String mappingJson, Long accountId) {
        validateFile(file);
        // Checked before reading the file, so another user's account id is a plain 404.
        Account account = accountId == null ? null : accountService.getOwned(userId, accountId);
        StatementReader.Plan plan;
        try {
            plan = statementReader.plan(userId, readUtf8(file), mappingJson);
        } catch (StatementParseException e) {
            return Preview.failed(null, null, List.of(), List.of(), e.getErrors());
        }
        List<PreviewColumn> columns = columns(plan);
        if (plan.mapping() == null) {
            return Preview.failed(plan.source(), null, columns, plan.warnings(), List.of(plan.failure()));
        }
        try {
            MappedCsvParser.Result result = MappedCsvParser.parse(plan.table(), plan.mapping());
            List<ParsedTransaction> all = result.transactions();
            List<ParsedTransaction> sample = all.stream().limit(PREVIEW_ROWS).toList();
            LocalDate first = all.stream().map(ParsedTransaction::transactionDate).min(LocalDate::compareTo).orElse(null);
            LocalDate last = all.stream().map(ParsedTransaction::transactionDate).max(LocalDate::compareTo).orElse(null);
            // Same duplicate check an import runs, so the page can say "3 already imported" up front.
            Integer alreadyImported = account == null ? null : all.size() - findNewRows(account.getId(), all).size();
            return new Preview(plan.source(), plan.mapping(), columns, sample, all.size(), result.skippedRows(),
                    plan.warnings(), List.of(), first, last, alreadyImported);
        } catch (StatementParseException e) {
            return Preview.failed(plan.source(), plan.mapping(), columns, plan.warnings(), e.getErrors());
        }
    }

    /** Each column's name and a few example values, so the user can recognize it. */
    private static List<PreviewColumn> columns(StatementReader.Plan plan) {
        int firstData = plan.layout().headerRow() + 1;
        List<PreviewColumn> columns = new ArrayList<>();
        for (int c = 0; c < plan.layout().columnNames().size(); c++) {
            final int column = c;
            List<String> samples = plan.table().rows().stream()
                    .skip(firstData)
                    .map(r -> r.cell(column))
                    .filter(v -> !v.isEmpty())
                    .limit(3)
                    .toList();
            columns.add(new PreviewColumn(c, plan.layout().columnNames().get(c), samples));
        }
        return columns;
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
