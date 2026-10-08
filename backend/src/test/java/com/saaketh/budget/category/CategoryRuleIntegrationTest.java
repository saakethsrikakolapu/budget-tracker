package com.saaketh.budget.category;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Rules end to end. Each test starts with a fresh user who imported:
 *   POSHMARK (Merchandise -> Shopping), UBER TRIP (Other Travel -> Travel),
 *   UBER EATS (Dining -> Food & Dining), BARBER (Other Services -> Uncategorized).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoryRuleIntegrationTest {

    private static final String CSV = """
            Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
            2026-09-01,2026-09-02,1234,POSHMARK,Merchandise,6.09,
            2026-09-02,2026-09-03,1234,UBER   TRIP 1234,Other Travel,12.40,
            2026-09-03,2026-09-04,1234,Uber Eats Order,Dining,18.00,
            2026-09-04,2026-09-05,1234,STYLE STREET BARBERS,Other Services,41.40,
            """;

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession session;
    private long account;

    @BeforeEach
    void setUp() throws Exception {
        session = TestUsers.registerAndLogin(mockMvc);
        account = createAccount();
        upload(CSV);
    }

    @Test
    void newRuleRecategorizesExistingTransactions() throws Exception {
        createRule(" barbers ", categoryId("Health & Personal Care"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rule.pattern").value("BARBERS"))
                .andExpect(jsonPath("$.rule.categoryName").value("Health & Personal Care"))
                .andExpect(jsonPath("$.rule.matchCount").value(1))
                .andExpect(jsonPath("$.recategorizedCount").value(1));

        expectCategory("BARBERS", "Health & Personal Care", "RULE");
    }

    @Test
    void rulesBeatBankLabelsButNotManualChoices() throws Exception {
        long poshmarkTx = transactionId("POSHMARK");
        long education = categoryId("Education");
        // The user puts POSHMARK in Education by hand...
        mockMvc.perform(put("/api/transactions/" + poshmarkTx + "/category").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\": " + education + "}"))
                .andExpect(status().isOk());

        // ...so a rule for POSHMARK leaves that transaction alone.
        createRule("POSHMARK", categoryId("Other")).andExpect(jsonPath("$.recategorizedCount").value(0));
        expectCategory("POSHMARK", "Education", "MANUAL");

        // A rule does override the bank's label.
        createRule("UBER TRIP", categoryId("Transportation")).andExpect(jsonPath("$.recategorizedCount").value(1));
        expectCategory("UBER TRIP", "Transportation", "RULE");
    }

    @Test
    void mostSpecificRuleWins() throws Exception {
        createRule("UBER", categoryId("Transportation")).andExpect(status().isCreated());
        createRule("UBER EATS", categoryId("Food & Dining")).andExpect(status().isCreated());

        expectCategory("UBER TRIP", "Transportation", "RULE");
        expectCategory("UBER EATS", "Food & Dining", "RULE");
        mockMvc.perform(get("/api/rules").session(session))
                .andExpect(jsonPath("$[0].pattern").value("UBER EATS"))  // listed in the order applied
                .andExpect(jsonPath("$[1].pattern").value("UBER"))
                .andExpect(jsonPath("$[1].matchCount").value(2));
    }

    @Test
    void futureImportsUseRules() throws Exception {
        createRule("depop", categoryId("Shopping")).andExpect(status().isCreated());

        upload("""
                Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
                2026-09-10,2026-09-11,1234,DEPOP* P470816315920,Other,5.26,
                """);

        expectCategory("DEPOP", "Shopping", "RULE");
    }

    @Test
    void deletingARuleFallsBackToTheBankLabel() throws Exception {
        long ruleId = idOf(createRule("UBER TRIP", categoryId("Transportation")), "$.rule.id");

        mockMvc.perform(delete("/api/rules/" + ruleId).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recategorizedCount").value(1));

        expectCategory("UBER TRIP", "Travel", "BANK");
    }

    @Test
    void editingARuleMovesItsTransactions() throws Exception {
        long ruleId = idOf(createRule("BARBERS", categoryId("Other")), "$.rule.id");

        mockMvc.perform(put("/api/rules/" + ruleId).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pattern\": \"barbers\", \"categoryId\": " + categoryId("Health & Personal Care") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recategorizedCount").value(1));

        expectCategory("BARBERS", "Health & Personal Care", "RULE");
    }

    @Test
    void deletingTheTargetCategoryDeletesItsRules() throws Exception {
        long coffee = createCategory("Coffee");
        createRule("UBER EATS", coffee).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/categories/" + coffee).session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rules").session(session)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validatesPatternsAndOwnership() throws Exception {
        long shopping = categoryId("Shopping");
        createRule("POSHMARK", shopping).andExpect(status().isCreated());

        createRule("poshmark", shopping)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.pattern").value("You already have a rule for this text"));
        createRule(" x ", shopping).andExpect(status().isBadRequest());
        createRule("", shopping).andExpect(status().isBadRequest());

        MockHttpSession stranger = TestUsers.registerAndLogin(mockMvc);
        long strangersCategory = categoryId(stranger, "Shopping");
        createRule("UBER", strangersCategory).andExpect(status().isNotFound());

        long myRule = JsonPath.<List<Number>>read(
                mockMvc.perform(get("/api/rules").session(session)).andReturn().getResponse().getContentAsString(),
                "$[*].id").getFirst().longValue();
        mockMvc.perform(delete("/api/rules/" + myRule).session(stranger).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/rules").session(stranger)).andExpect(jsonPath("$.length()").value(0));
    }

    // --- helpers ---

    private ResultActions createRule(String pattern, long categoryId) throws Exception {
        return mockMvc.perform(post("/api/rules").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\": \"" + pattern + "\", \"categoryId\": " + categoryId + "}"));
    }

    private void expectCategory(String search, String categoryName, String source) throws Exception {
        mockMvc.perform(get("/api/transactions").session(session).param("q", search))
                .andExpect(jsonPath("$.items[0].categoryName").value(categoryName))
                .andExpect(jsonPath("$.items[0].categorySource").value(source));
    }

    private long transactionId(String search) throws Exception {
        return idOf(mockMvc.perform(get("/api/transactions").session(session).param("q", search)), "$.items[0].id");
    }

    private long categoryId(String name) throws Exception {
        return categoryId(session, name);
    }

    private long categoryId(MockHttpSession who, String name) throws Exception {
        String response = mockMvc.perform(get("/api/categories").session(who)).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$[?(@.name == '" + name + "')].id").getFirst().longValue();
    }

    private long createCategory(String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/categories").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\", \"countsAsSpending\": true}")), "$.id");
    }

    private long createAccount() throws Exception {
        return idOf(mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Card\"}")), "$.id");
    }

    private void upload(String csv) throws Exception {
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes()))
                        .param("accountId", String.valueOf(account))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
    }

    private static long idOf(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }
}
