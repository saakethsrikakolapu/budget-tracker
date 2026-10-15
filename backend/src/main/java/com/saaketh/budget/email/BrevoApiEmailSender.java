package com.saaketh.budget.email;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Sends through Brevo's HTTPS API instead of SMTP. Render's free plan blocks outgoing SMTP ports
 * (25, 465, 587), but ordinary HTTPS on port 443 works, so this is what production uses.
 * Docs: POST https://api.brevo.com/v3/smtp/email with an "api-key" header.
 */
public class BrevoApiEmailSender implements EmailSender {

    static final String API_URL = "https://api.brevo.com/v3/smtp/email";
    /** "Spendr <no-reply@example.com>" or just "no-reply@example.com". */
    private static final Pattern NAME_AND_ADDRESS = Pattern.compile("^\\s*(.*?)\\s*<\\s*([^>]+?)\\s*>\\s*$");

    private final RestClient restClient;
    private final String apiKey;
    private final Map<String, String> sender;

    public BrevoApiEmailSender(RestClient restClient, String apiKey, String from) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.sender = parseFrom(from);
    }

    @Override
    public void send(String to, String subject, String body) {
        Map<String, Object> request = Map.of(
                "sender", sender,
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", body);
        // Throws on a 4xx/5xx response (bad key, unverified sender, blocked IP); the caller logs it.
        restClient.post()
                .uri(API_URL)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    static Map<String, String> parseFrom(String from) {
        Matcher m = NAME_AND_ADDRESS.matcher(from);
        if (m.matches()) {
            return m.group(1).isBlank() ? Map.of("email", m.group(2)) : Map.of("name", m.group(1), "email", m.group(2));
        }
        return Map.of("email", from.trim());
    }
}
