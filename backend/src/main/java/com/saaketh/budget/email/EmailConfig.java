package com.saaketh.budget.email;

import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

/**
 * Picks how email goes out, in this order:
 * <ol>
 *   <li>Brevo's HTTPS API, when BREVO_API_KEY is set (production: Render's free plan blocks SMTP
 *       ports, so SMTP can't be used there);</li>
 *   <li>SMTP, when spring.mail.host is set (Spring Boot only creates a JavaMailSender then);</li>
 *   <li>otherwise emails are logged (local development).</li>
 * </ol>
 */
@Configuration
public class EmailConfig {

    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:Budget Tracker <no-reply@localhost>}") String from,
            @Value("${app.mail.log-body:true}") boolean logBody,
            @Value("${app.mail.brevo-api-key:}") String brevoApiKey) {
        if (!brevoApiKey.isBlank()) {
            return new BrevoApiEmailSender(brevoClient(), brevoApiKey, from);
        }
        JavaMailSender smtp = mailSender.getIfAvailable();
        return smtp != null ? new SmtpEmailSender(smtp, from) : new LoggingEmailSender(logBody);
    }

    /** Short timeouts: a slow email service must not hold a request for long. */
    private static RestClient brevoClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory).build();
    }
}
