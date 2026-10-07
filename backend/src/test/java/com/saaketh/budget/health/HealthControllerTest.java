package com.saaketh.budget.health;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saaketh.budget.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class) // use our real security rules (health is public), not Boot's defaults
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    /** SecurityConfig needs one; this test never logs in, so a mock is enough. */
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void healthReturnsUpWhenDatabaseResponds() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    void healthReturns503WhenDatabaseIsDown() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.database").value("DOWN"));
    }
}
