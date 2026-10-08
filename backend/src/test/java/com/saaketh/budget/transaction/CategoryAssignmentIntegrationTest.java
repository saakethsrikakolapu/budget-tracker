package com.saaketh.budget.transaction;

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

/** Categories assigned on import from the bank's labels, changed by hand, filtered, and totaled. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoryAssignmentIntegrationTest {

    private static final Path SAMPLE = Path.of("..", "samples", "capital-one-credit-card.csv");

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession session;
    private long account;

    @BeforeEach
    void setUp() throws Exception {
        session = TestUsers.registerAndLogin(mockMvc);
        account = createAccount(session, "Quicksilver");
    }

    @Test
    void importMapsBankLabelsToDefaultCategories() throws Exception {
        upload(Files.readString(SAMPLE));

        mockMvc.perform(get("/api/transactions").session(session).param("q", "pizza palace"))
                .andExpect(jsonPath("$.items[0].bankCategory").value("Dining"))
                .andExpect(jsonPath("$.items[0].categoryName").value("Food & Dining"))
                .andExpect(jsonPath("$.items[0].categorySource").value("BANK"));
        expectCategory("BOOKS, SUPPLIES", "Shopping");          // Merchandise
        expectCategory("AUTOPAY", "Payments & Transfers");      // Payment/Credit
        expectCategory("RIDESHARE", "Travel");                  // Other Travel
        expectCategory("FRESH MART", "Groceries");              // Groceries
    }

    @Test
    void vagueOrUnknownBankLabelsStayUncategorized() throws Exception {
        upload(header() + """
                2026-09-01,2026-09-02,1234,BARBER SHOP,Other Services,20.00,
                2026-09-02,2026-09-03,1234,MYSTERY,Something New,5.00,
                2026-09-03,2026-09-04,1234,NO LABEL,,7.00,
                """);

        mockMvc.perform(get("/api/transactions").session(session).param("category", "uncategorized"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].categoryId").isEmpty())
                .andExpect(jsonPath("$.items[0].categorySource").isEmpty());
    }

    @Test
    void renamedDefaultCategoryIsNoLongerAMappingTarget() throws Exception {
        long food = categoryId("Food & Dining");
        mockMvc.perform(put("/api/categories/" + food).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Eating Out\", \"countsAsSpending\": true}"))
                .andExpect(status().isOk());

        upload(header() + "2026-09-01,2026-09-02,1234,PIZZA PALACE,Dining,18.20,\n");

        mockMvc.perform(get("/api/transactions").session(session))
                .andExpect(jsonPath("$.items[0].categoryName").isEmpty());
    }

    @Test
    void userCanChangeOrClearACategoryByHand() throws Exception {
        upload(header() + "2026-09-01,2026-09-02,1234,UNC STUDENT STORES,Merchandise,62.10,\n");
        long txId = firstTransactionId();
        long education = categoryId("Education");

        setCategory(txId, education)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Education"))
                .andExpect(jsonPath("$.categorySource").value("MANUAL"));

        setCategory(txId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").isEmpty());
        mockMvc.perform(get("/api/transactions").session(session).param("category", "uncategorized"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void cannotUseAnotherUsersTransactionOrCategory() throws Exception {
        upload(header() + "2026-09-01,2026-09-02,1234,COFFEE,Dining,4.75,\n");
        long myTx = firstTransactionId();

        MockHttpSession stranger = TestUsers.registerAndLogin(mockMvc);
        long strangersCategory = categoryId(stranger, "Groceries");

        // Their category on my transaction
        setCategory(myTx, strangersCategory).andExpect(status().isNotFound());
        // Their session on my transaction
        mockMvc.perform(put("/api/transactions/" + myTx + "/category").session(stranger).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\": null}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtersByCategory() throws Exception {
        upload(Files.readString(SAMPLE));

        mockMvc.perform(get("/api/transactions").session(session)
                        .param("category", String.valueOf(categoryId("Food & Dining"))))
                .andExpect(jsonPath("$.totalItems").value(4)) // 3 coffees + pizza
                .andExpect(jsonPath("$.totals.spent").value(32.45));
        mockMvc.perform(get("/api/transactions").session(session).param("category", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void totalsLeaveOutCategoriesThatDontCountAsSpending() throws Exception {
        upload(Files.readString(SAMPLE));

        mockMvc.perform(get("/api/transactions").session(session))
                .andExpect(jsonPath("$.totals.spent").value(170.77))
                .andExpect(jsonPath("$.totals.refunds").value(23.99))
                .andExpect(jsonPath("$.totals.netSpending").value(146.78))
                .andExpect(jsonPath("$.totals.count").value(10))
                .andExpect(jsonPath("$.totals.excludedCount").value(1)); // the card payment

        // Mark Travel (the 12.40 rideshare) as not spending: it drops out of the totals.
        long travel = categoryId("Travel");
        mockMvc.perform(put("/api/categories/" + travel).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Travel\", \"countsAsSpending\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/transactions").session(session))
                .andExpect(jsonPath("$.totals.spent").value(158.37))
                .andExpect(jsonPath("$.totals.excludedCount").value(2));
    }

    // --- helpers ---

    private static String header() {
        return "Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit\n";
    }

    private void expectCategory(String search, String categoryName) throws Exception {
        mockMvc.perform(get("/api/transactions").session(session).param("q", search))
                .andExpect(jsonPath("$.items[0].categoryName").value(categoryName));
    }

    private long firstTransactionId() throws Exception {
        String response = mockMvc.perform(get("/api/transactions").session(session))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.items[0].id")).longValue();
    }

    private long categoryId(String name) throws Exception {
        return categoryId(session, name);
    }

    private long categoryId(MockHttpSession who, String name) throws Exception {
        String response = mockMvc.perform(get("/api/categories").session(who))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(response, "$[?(@.name == '" + name + "')].id");
        return ids.getFirst().longValue();
    }

    private ResultActions setCategory(long transactionId, Long categoryId) throws Exception {
        return mockMvc.perform(put("/api/transactions/" + transactionId + "/category").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\": " + categoryId + "}"));
    }

    private static long createAccount(MockMvc mvc, MockHttpSession who, String name) throws Exception {
        String response = mvc.perform(post("/api/accounts").session(who).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long createAccount(MockHttpSession who, String name) throws Exception {
        return createAccount(mockMvc, who, name);
    }

    private void upload(String csv) throws Exception {
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes()))
                        .param("accountId", String.valueOf(account))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
    }
}
