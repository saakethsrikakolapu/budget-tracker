package com.saaketh.budget.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saaketh.budget.TestcontainersConfiguration;
import com.saaketh.budget.email.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Email verification and password reset, with emails captured instead of sent. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class EmailFlowsIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String VERIFY = "Verify your email";
    private static final String RESET = "Reset your";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailSender emails;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // --- verification ---

    @Test
    void signUpSendsAVerificationLinkThatWorksOnce() throws Exception {
        String email = newEmail();
        MockHttpSession session = registerAndLogin(email);
        assertThat(emails.to(email)).hasSize(1);
        assertThat(emails.to(email).getFirst().body()).contains("http://localhost:5173/verify-email?token=");
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.emailVerified").value(false));

        String token = emails.latestToken(email, VERIFY);
        // Works without being logged in (the link may be opened on another device).
        postJson("/api/auth/verify-email", "{\"token\": \"" + token + "\"}").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.emailVerified").value(true));
        postJson("/api/auth/verify-email", "{\"token\": \"" + token + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(AccountEmailService.INVALID_LINK));
    }

    @Test
    void resendIsThrottledAndStopsOnceVerified() throws Exception {
        String email = newEmail();
        MockHttpSession session = registerAndLogin(email);

        // Within a minute of the sign-up email: ignored.
        mockMvc.perform(post("/api/auth/resend-verification").session(session).with(csrf()))
                .andExpect(status().isAccepted());
        assertThat(emails.to(email)).hasSize(1);

        // A minute later it sends a fresh link, and the old one stops working.
        String first = emails.latestToken(email, VERIFY);
        jdbcTemplate.update("UPDATE email_tokens SET created_at = created_at - interval '2 minutes'");
        mockMvc.perform(post("/api/auth/resend-verification").session(session).with(csrf()))
                .andExpect(status().isAccepted());
        assertThat(emails.to(email)).hasSize(2);
        postJson("/api/auth/verify-email", "{\"token\": \"" + first + "\"}").andExpect(status().isBadRequest());
        postJson("/api/auth/verify-email", "{\"token\": \"" + emails.latestToken(email, VERIFY) + "\"}")
                .andExpect(status().isNoContent());

        // Verified: nothing more to send.
        jdbcTemplate.update("UPDATE email_tokens SET created_at = created_at - interval '2 minutes'");
        mockMvc.perform(post("/api/auth/resend-verification").session(session).with(csrf()))
                .andExpect(status().isAccepted());
        assertThat(emails.to(email)).hasSize(2);
    }

    @Test
    void expiredOrGarbledLinksAreRejected() throws Exception {
        String email = newEmail();
        registerAndLogin(email);
        String token = emails.latestToken(email, VERIFY);
        jdbcTemplate.update("UPDATE email_tokens SET expires_at = now() - interval '1 minute' WHERE token_hash = ?",
                AccountEmailService.hash(token));

        postJson("/api/auth/verify-email", "{\"token\": \"" + token + "\"}").andExpect(status().isBadRequest());
        postJson("/api/auth/verify-email", "{\"token\": \"not-a-real-token\"}").andExpect(status().isBadRequest());
        postJson("/api/auth/verify-email", "{\"token\": \"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void tokensAreStoredOnlyAsHashes() throws Exception {
        String email = newEmail();
        registerAndLogin(email);
        String token = emails.latestToken(email, VERIFY);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM email_tokens WHERE token_hash = ?",
                Integer.class, token)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM email_tokens WHERE token_hash = ?",
                Integer.class, AccountEmailService.hash(token))).isEqualTo(1);
    }

    // --- password reset ---

    @Test
    void resetSetsANewPasswordOnceAndVerifiesTheEmail() throws Exception {
        String email = newEmail();
        registerAndLogin(email);

        postJson("/api/auth/forgot-password", "{\"email\": \"  " + email.toUpperCase() + " \"}")
                .andExpect(status().isAccepted());
        String token = emails.latestToken(email, RESET);
        assertThat(emails.to(email).getLast().body()).contains("http://localhost:5173/reset-password?token=");

        postJson("/api/auth/reset-password", "{\"token\": \"" + token + "\", \"password\": \"brand-new-password\"}")
                .andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, "brand-new-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true)); // they opened a link sent to it
        // The link only works once.
        postJson("/api/auth/reset-password", "{\"token\": \"" + token + "\", \"password\": \"another-password\"}")
                .andExpect(status().isBadRequest());
        // Logging out other devices needs real sessions: see SessionPersistenceIntegrationTest.
    }

    @Test
    void forgotPasswordLooksTheSameForUnknownEmails() throws Exception {
        String nobody = newEmail();
        postJson("/api/auth/forgot-password", "{\"email\": \"" + nobody + "\"}").andExpect(status().isAccepted());
        assertThat(emails.to(nobody)).isEmpty();
        postJson("/api/auth/forgot-password", "{\"email\": \"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void atMostThreeResetEmailsPerHourAndOnlyTheNewestLinkWorks() throws Exception {
        String email = newEmail();
        registerAndLogin(email);

        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/forgot-password", "{\"email\": \"" + email + "\"}").andExpect(status().isAccepted());
        }
        assertThat(emails.to(email).stream().filter(s -> s.subject().contains(RESET))).hasSize(3);

        String newest = emails.latestToken(email, RESET);
        String older = emails.to(email).stream().filter(s -> s.subject().contains(RESET)).findFirst().orElseThrow()
                .body().replaceAll("(?s).*token=([A-Za-z0-9_-]+).*", "$1");
        postJson("/api/auth/reset-password", "{\"token\": \"" + older + "\", \"password\": \"brand-new-password\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/auth/reset-password", "{\"token\": \"" + newest + "\", \"password\": \"brand-new-password\"}")
                .andExpect(status().isNoContent());
    }

    @Test
    void resetValidatesTheNewPassword() throws Exception {
        String email = newEmail();
        registerAndLogin(email);
        postJson("/api/auth/forgot-password", "{\"email\": \"" + email + "\"}");
        String token = emails.latestToken(email, RESET);

        postJson("/api/auth/reset-password", "{\"token\": \"" + token + "\", \"password\": \"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password must be 8 to 72 characters"));
        // Still usable after a rejected attempt.
        postJson("/api/auth/reset-password", "{\"token\": \"" + token + "\", \"password\": \"long-enough-now\"}")
                .andExpect(status().isNoContent());
    }

    @Test
    void emailFailuresDontBreakSignUp() throws Exception {
        // RecordingEmailSender never fails, so check the guard directly: a throwing sender is caught.
        AccountEmailService service = new AccountEmailService(null, null, null, (to, subject, body) -> {
            throw new IllegalStateException("SMTP down");
        }, null, "http://x");
        var send = AccountEmailService.class.getDeclaredMethod("send", String.class, String.class, String.class);
        send.setAccessible(true);
        send.invoke(service, "a@b.c", "subject", "body"); // no exception
    }

    // --- helpers ---

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private MockHttpSession registerAndLogin(String email) throws Exception {
        postJson("/api/auth/register", body(email, PASSWORD)).andExpect(status().isCreated());
        return (MockHttpSession) login(email, PASSWORD).andExpect(status().isOk()).andReturn().getRequest()
                .getSession(false);
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", body(email, password));
    }

    private static String body(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
    }

    private static String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
