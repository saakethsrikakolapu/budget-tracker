package com.saaketh.budget.auth;

import com.saaketh.budget.auth.AuthDtos.RegisterRequest;
import com.saaketh.budget.user.User;
import com.saaketh.budget.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business logic for creating accounts. Login itself is handled by Spring Security. */
@Service
public class AuthService {

    /** BCrypt only uses the first 72 bytes of a password; longer ones would be silently truncated. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        // @Size counts characters; emoji and accented letters take several bytes each.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidPasswordException("Password is too long");
        }
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            // saveAndFlush sends the INSERT now, so a unique-constraint violation surfaces here.
            return userRepository.saveAndFlush(new User(email, passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the database's unique
            // constraint lets only one through.
            throw new EmailAlreadyRegisteredException();
        }
    }

    /** "  Saaketh@Gmail.com " and "saaketh@gmail.com" are the same account. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
