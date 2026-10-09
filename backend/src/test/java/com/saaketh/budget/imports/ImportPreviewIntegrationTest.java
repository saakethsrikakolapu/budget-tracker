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
import com.saaketh.budget.transaction.TransactionRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** The preview endpoint, the user's column choices, and remembered formats. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportPreviewIntegrationTest {

    private static final Path SAMPLES = Path.of("..", "samples");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void previewShowsWhatWouldBeImportedWithoutSavingAnything() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long before = transactionRepository.count();

        preview(session, sample("capital-one-credit-card.csv"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DETECTED"))
                .andExpect(jsonPath("$.mapping.descriptionColumn").value(3))
                .andExpect(jsonPath("$.mapping.amountStyle").value("DEBIT_CREDIT"))
                .andExpect(jsonPath("$.columns.length()").value(7))
                .andExpect(jsonPath("$.columns[3].name").value("Description"))
                .andExpect(jsonPath("$.columns[3].samples[0]").value("CAMPUS COFFEE CO"))
                .andExpect(jsonPath("$.transactionCount").value(10))
                .andExpect(jsonPath("$.transactions.length()").value(ImportService.PREVIEW_ROWS))
                .andExpect(jsonPath("$.transactions[0].amount").value(-4.75))
                .andExpect(jsonPath("$.errors.length()").value(0));

        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    void userCanCorrectTheColumnsInThePreview() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        byte[] file = sample("layouts/chase-credit-card.csv");
        String detected = mapping(preview(session, file, null));

        // Pretend inference got the sign wrong: flip it.
        String flipped = detected.replace("\"positiveIsSpending\":false", "\"positiveIsSpending\":true");
        preview(session, file, flipped)
                .andExpect(jsonPath("$.source").value("CUSTOM"))
                .andExpect(jsonPath("$.transactions[0].amount").value(4.75));
    }

    @Test
    void unrecognizableFileStillShowsItsColumns() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        byte[] file = "Name,Note\nAlice,hello\nBob,hi\n".getBytes(StandardCharsets.UTF_8);

        preview(session, file, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapping").isEmpty())
                .andExpect(jsonPath("$.columns.length()").value(2))
                .andExpect(jsonPath("$.errors[0]").value("Couldn't find any transactions: no rows have both a date and an amount."));

        long account = createAccount(session);
        upload(session, account, file, null, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rowErrors[0]").value(org.hamcrest.Matchers.containsString("Use the preview")));
    }

    @Test
    void rememberedFormatIsUsedForTheNextFileWithTheSameShape() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long account = createAccount(session);
        byte[] file = sample("layouts/discover.csv");
        String chosen = mapping(preview(session, file, null));

        upload(session, account, file, chosen, true).andExpect(status().isCreated());

        // A different month's export from the same bank: same header, different rows.
        String nextMonth = """
                Trans. Date,Post Date,Description,Amount,Category
                11/02/2026,11/03/2026,CAMPUS COFFEE CO,4.75,Restaurants
                11/03/2026,11/04/2026,BOOKSTORE ONLINE,20.00,Merchandise
                """;
        preview(session, nextMonth.getBytes(StandardCharsets.UTF_8), null)
                .andExpect(jsonPath("$.source").value("SAVED"))
                .andExpect(jsonPath("$.transactions[0].amount").value(-4.75));

        // Other users don't see it.
        MockHttpSession stranger = TestUsers.registerAndLogin(mockMvc);
        preview(stranger, nextMonth.getBytes(StandardCharsets.UTF_8), null)
                .andExpect(jsonPath("$.source").value("DETECTED"));
    }

    @Test
    void importsAFileWithNoHeaderRow() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long account = createAccount(session);

        upload(session, account, sample("layouts/wells-fargo.csv"), null, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedCount").value(7));
    }

    @Test
    void rejectsGarbledColumnChoices() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);

        preview(session, sample("capital-one-credit-card.csv"), "{not json")
                .andExpect(status().isBadRequest());
        // Incomplete (no positiveIsSpending): rejected as unreadable rather than guessed.
        preview(session, sample("capital-one-credit-card.csv"),
                "{\"dateColumn\":0,\"descriptionColumn\":3,\"amountStyle\":\"SIGNED\",\"amountColumn\":5,\"dateFormat\":\"uuuu-MM-dd\"}")
                .andExpect(status().isBadRequest());
        // Complete, but pointing at a column the file doesn't have.
        preview(session, sample("capital-one-credit-card.csv"),
                "{\"dateColumn\":0,\"descriptionColumn\":42,\"amountStyle\":\"SIGNED\",\"amountColumn\":5,"
                        + "\"positiveIsSpending\":false,\"dateFormat\":\"uuuu-MM-dd\"}")
                .andExpect(jsonPath("$.errors[0]").value("The column choices don't match this file. Choose the columns again."));
    }

    // --- helpers ---

    private ResultActions preview(MockHttpSession session, byte[] file, String mapping) throws Exception {
        var request = multipart("/api/imports/preview")
                .file(new MockMultipartFile("file", "statement.csv", "text/csv", file))
                .session(session).with(csrf());
        if (mapping != null) {
            request.param("mapping", mapping);
        }
        return mockMvc.perform(request);
    }

    private ResultActions upload(MockHttpSession session, long account, byte[] file, String mapping, boolean remember)
            throws Exception {
        var request = multipart("/api/imports")
                .file(new MockMultipartFile("file", "statement.csv", "text/csv", file))
                .param("accountId", String.valueOf(account))
                .param("rememberFormat", String.valueOf(remember))
                .session(session).with(csrf());
        if (mapping != null) {
            request.param("mapping", mapping);
        }
        return mockMvc.perform(request);
    }

    /** The preview's mapping as compact JSON, ready to send back. */
    private static String mapping(ResultActions preview) throws Exception {
        Object mapping = JsonPath.read(preview.andReturn().getResponse().getContentAsString(), "$.mapping");
        return com.jayway.jsonpath.internal.JsonFormatter.prettyPrint(
                net.minidev.json.JSONValue.toJSONString(mapping)).replaceAll("\\s+", "");
    }

    private long createAccount(MockHttpSession session) throws Exception {
        String response = mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Card\"}"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private static byte[] sample(String name) throws Exception {
        return Files.readAllBytes(SAMPLES.resolve(name));
    }
}
