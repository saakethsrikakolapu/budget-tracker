package com.saaketh.budget.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no SMTP server is configured. In development it prints the whole email (so you can
 * click the link from the backend's console). In production it never prints the body: a reset
 * link in a log would let anyone with log access take over the account.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final boolean showBody;

    public LoggingEmailSender(boolean showBody) {
        this.showBody = showBody;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (showBody) {
            log.info("Email (not sent, no SMTP configured) to {}: {}\n{}", to, subject, body);
        } else {
            log.warn("Email to {} ({}) was not sent: set SPRING_MAIL_HOST and friends to enable email.", to, subject);
        }
    }
}
