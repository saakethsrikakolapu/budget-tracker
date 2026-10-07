package com.saaketh.budget.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saaketh.budget.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Production stores login sessions in Postgres (Spring Session JDBC) so they survive restarts.
 * Other tests turn Spring Session off (see src/test/resources/config/application.properties);
 * this one turns it back on and logs in with a real session cookie, like a browser would.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",                // turn Spring Session back on
        "server.servlet.session.cookie.secure=true"})   // as in application-prod.properties
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loginSessionIsStoredInPostgresAndRemovedOnLogout() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"email": "%s", "password": "correct-horse-battery"}""".formatted(email);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()))
                .andExpect(status().isCreated());

        Cookie sessionCookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");

        assertThat(sessionCookie).as("Spring Session's cookie").isNotNull();
        assertThat(sessionCookie.isHttpOnly()).as("HttpOnly: JavaScript can't read it").isTrue();
        assertThat(sessionCookie.getSecure()).as("Secure: only sent over HTTPS").isTrue();
        assertThat(sessionCookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(sessionsFor(email)).as("session row in Postgres").isEqualTo(1);

        // The cookie alone is enough to be recognized, exactly like a browser after a server restart.
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie).with(csrf())).andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie)).andExpect(status().isUnauthorized());
        assertThat(sessionsFor(email)).as("session row deleted on logout").isZero();
    }

    @Test
    void anonymousRequestsDoNotCreateSessions() throws Exception {
        Integer before = jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session", Integer.class);

        var response = mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andReturn().getResponse();

        assertThat(response.getCookie("SESSION")).as("no session cookie for a denied anonymous request").isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session", Integer.class)).isEqualTo(before);
    }

    private int sessionsFor(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = ?", Integer.class, email);
    }
}
