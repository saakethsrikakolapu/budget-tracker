package com.saaketh.budget.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** The request sent to Brevo's API, checked against a fake server (nothing leaves the machine). */
class BrevoApiEmailSenderTest {

    @Test
    void sendsTheEmailAsBrevoExpects() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BrevoApiEmailSender.API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-key"))
                .andExpect(content().json("""
                        {
                          "sender": {"name": "Spendr", "email": "no-reply@8022029.xyz"},
                          "to": [{"email": "student@example.com"}],
                          "subject": "Reset your Spendr password",
                          "textContent": "Choose a new password here: https://www.8022029.xyz/reset-password?token=abc"
                        }"""))
                .andRespond(withSuccess("{\"messageId\": \"<1@smtp-relay.mailin.fr>\"}", MediaType.APPLICATION_JSON));

        new BrevoApiEmailSender(builder.build(), "test-key", "Spendr <no-reply@8022029.xyz>")
                .send("student@example.com", "Reset your Spendr password",
                        "Choose a new password here: https://www.8022029.xyz/reset-password?token=abc");

        server.verify();
    }

    @Test
    void anErrorFromBrevoIsThrownSoItGetsLogged() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BrevoApiEmailSender.API_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Key not found\"}"));

        BrevoApiEmailSender sender = new BrevoApiEmailSender(builder.build(), "wrong", "no-reply@8022029.xyz");
        assertThatThrownBy(() -> sender.send("a@b.c", "s", "b")).isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void readsTheFromAddressWithOrWithoutAName() {
        assertThat(BrevoApiEmailSender.parseFrom("Spendr <no-reply@8022029.xyz>"))
                .isEqualTo(Map.of("name", "Spendr", "email", "no-reply@8022029.xyz"));
        assertThat(BrevoApiEmailSender.parseFrom("  no-reply@8022029.xyz "))
                .isEqualTo(Map.of("email", "no-reply@8022029.xyz"));
        assertThat(BrevoApiEmailSender.parseFrom("<no-reply@8022029.xyz>"))
                .isEqualTo(Map.of("email", "no-reply@8022029.xyz"));
    }
}
