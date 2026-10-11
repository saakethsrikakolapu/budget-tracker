package com.saaketh.budget.email;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test stand-in for email: keeps what would have been sent so tests can read the links. */
public class RecordingEmailSender implements EmailSender {

    public record Sent(String to, String subject, String body) {
    }

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Sent(to, subject, body));
    }

    public List<Sent> to(String email) {
        return sent.stream().filter(s -> s.to().equals(email)).toList();
    }

    /** The token in the newest email to this address whose subject contains the given text. */
    public String latestToken(String email, String subjectContains) {
        Sent last = to(email).stream().filter(s -> s.subject().contains(subjectContains)).reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("No \"" + subjectContains + "\" email to " + email));
        Matcher m = TOKEN.matcher(last.body());
        if (!m.find()) {
            throw new AssertionError("No link in: " + last.body());
        }
        return m.group(1);
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }
}
