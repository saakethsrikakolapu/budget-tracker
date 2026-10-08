package com.saaketh.budget.budget;

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
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Budgets against the September sample statement. By category (net of refunds):
 *   Food & Dining 32.45 (3 coffees + pizza), Shopping 38.11 (62.10 books - 23.99 return),
 *   Entertainment 15.49, Groceries 48.33, Travel 12.40, Payments & Transfers (not spending).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BudgetIntegrationTest {

    private static final Path SAMPLE = Path.of("..", "samples", "capital-one-credit-card.csv");

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = TestUsers.registerAndLogin(mockMvc);
        long account = idOf(mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Card\"}")));
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", Files.readAllBytes(SAMPLE)))
                        .param("accountId", String.valueOf(account))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void overviewComparesSpendingToLimits() throws Exception {
        setBudget("Food & Dining", "50").andExpect(status().isNoContent());
        setBudget("Shopping", "40.00").andExpect(status().isNoContent());

        mockMvc.perform(overview("2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                // Most used first: Shopping 38.11 / 40 = 95%
                .andExpect(jsonPath("$.budgets[0].categoryName").value("Shopping"))
                .andExpect(jsonPath("$.budgets[0].spent").value(38.11))
                .andExpect(jsonPath("$.budgets[0].remaining").value(1.89))
                .andExpect(jsonPath("$.budgets[0].percentUsed").value(95))
                .andExpect(jsonPath("$.budgets[1].categoryName").value("Food & Dining"))
                .andExpect(jsonPath("$.budgets[1].limit").value(50.00))
                .andExpect(jsonPath("$.budgets[1].spent").value(32.45))
                .andExpect(jsonPath("$.budgets[1].percentUsed").value(65))
                .andExpect(jsonPath("$.totalBudgeted").value(90.00))
                .andExpect(jsonPath("$.totalSpent").value(70.56))
                // Entertainment + Groceries + Travel; the card payment doesn't count.
                .andExpect(jsonPath("$.unbudgetedSpending").value(76.22));
    }

    @Test
    void overBudgetShowsNegativeRemaining() throws Exception {
        setBudget("Groceries", "40").andExpect(status().isNoContent());

        mockMvc.perform(overview("2026-09"))
                .andExpect(jsonPath("$.budgets[0].spent").value(48.33))
                .andExpect(jsonPath("$.budgets[0].remaining").value(-8.33))
                .andExpect(jsonPath("$.budgets[0].percentUsed").value(121));
    }

    @Test
    void monthWithoutTransactionsShowsNothingSpent() throws Exception {
        setBudget("Food & Dining", "50").andExpect(status().isNoContent());

        mockMvc.perform(overview("2026-10"))
                .andExpect(jsonPath("$.budgets[0].spent").value(0.00))
                .andExpect(jsonPath("$.budgets[0].percentUsed").value(0))
                .andExpect(jsonPath("$.unbudgetedSpending").value(0.00));
    }

    @Test
    void settingAgainChangesTheLimitAndDeleteRemovesIt() throws Exception {
        setBudget("Food & Dining", "50").andExpect(status().isNoContent());
        setBudget("Food & Dining", "64.90").andExpect(status().isNoContent());

        mockMvc.perform(overview("2026-09"))
                .andExpect(jsonPath("$.budgets.length()").value(1))
                .andExpect(jsonPath("$.budgets[0].limit").value(64.90))
                .andExpect(jsonPath("$.budgets[0].percentUsed").value(50));

        long food = categoryId("Food & Dining");
        mockMvc.perform(delete("/api/budgets/" + food).session(session).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/budgets/" + food).session(session).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(overview("2026-09")).andExpect(jsonPath("$.budgets.length()").value(0));
    }

    @Test
    void deletingTheCategoryDeletesItsBudget() throws Exception {
        setBudget("Travel", "100").andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/categories/" + categoryId("Travel")).session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(overview("2026-09")).andExpect(jsonPath("$.budgets.length()").value(0));
    }

    @Test
    void rejectsInvalidAmountsMonthsAndNonSpendingCategories() throws Exception {
        setBudget("Food & Dining", "0").andExpect(status().isBadRequest());
        setBudget("Food & Dining", "-5").andExpect(status().isBadRequest());
        setBudget("Food & Dining", "10.005").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.monthlyLimit").value("Use at most 2 decimal places"));
        setBudget("Payments & Transfers", "100").andExpect(status().isBadRequest());
        mockMvc.perform(overview("September")).andExpect(status().isBadRequest());
    }

    @Test
    void otherUsersCantSeeOrChangeYourBudgets() throws Exception {
        setBudget("Food & Dining", "50").andExpect(status().isNoContent());
        MockHttpSession stranger = TestUsers.registerAndLogin(mockMvc);

        mockMvc.perform(get("/api/budgets").session(stranger).param("month", "2026-09"))
                .andExpect(jsonPath("$.budgets.length()").value(0));
        long myFood = categoryId("Food & Dining");
        mockMvc.perform(put("/api/budgets/" + myFood).session(stranger).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlyLimit\": \"1\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/budgets/" + myFood).session(stranger).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private ResultActions setBudget(String categoryName, String amount) throws Exception {
        return mockMvc.perform(put("/api/budgets/" + categoryId(categoryName)).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"monthlyLimit\": \"" + amount + "\"}"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder overview(String month) {
        return get("/api/budgets").session(session).param("month", month);
    }

    private long categoryId(String name) throws Exception {
        String response = mockMvc.perform(get("/api/categories").session(session)).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$[?(@.name == '" + name + "')].id").getFirst().longValue();
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
