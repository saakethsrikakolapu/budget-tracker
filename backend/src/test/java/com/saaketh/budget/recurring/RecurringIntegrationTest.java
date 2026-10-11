package com.saaketh.budget.recurring;

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

/** Recurring charges detected from imported statements, plus hiding false alarms. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecurringIntegrationTest {

    private static final String CSV = """
            Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
            2026-05-03,2026-05-04,1234,NETFLIX.COM,Entertainment,15.49,
            2026-06-02,2026-06-03,1234,NETFLIX.COM,Entertainment,15.49,
            2026-07-03,2026-07-04,1234,NETFLIX.COM,Entertainment,17.99,
            2026-08-04,2026-08-05,1234,NETFLIX.COM,Entertainment,17.99,
            2026-05-10,2026-05-11,1234,PLANET FITNESS #0423,Other Services,24.99,
            2026-06-10,2026-06-11,1234,PLANET FITNESS #0423,Other Services,24.99,
            2026-07-10,2026-07-11,1234,PLANET FITNESS #0423,Other Services,24.99,
            2026-05-01,2026-05-02,1234,HULU 877-8244858,Entertainment,7.99,
            2026-06-01,2026-06-02,1234,HULU 877-8244858,Entertainment,7.99,
            2026-07-01,2026-07-02,1234,HULU 877-8244858,Entertainment,7.99,
            2026-05-20,2026-05-21,1234,CARD AUTOPAY TRANSFER,Payment/Credit,300.00,
            2026-06-20,2026-06-21,1234,CARD AUTOPAY TRANSFER,Payment/Credit,300.00,
            2026-07-20,2026-07-21,1234,CARD AUTOPAY TRANSFER,Payment/Credit,300.00,
            2026-05-04,2026-05-05,1234,STARBUCKS STORE 01234,Dining,5.25,
            2026-05-06,2026-05-07,1234,STARBUCKS STORE 01234,Dining,5.25,
            2026-06-19,2026-06-20,1234,STARBUCKS STORE 01234,Dining,5.25,
            2026-09-10,2026-09-11,1234,UNC STUDENT STORES,Merchandise,40.00,
            """;

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = TestUsers.registerAndLogin(mockMvc);
    }

    @Test
    void noTransactionsMeansNothingRecurring() throws Exception {
        mockMvc.perform(get("/api/recurring").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges.length()").value(0))
                .andExpect(jsonPath("$.activeMonthlyTotal").value(0))
                .andExpect(jsonPath("$.asOf").isEmpty());
    }

    @Test
    void findsSubscriptionsAndSkipsPaymentsAndCoffee() throws Exception {
        upload(CSV);

        mockMvc.perform(get("/api/recurring").session(session))
                .andExpect(jsonPath("$.asOf").value("2026-09-10"))
                .andExpect(jsonPath("$.charges.length()").value(3))
                // Netflix: still charging, so active; current price after the increase
                .andExpect(jsonPath("$.charges[0].merchantKey").value("NETFLIX COM"))
                .andExpect(jsonPath("$.charges[0].name").value("Netflix"))
                .andExpect(jsonPath("$.charges[0].cadence").value("MONTHLY"))
                .andExpect(jsonPath("$.charges[0].amount").value(17.99))
                .andExpect(jsonPath("$.charges[0].nextExpected").value("2026-09-04"))
                .andExpect(jsonPath("$.charges[0].active").value(true))
                .andExpect(jsonPath("$.charges[0].categoryName").value("Entertainment"))
                // Gym and Hulu stopped in July: they look cancelled, listed after active ones
                .andExpect(jsonPath("$.charges[1].name").value("Planet Fitness"))
                .andExpect(jsonPath("$.charges[1].active").value(false))
                .andExpect(jsonPath("$.charges[2].name").value("Hulu"))
                .andExpect(jsonPath("$.activeMonthlyTotal").value(17.99));
    }

    @Test
    void hidingAMerchantMovesItToHiddenAndBack() throws Exception {
        upload(CSV);

        mockMvc.perform(put("/api/recurring/hidden/NETFLIX COM").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/recurring/hidden/NETFLIX COM").session(session).with(csrf()))
                .andExpect(status().isNoContent()); // again: harmless

        mockMvc.perform(get("/api/recurring").session(session))
                .andExpect(jsonPath("$.charges.length()").value(2))
                .andExpect(jsonPath("$.hidden[0].merchantKey").value("NETFLIX COM"))
                .andExpect(jsonPath("$.activeMonthlyTotal").value(0));

        mockMvc.perform(delete("/api/recurring/hidden/NETFLIX COM").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/recurring").session(session))
                .andExpect(jsonPath("$.charges.length()").value(3))
                .andExpect(jsonPath("$.hidden.length()").value(0));

        mockMvc.perform(delete("/api/recurring/hidden/NETFLIX COM").session(session).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesKeysAndKeepsUsersApart() throws Exception {
        upload(CSV);

        mockMvc.perform(put("/api/recurring/hidden/netflix-com").session(session).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/recurring/hidden/" + "A".repeat(201)).session(session).with(csrf()))
                .andExpect(status().isBadRequest());

        MockHttpSession stranger = TestUsers.registerAndLogin(mockMvc);
        mockMvc.perform(get("/api/recurring").session(stranger))
                .andExpect(jsonPath("$.charges.length()").value(0));
        mockMvc.perform(put("/api/recurring/hidden/NETFLIX COM").session(stranger).with(csrf()))
                .andExpect(status().isNoContent());
        // Their hiding doesn't affect me.
        mockMvc.perform(get("/api/recurring").session(session))
                .andExpect(jsonPath("$.charges.length()").value(3));
    }

    private void upload(String csv) throws Exception {
        String response = mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Card\"}"))
                .andReturn().getResponse().getContentAsString();
        long account = ((Number) JsonPath.read(response, "$.id")).longValue();
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes()))
                        .param("accountId", String.valueOf(account))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
    }
}
