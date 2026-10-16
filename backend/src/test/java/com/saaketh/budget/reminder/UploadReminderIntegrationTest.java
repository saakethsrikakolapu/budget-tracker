package com.saaketh.budget.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saaketh.budget.TestcontainersConfiguration;
import com.saaketh.budget.email.RecordingEmailSender;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

/** The opt-in monthly "time to upload your statement" email. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class UploadReminderIntegrationTest {

    private static final String SUBJECT = "Time to upload";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailSender emails;

    @Autowired
    private UploadReminderService reminders;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void remindsOptedInPeopleOnceAMonthUntilTheyUpload() throws Exception {
        String email = newEmail();
        MockHttpSession session = signUp(email, true);
        upload(session, "09/20/2026,CHIPOTLE 1234,-12.00\n");
        setReminders(session, true);

        // October 3: September isn't fully uploaded yet (it ends on the 20th).
        reminders.sendDue(LocalDate.of(2026, 10, 3));
        assertThat(reminders(email)).hasSize(1);
        RecordingEmailSender.Sent sent = emails.to(email).getLast();
        assertThat(sent.subject()).isEqualTo("Time to upload your September statement");
        assertThat(sent.body()).contains("end on September 20", "/import", "/account");

        // Running again the same month sends nothing more.
        reminders.sendDue(LocalDate.of(2026, 10, 3));
        reminders.sendDue(LocalDate.of(2026, 10, 20));
        assertThat(reminders(email)).hasSize(1);

        // They upload through the end of October: no reminder in November.
        upload(session, "10/29/2026,CHIPOTLE 1234,-9.00\n");
        reminders.sendDue(LocalDate.of(2026, 11, 3));
        assertThat(reminders(email)).hasSize(1);

        // Still nothing new by December: reminded about November.
        reminders.sendDue(LocalDate.of(2026, 12, 3));
        assertThat(reminders(email)).hasSize(2);
        assertThat(emails.to(email).getLast().subject()).isEqualTo("Time to upload your November statement");
    }

    @Test
    void offByDefaultAndNeverSentToUnconfirmedEmails() throws Exception {
        String notOptedIn = newEmail();
        signUp(notOptedIn, true);

        String unverified = newEmail();
        MockHttpSession session = signUp(unverified, false);
        setReminders(session, true);

        reminders.sendDue(LocalDate.of(2026, 10, 3));

        assertThat(reminders(notOptedIn)).isEmpty();
        assertThat(reminders(unverified)).isEmpty();
    }

    @Test
    void theSettingShowsUpOnTheUserAndCanBeTurnedOff() throws Exception {
        String email = newEmail();
        MockHttpSession session = signUp(email, true);
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.uploadReminders").value(false));

        setReminders(session, true);
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.uploadReminders").value(true));

        setReminders(session, false);
        reminders.sendDue(LocalDate.of(2026, 10, 3));
        assertThat(reminders(email)).isEmpty();

        mockMvc.perform(put("/api/auth/reminders").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private java.util.List<RecordingEmailSender.Sent> reminders(String email) {
        return emails.to(email).stream().filter(s -> s.subject().startsWith(SUBJECT)).toList();
    }

    private MockHttpSession signUp(String email, boolean verified) throws Exception {
        String body = "{\"email\": \"" + email + "\", \"password\": \"correct-horse-battery\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()))
                .andExpect(status().isCreated());
        if (verified) {
            jdbcTemplate.update("UPDATE users SET email_verified_at = now() WHERE email = ?", email);
        }
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private void setReminders(MockHttpSession session, boolean enabled) throws Exception {
        mockMvc.perform(put("/api/auth/reminders").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": " + enabled + "}"))
                .andExpect(status().isOk());
    }

    private void upload(MockHttpSession session, String rows) throws Exception {
        String accounts = mockMvc.perform(get("/api/accounts").session(session))
                .andReturn().getResponse().getContentAsString();
        long account = JsonPath.<java.util.List<Object>>read(accounts, "$").isEmpty()
                ? ((Number) JsonPath.read(mockMvc.perform(post("/api/accounts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Card\"}"))
                        .andReturn().getResponse().getContentAsString(), "$.id")).longValue()
                : ((Number) JsonPath.read(accounts, "$[0].id")).longValue();
        byte[] csv = ("Date,Description,Amount\n" + rows).getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "s.csv", "text/csv", csv))
                        .param("accountId", String.valueOf(account))
                        .session(session).with(csrf()))
                .andExpect(status().isCreated());
    }

    private static String newEmail() {
        return "reminder-" + UUID.randomUUID() + "@example.com";
    }
}
