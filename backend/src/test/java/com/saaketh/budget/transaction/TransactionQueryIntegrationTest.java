package com.saaketh.budget.transaction;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saaketh.budget.TestUsers;
import com.saaketh.budget.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Listing, filtering, paging, and totals. Each test gets a fresh user with:
 *   "Quicksilver": the 10-row sample statement (September 2026)
 *   "Discover":    2 October rows, one with a literal % in its description
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionQueryIntegrationTest {

    private static final Path SAMPLE = Path.of("..", "samples", "capital-one-credit-card.csv");
    private static final String OCTOBER = """
            Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
            2026-10-05,2026-10-06,9999,TACO TRUCK,Dining,9.50,
            2026-10-02,2026-10-03,9999,SAVE 100% PROMO,Merchandise,20.00,
            """;

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession session;
    private long quicksilver;
    private long discover;

    @BeforeEach
    void setUp() throws Exception {
        session = TestUsers.registerAndLogin(mockMvc);
        quicksilver = createAccount("Quicksilver");
        discover = createAccount("Discover");
        upload(quicksilver, Files.readAllBytes(SAMPLE));
        upload(discover, OCTOBER.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void listsEverythingNewestFirstWithTotals() throws Exception {
        mockMvc.perform(list())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(12))
                .andExpect(jsonPath("$.items[0].description").value("TACO TRUCK"))
                .andExpect(jsonPath("$.items[0].transactionDate").value("2026-10-05"))
                .andExpect(jsonPath("$.items[0].accountName").value("Discover"))
                .andExpect(jsonPath("$.items[0].amount").value(-9.50))
                .andExpect(jsonPath("$.items[11].description").value("PIZZA PALACE"))
                // Sample: spent 170.77, received 273.99. October: spent 29.50.
                .andExpect(jsonPath("$.totals.spent").value(200.27))
                .andExpect(jsonPath("$.totals.received").value(273.99))
                .andExpect(jsonPath("$.totals.net").value(73.72))
                .andExpect(jsonPath("$.totals.count").value(12));
    }

    @Test
    void filtersByAccount() throws Exception {
        mockMvc.perform(list().param("accountId", String.valueOf(discover)))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totals.spent").value(29.50));
    }

    @Test
    void filtersByDateRangeInclusive() throws Exception {
        mockMvc.perform(list().param("from", "2026-09-22").param("to", "2026-09-28"))
                .andExpect(jsonPath("$.totalItems").value(7))
                .andExpect(jsonPath("$.items[0].transactionDate").value("2026-09-28"))
                .andExpect(jsonPath("$.items[6].transactionDate").value("2026-09-22"));
    }

    @Test
    void searchIsCaseInsensitiveAndTotalsFollowTheFilter() throws Exception {
        mockMvc.perform(list().param("q", "  coffee "))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totals.spent").value(14.25))
                .andExpect(jsonPath("$.totals.received").value(0.00));
    }

    @Test
    void searchTreatsPercentAndUnderscoreLiterally() throws Exception {
        mockMvc.perform(list().param("q", "100%"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].description").value("SAVE 100% PROMO"));
        // As a wildcard, "%" would match everything; literally, only the promo row contains it.
        mockMvc.perform(list().param("q", "%")).andExpect(jsonPath("$.totalItems").value(1));
        mockMvc.perform(list().param("q", "_")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void pagesThroughResultsWhileTotalsCoverEverything() throws Exception {
        mockMvc.perform(list().param("size", "5").param("page", "2"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totals.count").value(12));
    }

    @Test
    void combinesFilters() throws Exception {
        mockMvc.perform(list().param("accountId", String.valueOf(quicksilver))
                        .param("from", "2026-09-01").param("to", "2026-09-30").param("q", "coffee"))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void rejectsBackwardsDateRange() throws Exception {
        mockMvc.perform(list().param("from", "2026-10-01").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsMonthsWithTransactionsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/transactions/months").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("2026-10"))
                .andExpect(jsonPath("$[1]").value("2026-09"));
    }

    @Test
    void otherUsersSeeNothingEvenWithYourAccountId() throws Exception {
        MockHttpSession stranger = TestUsers.registerAndLogin(mockMvc);

        mockMvc.perform(get("/api/transactions").session(stranger).param("accountId", String.valueOf(quicksilver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totals.spent").value(0.00));
        mockMvc.perform(get("/api/transactions/months").session(stranger)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private MockHttpServletRequestBuilder list() {
        return get("/api/transactions").session(session);
    }

    private long createAccount(String name) throws Exception {
        String response = mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void upload(long accountId, byte[] csv) throws Exception {
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "statement.csv", "text/csv", csv))
                        .param("accountId", String.valueOf(accountId))
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isCreated());
    }
}
