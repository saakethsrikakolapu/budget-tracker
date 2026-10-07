package com.saaketh.budget.category;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void newUsersStartWithTheDefaultCategories() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);

        mockMvc.perform(get("/api/categories").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(DefaultCategories.ALL.size()))
                // Sorted by name
                .andExpect(jsonPath("$[0].name").value("Bills & Utilities"))
                .andExpect(jsonPath("$[?(@.name == 'Payments & Transfers')].countsAsSpending").value(false))
                .andExpect(jsonPath("$[?(@.name == 'Income')].countsAsSpending").value(false))
                .andExpect(jsonPath("$[?(@.name == 'Groceries')].countsAsSpending").value(true))
                .andExpect(jsonPath("$[0].transactionCount").value(0));
    }

    /** The V5 migration backfilled the same list for users who existed before categories. */
    @Test
    void migrationBackfillListMatchesJavaDefaults() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V5__create_categories.sql"));
        Matcher m = Pattern.compile("\\('([^']+)', (TRUE|FALSE)\\)").matcher(sql);
        List<DefaultCategories.Default> fromSql = m.results()
                .map(r -> new DefaultCategories.Default(r.group(1), Boolean.parseBoolean(r.group(2).toLowerCase())))
                .toList();

        assertThat(fromSql).containsExactlyElementsOf(DefaultCategories.ALL);
    }

    @Test
    void createRenameAndToggleSpending() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);

        long id = idOf(create(session, "  Textbooks ", true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Textbooks")));

        update(session, id, "Books & Supplies", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Books & Supplies"))
                .andExpect(jsonPath("$.countsAsSpending").value(false));
        // Changing only the capitalization of its own name is allowed.
        update(session, id, "BOOKS & SUPPLIES", false).andExpect(status().isOk());
    }

    @Test
    void namesAreUniqueIgnoringCase() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);

        create(session, "groceries", true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.name").value("You already have a category with this name"));

        long id = idOf(create(session, "Coffee", true));
        update(session, id, "FOOD & DINING", true).andExpect(status().isConflict());
    }

    @Test
    void rejectsBlankOrTooLongNamesAndMissingFlag() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);

        create(session, "   ", true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
        create(session, "x".repeat(51), true).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/categories").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Pets\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.countsAsSpending").exists());
    }

    @Test
    void deletingACategoryMakesItsTransactionsUncategorized() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        long category = idOf(create(session, "Coffee", true));
        long account = createAccount(session);
        uploadOneTransaction(session, account);
        // Assigning categories comes in piece 2; set it directly for this test.
        jdbcTemplate.update("UPDATE transactions SET category_id = ? WHERE account_id = ?", category, account);

        mockMvc.perform(get("/api/categories").session(session))
                .andExpect(jsonPath("$[?(@.name == 'Coffee')].transactionCount").value(1));

        mockMvc.perform(delete("/api/categories/" + category).session(session).with(csrf()))
                .andExpect(status().isNoContent());

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM transactions WHERE account_id = ? AND category_id IS NULL", Integer.class, account);
        assertThat(remaining).as("transaction kept, now uncategorized").isEqualTo(1);
    }

    @Test
    void cannotSeeChangeOrDeleteAnotherUsersCategories() throws Exception {
        MockHttpSession alice = TestUsers.registerAndLogin(mockMvc);
        MockHttpSession bob = TestUsers.registerAndLogin(mockMvc);
        long alicesCategory = idOf(create(alice, "Alice Only", true));

        mockMvc.perform(get("/api/categories").session(bob))
                .andExpect(jsonPath("$[?(@.name == 'Alice Only')]").isEmpty());
        update(bob, alicesCategory, "Hacked", true).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/categories/" + alicesCategory).session(bob).with(csrf()))
                .andExpect(status().isNotFound());
        // Bob can still use the same name for his own category.
        create(bob, "Alice Only", true).andExpect(status().isCreated());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private ResultActions create(MockHttpSession session, String name, boolean countsAsSpending) throws Exception {
        return mockMvc.perform(post("/api/categories").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "countsAsSpending": %s}""".formatted(name, countsAsSpending)));
    }

    private ResultActions update(MockHttpSession session, long id, String name, boolean countsAsSpending)
            throws Exception {
        return mockMvc.perform(put("/api/categories/" + id).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "countsAsSpending": %s}""".formatted(name, countsAsSpending)));
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long createAccount(MockHttpSession session) throws Exception {
        String response = mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Card\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void uploadOneTransaction(MockHttpSession session, long accountId) throws Exception {
        String csv = """
                Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
                2026-09-22,2026-09-23,1234,CAMPUS COFFEE CO,Dining,4.75,
                """;
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes()))
                        .param("accountId", String.valueOf(accountId))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
    }
}
