package com.saaketh.budget.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saaketh.budget.TestUsers;
import com.saaketh.budget.TestcontainersConfiguration;
import com.saaketh.budget.transaction.Transaction;
import com.saaketh.budget.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Statement upload end to end: HTTP -> parser -> Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportIntegrationTest {

    /** The fake demo statement in the repo's samples/ folder (tests run from backend/). */
    private static final Path SAMPLE = Path.of("..", "samples", "capital-one-credit-card.csv");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Test
    void importsSampleStatement() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long accountId = createAccount(session, "Capital One Quicksilver");

        String response = upload(session, accountId, csvFile("statement.csv", Files.readAllBytes(SAMPLE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(10))
                .andExpect(jsonPath("$.fileName").value("statement.csv"))
                .andReturn().getResponse().getContentAsString();

        long batchId = ((Number) JsonPath.read(response, "$.importBatchId")).longValue();
        assertThat(importBatchRepository.findById(batchId).orElseThrow().getRowCount()).isEqualTo(10);

        List<Transaction> saved = transactionRepository.findByImportBatchIdOrderByIdAsc(batchId);
        assertThat(saved).hasSize(10);
        assertThat(saved.getFirst().getDescription()).isEqualTo("CAMPUS COFFEE CO");
        assertThat(saved.getFirst().getAmount()).isEqualByComparingTo("-4.75");
        assertThat(saved).extracting(Transaction::getDescription).contains("BOOKS, SUPPLIES & MORE");
        // 8 purchases (-170.77) + payment (250.00) + refund (23.99) = 103.22, exact to the cent.
        BigDecimal total = saved.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("103.22");
    }

    @Test
    void badRowMeansNothingIsSaved() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long accountId = createAccount(session, "Card");
        long transactionsBefore = transactionRepository.count();
        long batchesBefore = importBatchRepository.count();

        // The 10 good sample rows plus one with a typo in its date (line 12 of the file).
        String csv = Files.readString(SAMPLE).strip() + "\nnot-a-date,2026-09-02,1234,BAD ROW,Other,1.00,\n";
        upload(session, accountId, csvFile("bad.csv", csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file could not be imported. Nothing was saved."))
                .andExpect(jsonPath("$.rowErrors[0]").value("Row 12: date \"not-a-date\" isn't a date like 2026-07-30"));

        assertThat(transactionRepository.count()).isEqualTo(transactionsBefore);
        assertThat(importBatchRepository.count()).isEqualTo(batchesBefore);
    }

    @Test
    void cannotImportIntoAnotherUsersAccount() throws Exception {
        MockHttpSession alice = TestUsers.registerAndLogin(mockMvc);
        MockHttpSession bob = TestUsers.registerAndLogin(mockMvc);
        long alicesAccount = createAccount(alice, "Alice Card");
        long transactionsBefore = transactionRepository.count();

        upload(bob, alicesAccount, csvFile("statement.csv", Files.readAllBytes(SAMPLE)))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.count()).isEqualTo(transactionsBefore);
    }

    @Test
    void rejectsNonCsvEmptyAndBinaryFiles() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long accountId = createAccount(session, "Card");

        upload(session, accountId, csvFile("statement.pdf", Files.readAllBytes(SAMPLE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only .csv files can be imported."));
        upload(session, accountId, csvFile("empty.csv", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is empty."));
        upload(session, accountId, csvFile("image.csv", new byte[] {(byte) 0x89, 'P', 'N', 'G', (byte) 0xFF, (byte) 0xFE}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is not a text CSV file (expected UTF-8)."));
    }

    @Test
    void rejectsFilesOver2Megabytes() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long accountId = createAccount(session, "Card");

        upload(session, accountId, csvFile("huge.csv", new byte[(int) ImportService.MAX_FILE_BYTES + 1]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is larger than 2 MB."));
    }

    @Test
    void requiresLoginAndCsrfToken() throws Exception {
        MockMultipartFile file = csvFile("statement.csv", Files.readAllBytes(SAMPLE));

        mockMvc.perform(multipart("/api/imports").file(file).param("accountId", "1").with(csrf()))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        mockMvc.perform(multipart("/api/imports").file(file).param("accountId", "1").session(session))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private long createAccount(MockHttpSession session, String name) throws Exception {
        String response = mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions upload(MockHttpSession session, long accountId, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/imports")
                .file(file)
                .param("accountId", String.valueOf(accountId))
                .session(session)
                .with(csrf()));
    }

    private static MockMultipartFile csvFile(String name, byte[] content) {
        return new MockMultipartFile("file", name, "text/csv", content);
    }
}
