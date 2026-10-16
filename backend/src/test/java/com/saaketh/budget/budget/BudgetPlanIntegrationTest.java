package com.saaketh.budget.budget;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.nio.charset.StandardCharsets;
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
 * The suggested budget through the real API, against the six-month credit card sample
 * (April to September 2026, fake data): no income on a credit card, a monthly phone bill.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BudgetPlanIntegrationTest {

    private static final Path SAMPLE = Path.of("..", "samples", "subscriptions-6-months.csv");

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = TestUsers.registerAndLogin(mockMvc);
    }

    @Test
    void creditCardOnlySuggestsABudgetFromUsualSpending() throws Exception {
        upload("Card", Files.readAllBytes(SAMPLE));

        String plan = mockMvc.perform(plan("2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullMonths").value(6))
                .andExpect(jsonPath("$.monthlyIncome").doesNotExist())
                .andExpect(jsonPath("$.basis").value("USUAL"))
                .andExpect(jsonPath("$.throughDay").value(0)) // no October data yet
                .andExpect(jsonPath("$.pace").value("UNKNOWN"))
                .andReturn().getResponse().getContentAsString();

        // The phone bill is a regular bill: it keeps its cost.
        List<Number> billsFixed = JsonPath.read(plan, "$.lines[?(@.categoryName == 'Bills & Utilities')].fixed");
        assertThat(billsFixed.getFirst().doubleValue()).isPositive();
        // Card payments aren't spending, so they're nowhere in the plan.
        List<String> names = JsonPath.read(plan, "$.lines[*].categoryName");
        assertThat(names).isNotEmpty().doesNotContain("Payments & Transfers", "Income");
    }

    @Test
    void usingThePlanCreatesBudgetsButKeepsOnesYouSet() throws Exception {
        upload("Card", Files.readAllBytes(SAMPLE));
        long food = categoryId("Food & Dining");
        mockMvc.perform(put("/api/budgets/" + food).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlyLimit\": \"7.00\"}"))
                .andExpect(status().isNoContent());

        String plan = mockMvc.perform(plan("2026-10")).andReturn().getResponse().getContentAsString();
        List<Object> budgetable = JsonPath.read(plan, "$.lines[?(@.categoryId != null && @.suggested > 0)]");

        String overview = mockMvc.perform(post("/api/budgets/plan").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"month\": \"2026-10\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<Object>>read(overview, "$.budgets")).hasSize(budgetable.size());
        // The budget set by hand is untouched.
        assertThat(JsonPath.<List<Number>>read(overview, "$.budgets[?(@.categoryName == 'Food & Dining')].limit")
                .getFirst().doubleValue()).isEqualTo(7.00);

        // Using it again changes nothing.
        String again = mockMvc.perform(post("/api/budgets/plan").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"month\": \"2026-10\"}"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(again, "$.budgets")).hasSize(budgetable.size());
    }

    @Test
    void yourOwnTotalShrinksTheSuggestion() throws Exception {
        upload("Card", Files.readAllBytes(SAMPLE));

        mockMvc.perform(plan("2026-10").param("target", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("CUSTOM"))
                .andExpect(jsonPath("$.target").value(100.00));
        mockMvc.perform(plan("2026-10").param("target", "-5")).andExpect(status().isBadRequest());
        mockMvc.perform(plan("2026-10").param("target", "10.005")).andExpect(status().isBadRequest());
        mockMvc.perform(plan("October")).andExpect(status().isBadRequest());
    }

    @Test
    void paychecksAndMoneyFromFamilyCountAsIncome() throws Exception {
        // A checking account, July to September: $500 pay + $300 from family each month. Money sent
        // to a friend is neither income nor spending.
        StringBuilder csv = new StringBuilder("Date,Description,Amount\n");
        for (int month = 7; month <= 9; month++) {
            csv.append("%02d/01/2026,HARRIS TEETER #0318,-60.00\n".formatted(month));
            csv.append("%02d/05/2026,PAYROLL ACME CORP,500.00\n".formatted(month));
            csv.append("%02d/12/2026,Zelle money received from JANE DOE,300.00\n".formatted(month));
            csv.append("%02d/18/2026,Zelle money sent to JOHN ROE,-50.00\n".formatted(month));
            csv.append("%02d/28/2026,CHIPOTLE 1234,-12.00\n".formatted(month));
        }
        csv.append("10/03/2026,CHIPOTLE 1234,-15.00\n");
        upload("Checking", csv.toString().getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(plan("2026-10"))
                .andExpect(jsonPath("$.fullMonths").value(3))
                .andExpect(jsonPath("$.monthlyIncome").value(800.00))
                .andExpect(jsonPath("$.basis").value("INCOME"))
                .andExpect(jsonPath("$.target").value(800.00))
                .andExpect(jsonPath("$.usualTotal").value(72.00))
                .andExpect(jsonPath("$.throughDay").value(3));
    }

    @Test
    void notEnoughHistoryYet() throws Exception {
        upload("Card", "Date,Description,Amount\n09/19/2026,CHIPOTLE 1234,-12.00\n10/02/2026,CHIPOTLE 1234,-9.00\n"
                .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(plan("2026-10"))
                .andExpect(jsonPath("$.fullMonths").value(0))
                .andExpect(jsonPath("$.target").doesNotExist());
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder plan(String month) {
        return get("/api/budgets/plan").session(session).param("month", month);
    }

    private void upload(String accountName, byte[] csv) throws Exception {
        long account = idOf(mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + accountName + "\"}")));
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", csv))
                        .param("accountId", String.valueOf(account))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
    }

    private long categoryId(String name) throws Exception {
        String response = mockMvc.perform(get("/api/categories").session(session)).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(response, "$[?(@.name == '" + name + "')].id").getFirst().longValue();
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
