package com.saaketh.budget.email;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Picks how email goes out. Spring Boot only creates a JavaMailSender when spring.mail.host is set
 * (in production: the SPRING_MAIL_HOST environment variable), so without it emails are logged.
 */
@Configuration
public class EmailConfig {

    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:Budget Tracker <no-reply@localhost>}") String from,
            @Value("${app.mail.log-body:true}") boolean logBody) {
        JavaMailSender smtp = mailSender.getIfAvailable();
        return smtp != null ? new SmtpEmailSender(smtp, from) : new LoggingEmailSender(logBody);
    }
}
