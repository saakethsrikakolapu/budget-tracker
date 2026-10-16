package com.saaketh.budget.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saaketh.budget.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

/** The Account page: download my data, change password, rename or remove a bank account. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountSettingsIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    // --- download my data ---

    @Test
    void downloadsEveryTransactionAsCsv() throws Exception {
        MockHttpSession session = login(register());
        long card = createAccount(session, "Chase, Freedom");
        upload(session, card, """
                Date,Description,Amount
                09/02/2026,"CHIPOTLE 1234",-12.50
                09/05/2026,=HYPERLINK("http://evil.example"),-1.00
                """);

        String csv = mockMvc.perform(get("/api/transactions/export").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo("Date,Description,Amount,Category,Account");
        assertThat(lines).hasSize(3);
        // Newest first; a formula stays text; a comma in a name is quoted.
        assertThat(lines[1]).startsWith("2026-09-05,\"'=HYPERLINK(\"\"http://evil.example\"\")\",-1.00,");
        assertThat(lines[2]).isEqualTo("2026-09-02,CHIPOTLE 1234,-12.50,Food & Dining,\"Chase, Freedom\"");
    }

    // --- change password ---

    // Logging out the other devices needs real stored sessions: see SessionPersistenceIntegrationTest.
    @Test
    void changingThePasswordNeedsTheCurrentOne() throws Exception {
        String email = register();
        MockHttpSession here = login(email);

        changePassword(here, "wrong-password-1", "new-password-123").andExpect(status().isBadRequest());
        changePassword(here, PASSWORD, "short").andExpect(status().isBadRequest());
        changePassword(here, PASSWORD, "new-password-123").andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(here)).andExpect(status().isOk());
        loginWith(email, PASSWORD).andExpect(status().isUnauthorized());
        loginWith(email, "new-password-123").andExpect(status().isOk());
    }

    // --- bank accounts ---

    @Test
    void renameAndRemoveBankAccounts() throws Exception {
        MockHttpSession session = login(register());
        long card = createAccount(session, "Card");
        long checking = createAccount(session, "Checking");
        upload(session, card, "Date,Description,Amount\n09/02/2026,CHIPOTLE 1234,-12.50\n");
        upload(session, checking, "Date,Description,Amount\n09/03/2026,HARRIS TEETER 0318,-40.00\n");

        rename(session, card, "Chase Freedom").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Chase Freedom"));
        rename(session, card, "Checking").andExpect(status().isConflict());
        rename(session, card, " ").andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/accounts/" + checking).session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/accounts").session(session))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Chase Freedom"));
        // The removed account's transactions are gone; the other account's stay.
        mockMvc.perform(get("/api/transactions").session(session).param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(jsonPath("$.totals.count").value(1));
    }

    @Test
    void otherPeoplesAccountsCantBeRenamedOrRemoved() throws Exception {
        MockHttpSession owner = login(register());
        long card = createAccount(owner, "Card");
        MockHttpSession stranger = login(register());

        rename(stranger, card, "Mine now").andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/accounts/" + card).session(stranger).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/accounts").session(owner)).andExpect(jsonPath("$[0].name").value("Card"));
    }

    // --- helpers ---

    private String register() throws Exception {
        String email = "settings-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).with(csrf())
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) loginWith(email, PASSWORD).andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private ResultActions loginWith(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).with(csrf())
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }

    private ResultActions changePassword(MockHttpSession session, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/auth/change-password").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"" + current + "\", \"newPassword\": \"" + next + "\"}"));
    }

    private long createAccount(MockHttpSession session, String name) throws Exception {
        String json = mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions rename(MockHttpSession session, long id, String name) throws Exception {
        return mockMvc.perform(put("/api/accounts/" + id).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + name + "\"}"));
    }

    private void upload(MockHttpSession session, long account, String csv) throws Exception {
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("accountId", String.valueOf(account))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
    }
}
