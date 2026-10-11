package com.saaketh.budget.email;

/**
 * Sends one plain-text email. Two implementations: SMTP in production (any provider), and a
 * logger for local development so links can be copied from the backend's output.
 */
public interface EmailSender {

    void send(String to, String subject, String body);
}
