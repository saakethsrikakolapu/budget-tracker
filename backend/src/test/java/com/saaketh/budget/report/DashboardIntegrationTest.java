package com.saaketh.budget.report;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saaketh.budget.TestUsers;
import com.saaketh.budget.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Dashboard for the September sample, plus one August purchase for the trend. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DashboardIntegrationTest {

    private static final Path SAMPLE = Path.of("..", "samples", "capital-one-credit-card.csv");

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = TestUsers.registerAndLogin(mockMvc);
        String response = mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Card\"}"))
                .andReturn().getResponse().getContentAsString();
        long account = ((Number) JsonPath.read(response, "$.id")).longValue();
        upload(account, Files.readString(SAMPLE));
        upload(account, """
                Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
                2026-08-15,2026-08-16,1234,AUGUST GROCERIES,Groceries,30.00,
                """);
    }

    @Test
    void summarizesTheMonth() throws Exception {
        mockMvc.perform(get("/api/dashboard").session(session).param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.totals.spent").value(170.77))
                .andExpect(jsonPath("$.totals.refunds").value(23.99))
                .andExpect(jsonPath("$.totals.netSpending").value(146.78))
                // Biggest first; the card payment (Payments & Transfers) is left out.
                .andExpect(jsonPath("$.spendingByCategory.length()").value(5))
                .andExpect(jsonPath("$.spendingByCategory[0].categoryName").value("Groceries"))
                .andExpect(jsonPath("$.spendingByCategory[0].amount").value(48.33))
                .andExpect(jsonPath("$.spendingByCategory[1].categoryName").value("Shopping"))
                .andExpect(jsonPath("$.spendingByCategory[1].amount").value(38.11))
                .andExpect(jsonPath("$.spendingByCategory[4].categoryName").value("Travel"))
                // Six months, oldest first, ending with the selected month.
                .andExpect(jsonPath("$.monthlyTrend.length()").value(6))
                .andExpect(jsonPath("$.monthlyTrend[0].month").value("2026-04"))
                .andExpect(jsonPath("$.monthlyTrend[0].netSpending").value(0.00))
                .andExpect(jsonPath("$.monthlyTrend[4].month").value("2026-08"))
                .andExpect(jsonPath("$.monthlyTrend[4].netSpending").value(30.00))
                .andExpect(jsonPath("$.monthlyTrend[5].netSpending").value(146.78));
    }

    @Test
    void uncategorizedSpendingIsShownByName() throws Exception {
        String response = mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Other card\"}"))
                .andReturn().getResponse().getContentAsString();
        upload(((Number) JsonPath.read(response, "$.id")).longValue(), """
                Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
                2026-10-01,2026-10-02,1234,MYSTERY SHOP,Other,9.99,
                """);

        mockMvc.perform(get("/api/dashboard").session(session).param("month", "2026-10"))
                .andExpect(jsonPath("$.spendingByCategory[0].categoryName").value("Uncategorized"))
                .andExpect(jsonPath("$.spendingByCategory[0].categoryId").isEmpty())
                .andExpect(jsonPath("$.spendingByCategory[0].amount").value(9.99));
    }

    @Test
    void emptyMonthAndBadInput() throws Exception {
        mockMvc.perform(get("/api/dashboard").session(session).param("month", "2025-01"))
                .andExpect(jsonPath("$.spendingByCategory.length()").value(0))
                .andExpect(jsonPath("$.totals.count").value(0));
        mockMvc.perform(get("/api/dashboard").session(session).param("month", "nope"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dashboard").param("month", "2026-09")).andExpect(status().isUnauthorized());
    }

    private void upload(long account, String csv) throws Exception {
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes()))
                        .param("accountId", String.valueOf(account))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
    }
}
