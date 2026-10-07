package com.saaketh.budget.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saaketh.budget.TestUsers;
import com.saaketh.budget.TestcontainersConfiguration;
import com.saaketh.budget.transaction.Transaction;
import com.saaketh.budget.transaction.TransactionFingerprint;
import com.saaketh.budget.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Duplicate detection across overlapping exports, and undoing imports. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DuplicateImportIntegrationTest {

    private static final String HEADER = "Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit\n";
    private static final String COFFEE = "2026-09-22,2026-09-23,1234,CAMPUS COFFEE CO,Dining,4.75,\n";
    private static final String PIZZA = "2026-09-28,2026-09-29,1234,PIZZA PALACE,Dining,18.20,\n";
    private static final String STREAMFLIX = "2026-10-03,2026-10-04,1234,STREAMFLIX MONTHLY,Entertainment,15.49,\n";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reuploadingTheSameFileImportsNothing() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long account = createAccount(session, "Card");
        String csv = HEADER + COFFEE + COFFEE + PIZZA;

        upload(session, account, "sep.csv", csv).andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(3))
                .andExpect(jsonPath("$.skippedCount").value(0));
        long batchesAfterFirst = importBatchRepository.count();

        upload(session, account, "sep.csv", csv).andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(3))
                .andExpect(jsonPath("$.importBatchId").isEmpty());

        assertThat(transactionRepository.countByAccountId(account)).isEqualTo(3);
        assertThat(importBatchRepository.count()).as("no empty import record").isEqualTo(batchesAfterFirst);
    }

    @Test
    void overlappingExportImportsOnlyTheNewRows() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long account = createAccount(session, "Card");
        // Export 1 was taken after the first coffee; by export 2 a second identical coffee happened.
        upload(session, account, "export1.csv", HEADER + COFFEE + PIZZA).andExpect(status().isCreated());

        upload(session, account, "export2.csv", HEADER + COFFEE + COFFEE + PIZZA + STREAMFLIX)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(2));

        assertThat(transactionRepository.countByAccountId(account)).isEqualTo(4);
        assertThat(coffeeOccurrences(account)).containsExactly(1, 2);
    }

    @Test
    void sameFileInADifferentAccountIsNotADuplicate() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long cardA = createAccount(session, "Card A");
        long cardB = createAccount(session, "Card B");
        String csv = HEADER + COFFEE + PIZZA;

        upload(session, cardA, "x.csv", csv).andExpect(status().isCreated());
        upload(session, cardB, "x.csv", csv).andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(2));
    }

    @Test
    void undoRemovesExactlyThatImportsTransactions() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long account = createAccount(session, "Card");
        upload(session, account, "export1.csv", HEADER + COFFEE + PIZZA).andExpect(status().isCreated());
        long secondBatch = batchId(upload(session, account, "export2.csv", HEADER + COFFEE + PIZZA + STREAMFLIX));

        mockMvc.perform(delete("/api/imports/" + secondBatch).session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(transactionRepository.countByAccountId(account)).isEqualTo(2);
        assertThat(transactionRepository.countByImportBatchId(secondBatch)).isZero();
        mockMvc.perform(get("/api/imports").session(session))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("export1.csv"));
        // After undo, the removed row counts as new again.
        upload(session, account, "export2.csv", HEADER + COFFEE + PIZZA + STREAMFLIX)
                .andExpect(jsonPath("$.importedCount").value(1));
    }

    @Test
    void undoThatLeavesAGapInOccurrencesStillWorks() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long account = createAccount(session, "Card");
        long first = batchId(upload(session, account, "a.csv", HEADER + COFFEE));          // occurrence 1
        upload(session, account, "b.csv", HEADER + COFFEE + COFFEE).andExpect(status().isCreated()); // adds occurrence 2

        // Remove occurrence 1; occurrence 2 remains, so the next copy must be numbered 3, not 2.
        mockMvc.perform(delete("/api/imports/" + first).session(session).with(csrf())).andExpect(status().isNoContent());
        upload(session, account, "b.csv", HEADER + COFFEE + COFFEE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(1));

        assertThat(coffeeOccurrences(account)).containsExactly(2, 3);
    }

    @Test
    void importHistoryIsNewestFirstWithCounts() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long account = createAccount(session, "Quicksilver");
        upload(session, account, "first.csv", HEADER + COFFEE).andExpect(status().isCreated());
        upload(session, account, "second.csv", HEADER + COFFEE + PIZZA).andExpect(status().isCreated());

        mockMvc.perform(get("/api/imports").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fileName").value("second.csv"))
                .andExpect(jsonPath("$[0].accountName").value("Quicksilver"))
                .andExpect(jsonPath("$[0].importedCount").value(1))
                .andExpect(jsonPath("$[0].skippedCount").value(1))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].fileName").value("first.csv"));
    }

    @Test
    void cannotSeeOrUndoAnotherUsersImports() throws Exception {
        MockHttpSession alice = TestUsers.registerAndLogin(mockMvc);
        MockHttpSession bob = TestUsers.registerAndLogin(mockMvc);
        long account = createAccount(alice, "Alice Card");
        long batch = batchId(upload(alice, account, "a.csv", HEADER + COFFEE));

        mockMvc.perform(get("/api/imports").session(bob)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/imports/" + batch).session(bob).with(csrf())).andExpect(status().isNotFound());

        assertThat(transactionRepository.countByImportBatchId(batch)).isEqualTo(1);
    }

    @Test
    void databaseRejectsASecondCopyOfTheSameOccurrence() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long account = createAccount(session, "Card");
        long batch = batchId(upload(session, account, "a.csv", HEADER + COFFEE));
        Transaction existing = transactionRepository.findByImportBatchIdOrderByIdAsc(batch).getFirst();

        // Simulates two simultaneous uploads both deciding "occurrence 1 is new".
        Transaction duplicate = new Transaction(existing.getUserId(), account, batch, existing.getTransactionDate(),
                existing.getPostedDate(), existing.getDescription(), existing.getAmount(), null, 1);

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** The V3 migration computed fingerprints for existing rows in SQL; it must agree with Java. */
    @Test
    void sqlBackfillFormulaMatchesJava() {
        String sql = """
                SELECT encode(sha256(convert_to(
                    ?::date::text || '|' || ?::numeric(12,2)::text || '|'
                        || upper(btrim(regexp_replace(?, '\\s+', ' ', 'g'))),
                    'UTF8')), 'hex')""";
        List<Object[]> samples = List.of(
                new Object[] {"2026-09-22", "-4.75", "CAMPUS COFFEE CO"},
                new Object[] {"2026-01-05", "250.00", "  Capital One   Autopay\tPymt "},
                new Object[] {"2026-12-31", "0.00", "x"},
                new Object[] {"2026-07-04", "-1234567.80", "BOOKS, SUPPLIES & MORE #12"});

        for (Object[] s : samples) {
            String fromSql = jdbcTemplate.queryForObject(sql, String.class, s[0], new BigDecimal((String) s[1]), s[2]);
            String fromJava = TransactionFingerprint.of(LocalDate.parse((String) s[0]), new BigDecimal((String) s[1]), (String) s[2]);
            assertThat(fromSql).as("fingerprint of %s", Arrays.toString(s)).isEqualTo(fromJava);
        }
    }

    // --- helpers ---

    private List<Integer> coffeeOccurrences(long accountId) {
        return jdbcTemplate.queryForList(
                "SELECT occurrence FROM transactions WHERE account_id = ? AND description = 'CAMPUS COFFEE CO' ORDER BY occurrence",
                Integer.class, accountId);
    }

    private long createAccount(MockHttpSession session, String name) throws Exception {
        String response = mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions upload(MockHttpSession session, long accountId, String fileName, String csv) throws Exception {
        return mockMvc.perform(multipart("/api/imports")
                .file(new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("accountId", String.valueOf(accountId))
                .session(session)
                .with(csrf()));
    }

    private static long batchId(ResultActions result) throws Exception {
        String response = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.importBatchId")).longValue();
    }
}
