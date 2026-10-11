package com.saaketh.budget.auth;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saaketh.budget.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Password guessing through the real login endpoint (default: 5 failures per email per 15 minutes). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginRateLimitIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fiveWrongPasswordsLockTheEmailEvenForTheRightPassword() throws Exception {
        String email = register();
        for (int i = 0; i < 5; i++) {
            login(email, "guess-" + i).andExpect(status().isUnauthorized());
        }

        // Locked: even the right password is refused, so a guesser can't tell when they got it.
        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("8[5-9][0-9]|900"))) // ~15 min
                .andExpect(jsonPath("$.detail").value("Too many failed login attempts. Try again in 15 minutes."));
        // The email is matched however it's typed.
        login("  " + email.toUpperCase() + " ", PASSWORD).andExpect(status().isTooManyRequests());
        // Other people aren't affected.
        login(register(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void successBeforeTheLimitResetsTheCount() throws Exception {
        String email = register();
        for (int i = 0; i < 4; i++) {
            login(email, "typo-" + i).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(email, "typo-" + i).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownEmailsCountToo() throws Exception {
        String nobody = "nobody-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 5; i++) {
            login(nobody, PASSWORD).andExpect(status().isUnauthorized());
        }
        // Same response as a real account, so the limit doesn't reveal which emails exist.
        login(nobody, PASSWORD).andExpect(status().isTooManyRequests());
    }

    private String register() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }
}
