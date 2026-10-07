package com.saaketh.budget.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saaketh.budget.TestcontainersConfiguration;
import com.saaketh.budget.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Register, login, logout, and access rules, end to end against a real Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerCreatesAccountWithNormalizedEmail() throws Exception {
        String email = uniqueEmail();

        register("  " + email.toUpperCase() + " ", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        String hash = userRepository.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists"));
    }

    @Test
    void registerRejectsInvalidEmailAndShortPassword() throws Exception {
        register("not-an-email", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerRejectsPasswordOver72Bytes() throws Exception {
        // 40 characters, but "é" is 2 bytes in UTF-8, so 80 bytes: BCrypt would silently truncate it.
        register(uniqueEmail(), "é".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password is too long"));
    }

    @Test
    void loginThenMeReturnsCurrentUser() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        MockHttpSession session = login(email, PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void loginFailuresLookIdenticalForWrongPasswordAndUnknownEmail() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        postJson("/api/auth/login", email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        postJson("/api/auth/login", uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsSession() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        MockHttpSession session = login(email, PASSWORD);

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(uniqueEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    // --- helpers ---

    private ResultActions register(String email, String password) throws Exception {
        return postJson("/api/auth/register", email, password);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) postJson("/api/auth/login", email, password)
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private ResultActions postJson(String url, String email, String password) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(email, password))
                .with(csrf()));
    }

    private static String json(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }

    /** Tests share one database, so every test uses its own email. */
    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
