package com.saaketh.budget.auth;

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
import com.saaketh.budget.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
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

/** "Delete my account": everything the user owns is gone, nobody else's data is touched. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountDeletionIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deletesEverythingTheUserOwnsAndNothingElse() throws Exception {
        String email = newEmail();
        MockHttpSession session = registerAndLogin(email);
        long userId = fillWithData(session);
        MockHttpSession other = registerAndLogin(newEmail());
        long otherId = fillWithData(other);
        // Every table with a user_id column, read from the database itself: a table added later is
        // checked automatically (and this fails until fillWithData puts something in it).
        List<String> userTables = jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = 'user_id'""", String.class);
        assertThat(userTables).contains("transactions", "categories", "recurring_hidden");
        for (String table : userTables) {
            assertThat(rows(table, userId)).as(table + " has data before").isPositive();
        }

        deleteAccount(session, PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, userId))
                .isZero();
        for (String table : userTables) {
            assertThat(rows(table, userId)).as(table + " emptied").isZero();
            assertThat(rows(table, otherId)).as("other user's " + table + " untouched").isPositive();
        }
        // Logged out, can't log back in, and the email is free to sign up again.
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        login(email).andExpect(status().isUnauthorized());
        register(email).andExpect(status().isCreated());
    }

    @Test
    void wrongOrMissingPasswordDeletesNothing() throws Exception {
        String email = newEmail();
        MockHttpSession session = registerAndLogin(email);
        long userId = fillWithData(session);

        deleteAccount(session, "not-my-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Wrong password"));
        deleteAccount(session, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Enter your password to confirm"));

        assertThat(rows("transactions", userId)).isPositive();
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
    }

    @Test
    void requiresLoginAndCsrf() throws Exception {
        mockMvc.perform(delete("/api/auth/account").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"x\"}"))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = registerAndLogin(newEmail());
        // No CSRF token: another website can't delete your account by tricking your browser.
        mockMvc.perform(delete("/api/auth/account").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
    }

    // --- helpers ---

    /** An import (accounts, batches, transactions; categories exist from signup), a rule, a budget,
     * a remembered format, and a hidden merchant. Returns the user's id. */
    private long fillWithData(MockHttpSession session) throws Exception {
        long account = id(mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Card\"}")));
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv",
                                Files.readAllBytes(Path.of("..", "samples", "capital-one-credit-card.csv"))))
                        .param("accountId", String.valueOf(account))
                        .param("rememberFormat", "true")
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
        String categories = mockMvc.perform(get("/api/categories").session(session))
                .andReturn().getResponse().getContentAsString();
        long shopping = JsonPath.<List<Number>>read(categories, "$[?(@.name == 'Shopping')].id").getFirst().longValue();
        mockMvc.perform(post("/api/rules").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pattern\": \"POSHMARK\", \"categoryId\": " + shopping + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/budgets/" + shopping).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlyLimit\": \"100.00\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/recurring/hidden/STARBUCKS").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        return id(mockMvc.perform(get("/api/auth/me").session(session)));
    }

    private int rows(String table, long userId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class,
                userId);
    }

    private ResultActions deleteAccount(MockHttpSession session, String password) throws Exception {
        return mockMvc.perform(delete("/api/auth/account").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"" + password + "\"}"));
    }

    private MockHttpSession registerAndLogin(String email) throws Exception {
        register(email).andExpect(status().isCreated());
        return (MockHttpSession) login(email).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(email)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(email)));
    }

    private static String body(String email) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}";
    }

    private static String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
