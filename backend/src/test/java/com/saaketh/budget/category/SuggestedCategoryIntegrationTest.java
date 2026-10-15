package com.saaketh.budget.category;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saaketh.budget.TestUsers;
import com.saaketh.budget.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The model's suggestions in the app: last in line, labeled SUGGESTED, never over a person's choice. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SuggestedCategoryIntegrationTest {

    /** A checking account export (no category column): rules only have the dictionary. */
    private static final String CSV = """
            Date,Description,Amount
            10/20/2026,DOMINOSPIZZA 4471 CHAPEL HILL NC,-18.20
            10/21/2026,SQ *JOES COFFEE,-4.75
            10/22/2026,WALGRNS #1234,-12.99
            10/23/2026,ZORBLAX GADGETS,-30.00
            10/24/2026,PAYROLL ACME,800.00
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SuggestionBackfill backfill;

    private MockHttpSession session;
    private long account;

    @BeforeEach
    void setUp() throws Exception {
        session = TestUsers.registerAndLogin(mockMvc);
        account = id(mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Checking\"}")));
    }

    @Test
    void theModelFillsGapsRulesMissAndOnlyWhenConfident() throws Exception {
        upload(CSV);

        expect("DOMINOSPIZZA", "Food & Dining", "SUGGESTED");      // glued words: the dictionary misses PIZZA
        expect("WALGRNS", "Health & Personal Care", "SUGGESTED");  // dropped vowels
        expect("JOES COFFEE", "Food & Dining", "KEYWORD");         // the dictionary still goes first
        expect("PAYROLL", "Income", "KEYWORD");                    // money in: rules only
        expect("ZORBLAX", null, null);                             // not sure enough: no guess
    }

    @Test
    void peoplesChoicesBeatSuggestions() throws Exception {
        upload(CSV);
        long pizza = transactionId("DOMINOSPIZZA");

        // "Always put DOMINOSPIZZA in Shopping": a rule outranks the model.
        mockMvc.perform(post("/api/rules").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pattern\": \"DOMINOSPIZZA\", \"categoryId\": " + categoryId("Shopping") + "}"))
                .andExpect(status().isCreated());
        expect("DOMINOSPIZZA", "Shopping", "RULE");

        // Confirming a suggestion (choosing the same category) makes it the person's own choice.
        long walgreens = transactionId("WALGRNS");
        setCategory(walgreens, categoryId("Health & Personal Care"));
        expect("WALGRNS", "Health & Personal Care", "MANUAL");

        // And a manual choice survives everything that re-runs categorization.
        setCategory(pizza, categoryId("Entertainment"));
        backfill.run(null);
        expect("DOMINOSPIZZA", "Entertainment", "MANUAL");
    }

    @Test
    void noSuggestionIntoACategoryTheUserRenamed() throws Exception {
        long food = categoryId("Food & Dining");
        mockMvc.perform(put("/api/categories/" + food).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Eating Out\", \"countsAsSpending\": true}"))
                .andExpect(status().isOk());

        upload(CSV);

        expect("DOMINOSPIZZA", null, null);
    }

    @Test
    void olderUncategorizedSpendingGetsSuggestionsAtStartup() throws Exception {
        upload(CSV);
        long pizza = transactionId("DOMINOSPIZZA");
        // As if it had been imported before the model existed.
        jdbcTemplate.update("UPDATE transactions SET category_id = NULL, category_source = NULL WHERE id = ?", pizza);
        expect("DOMINOSPIZZA", null, null);

        backfill.run(null);

        expect("DOMINOSPIZZA", "Food & Dining", "SUGGESTED");
    }

    // --- helpers ---

    private void upload(String csv) throws Exception {
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes()))
                        .param("accountId", String.valueOf(account))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
    }

    private void expect(String search, String categoryName, String source) throws Exception {
        mockMvc.perform(get("/api/transactions").session(session).param("q", search).param("month", "all"))
                .andExpect(jsonPath("$.items[0].categoryName").value(categoryName))
                .andExpect(jsonPath("$.items[0].categorySource").value(source));
    }

    private long transactionId(String search) throws Exception {
        return ((Number) JsonPath.read(mockMvc.perform(get("/api/transactions").session(session).param("q", search))
                .andReturn().getResponse().getContentAsString(), "$.items[0].id")).longValue();
    }

    private long categoryId(String name) throws Exception {
        String response = mockMvc.perform(get("/api/categories").session(session)).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$[?(@.name == '" + name + "')].id").getFirst().longValue();
    }

    private void setCategory(long transactionId, long categoryId) throws Exception {
        mockMvc.perform(put("/api/transactions/" + transactionId + "/category").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\": " + categoryId + "}"))
                .andExpect(status().isOk());
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
