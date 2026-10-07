package com.saaketh.budget;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** Test helper: creates a brand-new user and returns a logged-in session for them. */
public final class TestUsers {

    private TestUsers() {
    }

    public static MockHttpSession registerAndLogin(MockMvc mockMvc) throws Exception {
        String body = """
                {"email": "user-%s@example.com", "password": "correct-horse-battery"}""".formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()))
                .andExpect(status().isCreated());
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
