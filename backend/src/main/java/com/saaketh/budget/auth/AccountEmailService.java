package com.saaketh.budget.auth;

import com.saaketh.budget.auth.EmailToken.Purpose;
import com.saaketh.budget.common.BadRequestException;
import com.saaketh.budget.email.EmailSender;
import com.saaketh.budget.user.User;
import com.saaketh.budget.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emailed one-time links: "verify your email" and "reset your password".
 *
 * <p>How the links are kept safe:
 * <ul>
 *   <li>each link carries 256 random bits; only their SHA-256 hash is stored, so a database leak
 *       doesn't leak working links;</li>
 *   <li>links expire (reset: {@value #RESET_MINUTES} minutes, verify: {@value #VERIFY_HOURS} hours),
 *       work once, and sending a new one cancels the old ones;</li>
 *   <li>"forgot password" never reveals whether an email has an account, and sends at most
 *       {@value #MAX_RESETS_PER_HOUR} emails per account per hour.</li>
 * </ul>
 * Database work happens in short transactions; the email is sent after, so a slow mail server
 * never holds a database connection.
 */
@Service
public class AccountEmailService {

    static final int RESET_MINUTES = 30;
    static final int VERIFY_HOURS = 24;
    static final int MAX_RESETS_PER_HOUR = 3;
    /** "Resend verification" more often than this is ignored (double-clicks, impatience). */
    static final Duration RESEND_GAP = Duration.ofMinutes(1);
    static final String INVALID_LINK = "This link is invalid or has expired. Request a new one.";

    private static final Logger log = LoggerFactory.getLogger(AccountEmailService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Link(String email, String url) {
    }

    private final UserRepository userRepository;
    private final EmailTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final TransactionTemplate transaction;
    private final String baseUrl;

    public AccountEmailService(UserRepository userRepository, EmailTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder, EmailSender emailSender, TransactionTemplate transaction,
            @Value("${app.base-url:http://localhost:5173}") String baseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.transaction = transaction;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** After sign-up, or when the user asks again. Does nothing if already verified or just sent. */
    public void sendVerification(Long userId) {
        Link link = transaction.execute(status -> {
            User user = userRepository.findById(userId).orElse(null);
            Instant now = Instant.now();
            if (user == null || user.isEmailVerified()
                    || tokenRepository.countByUserIdAndPurposeAndCreatedAtAfter(userId, Purpose.VERIFY_EMAIL,
                            now.minus(RESEND_GAP)) > 0) {
                return null;
            }
            return newLink(user, Purpose.VERIFY_EMAIL, Duration.ofHours(VERIFY_HOURS), "/verify-email", now);
        });
        if (link != null) {
            send(link.email(), "Verify your email for Budget Tracker", """
                    Welcome to Budget Tracker!

                    Confirm this is your email address so you can reset your password if you ever forget it:

                    %s

                    The link works for %d hours. If you didn't sign up, you can ignore this email.
                    """.formatted(link.url(), VERIFY_HOURS));
        }
    }

    /** The link from the verification email. Works whether or not the user is logged in. */
    public void verifyEmail(String token) {
        transaction.executeWithoutResult(status -> {
            Instant now = Instant.now();
            EmailToken emailToken = usableToken(token, Purpose.VERIFY_EMAIL, now);
            User user = userRepository.findById(emailToken.getUserId()).orElseThrow(() -> new BadRequestException(INVALID_LINK));
            user.markEmailVerified();
            tokenRepository.expireAll(user.getId(), Purpose.VERIFY_EMAIL, now);
        });
    }

    /**
     * "Forgot password". Always behaves the same from the outside, whether or not the email has an
     * account, so it can't be used to find out who uses the app.
     */
    public void requestPasswordReset(String email) {
        Link link = transaction.execute(status -> {
            User user = userRepository.findByEmail(AuthService.normalizeEmail(email)).orElse(null);
            Instant now = Instant.now();
            if (user == null || tokenRepository.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(),
                    Purpose.RESET_PASSWORD, now.minus(Duration.ofHours(1))) >= MAX_RESETS_PER_HOUR) {
                return null;
            }
            return newLink(user, Purpose.RESET_PASSWORD, Duration.ofMinutes(RESET_MINUTES), "/reset-password", now);
        });
        if (link != null) {
            send(link.email(), "Reset your Budget Tracker password", """
                    Someone (hopefully you) asked to reset the password for this Budget Tracker account.

                    Choose a new password here:

                    %s

                    The link works for %d minutes and only once. If you didn't ask, ignore this email:
                    your password stays the same.
                    """.formatted(link.url(), RESET_MINUTES));
        }
    }

    /**
     * Sets a new password from a reset link. It also counts as verifying the email (they opened a
     * link sent there).
     *
     * @return the account's email, so the caller can log out its other sessions
     */
    public String resetPassword(String token, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > AuthService.MAX_PASSWORD_BYTES) {
            throw new InvalidPasswordException("Password is too long");
        }
        return transaction.execute(status -> {
            Instant now = Instant.now();
            EmailToken emailToken = usableToken(token, Purpose.RESET_PASSWORD, now);
            User user = userRepository.findById(emailToken.getUserId()).orElseThrow(() -> new BadRequestException(INVALID_LINK));
            user.changePasswordHash(passwordEncoder.encode(newPassword));
            user.markEmailVerified();
            tokenRepository.expireAll(user.getId(), Purpose.RESET_PASSWORD, now);
            return user.getEmail();
        });
    }

    // --- helpers ---

    private Link newLink(User user, Purpose purpose, Duration validFor, String path, Instant now) {
        tokenRepository.expireAll(user.getId(), purpose, now); // only the newest link works
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(new EmailToken(user.getId(), purpose, hash(token), now, now.plus(validFor)));
        return new Link(user.getEmail(), baseUrl + path + "?token=" + token);
    }

    private EmailToken usableToken(String token, Purpose purpose, Instant now) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new BadRequestException(INVALID_LINK);
        }
        return tokenRepository.findByTokenHashAndPurpose(hash(token.trim()), purpose)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BadRequestException(INVALID_LINK));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /** Email trouble must not break sign-up or leak details: log it and carry on. */
    private void send(String to, String subject, String body) {
        try {
            emailSender.send(to, subject, body);
        } catch (RuntimeException e) {
            log.error("Could not send \"{}\" to {}: {}", subject, to, e.getMessage());
        }
    }
}
