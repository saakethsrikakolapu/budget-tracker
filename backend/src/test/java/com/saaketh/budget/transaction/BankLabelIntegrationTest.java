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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Other banks' category labels map onto the default categories after a real upload. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BankLabelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            chase-credit-card.csv  | CAMPUS COFFEE    | Food & Dining
            chase-credit-card.csv  | FRESH MART       | Groceries
            discover.csv           | CAMPUS COFFEE    | Food & Dining
            discover.csv           | FRESH MART       | Groceries
            discover.csv           | INTERNET PAYMENT | Payments & Transfers
            american-express.csv   | CAMPUS COFFEE    | Food & Dining
            american-express.csv   | FRESH MART       | Groceries
            american-express.csv   | BOOKSTORE        | Shopping
            apple-card.csv         | CAMPUS COFFEE    | Food & Dining
            apple-card.csv         | ACH DEPOSIT      | Payments & Transfers
            """)
    void mapsOtherBanksLabels(String file, String search, String expectedCategory) throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        String response = mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Card\"}"))
                .andReturn().getResponse().getContentAsString();
        long account = ((Number) JsonPath.read(response, "$.id")).longValue();
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", file, "text/csv",
                                Files.readAllBytes(Path.of("..", "samples", "layouts", file))))
                        .param("accountId", String.valueOf(account))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transactions").session(session).param("q", search))
                .andExpect(jsonPath("$.items[0].categoryName").value(expectedCategory))
                .andExpect(jsonPath("$.items[0].categorySource").value("BANK"));
    }
}
