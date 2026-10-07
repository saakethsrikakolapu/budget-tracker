package com.saaketh.budget.account;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saaketh.budget.TestUsers;
import com.saaketh.budget.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createAndListAccountsSortedByName() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);

        create(session, "  Discover It  ").andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Discover It"));
        create(session, "Capital One Quicksilver").andExpect(status().isCreated());

        mockMvc.perform(get("/api/accounts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Capital One Quicksilver"))
                .andExpect(jsonPath("$[1].name").value("Discover It"));
    }

    @Test
    void rejectsDuplicateAndBlankNames() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        create(session, "Checking").andExpect(status().isCreated());

        create(session, "Checking").andExpect(status().isConflict()).andExpect(jsonPath("$.errors.name").exists());
        create(session, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void usersOnlySeeTheirOwnAccounts() throws Exception {
        MockHttpSession alice = TestUsers.registerAndLogin(mockMvc);
        MockHttpSession bob = TestUsers.registerAndLogin(mockMvc);
        create(alice, "Alice Card").andExpect(status().isCreated());

        mockMvc.perform(get("/api/accounts").session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        // Different users may use the same account name.
        create(bob, "Alice Card").andExpect(status().isCreated());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    private ResultActions create(MockHttpSession session, String name) throws Exception {
        return mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s"}""".formatted(name)));
    }
}
