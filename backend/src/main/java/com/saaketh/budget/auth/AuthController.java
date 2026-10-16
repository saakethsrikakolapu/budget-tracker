package com.saaketh.budget.auth;

import com.saaketh.budget.auth.AuthDtos.ChangePasswordRequest;
import com.saaketh.budget.auth.AuthDtos.DeleteAccountRequest;
import com.saaketh.budget.auth.AuthDtos.ForgotPasswordRequest;
import com.saaketh.budget.auth.AuthDtos.LoginRequest;
import com.saaketh.budget.auth.AuthDtos.RegisterRequest;
import com.saaketh.budget.auth.AuthDtos.RemindersRequest;
import com.saaketh.budget.auth.AuthDtos.ResetPasswordRequest;
import com.saaketh.budget.auth.AuthDtos.UserResponse;
import com.saaketh.budget.auth.AuthDtos.VerifyEmailRequest;
import com.saaketh.budget.common.NotFoundException;
import com.saaketh.budget.user.User;
import com.saaketh.budget.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Register, log in, and check who is logged in. Logout is handled by SecurityConfig. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    /** Spring Session's store (Postgres), when enabled; lets us find every session a user has. */
    private final ObjectProvider<FindByIndexNameSessionRepository<?>> sessionRepository;
    private final LoginRateLimiter loginRateLimiter;
    private final AccountEmailService accountEmailService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            ObjectProvider<FindByIndexNameSessionRepository<?>> sessionRepository,
            LoginRateLimiter loginRateLimiter, AccountEmailService accountEmailService,
            UserRepository userRepository) {
        this.loginRateLimiter = loginRateLimiter;
        this.accountEmailService = accountEmailService;
        this.userRepository = userRepository;
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionRepository = sessionRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        // After the sign-up transaction committed; email trouble never fails the sign-up.
        accountEmailService.sendVerification(user.getId());
        return response(user.getId());
    }

    /**
     * Checks the password, then stores the logged-in user in the session. Wrong email and wrong
     * password both throw BadCredentialsException, so the response never reveals which emails exist.
     */
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = AuthService.normalizeEmail(request.email());
        // Behind Render's proxy this is the visitor's IP (server.forward-headers-strategy=framework).
        String ip = httpRequest.getRemoteAddr();
        // Checked before the password, so a locked-out guesser learns nothing (even if right).
        loginRateLimiter.check(email, ip);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException e) {
            loginRateLimiter.recordFailure(email, ip);
            throw e;
        }
        loginRateLimiter.recordSuccess(email);

        // Session fixation defense: give the browser a new session id at login, so a session id
        // an attacker planted before login is useless afterwards.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return response(user.getId());
    }

    /**
     * Permanently deletes the account and all its data, then logs out everywhere: this browser and
     * any other device still logged in (otherwise those sessions would act as a user that no
     * longer exists).
     */
    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.deleteAccount(user.getId(), request.password());
        logOutEverywhere(user.getUsername(), httpRequest, httpResponse);
    }

    /** "Forgot password": 202 Accepted every time, so it doesn't reveal which emails have accounts. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountEmailService.requestPasswordReset(request.email());
    }

    /** New password from an emailed link. Every device is logged out; the user logs in again. */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = accountEmailService.resetPassword(request.token(), request.password());
        logOutEverywhere(email, httpRequest, httpResponse);
    }

    /** The link from the verification email (works logged in or out). */
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        accountEmailService.verifyEmail(request.token());
    }

    /**
     * New password while logged in. This browser stays logged in; every other device is logged
     * out, in case the password changed because someone else knew it.
     */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        authService.changePassword(user.getId(), request.currentPassword(), request.newPassword());
        HttpSession current = httpRequest.getSession(false);
        String keep = current == null ? null : current.getId();
        sessionRepository.ifAvailable(repository -> repository.findByPrincipalName(user.getUsername())
                .keySet()
                .stream()
                .filter(id -> !id.equals(keep))
                .forEach(repository::deleteById));
    }

    /** Monthly upload reminder on or off (Account page). Returns the updated user. */
    @PutMapping("/reminders")
    public UserResponse setReminders(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RemindersRequest request) {
        authService.setUploadReminders(user.getId(), request.enabled());
        return response(user.getId());
    }

    /** "Resend the email". Repeats within a minute are ignored. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@AuthenticationPrincipal AuthenticatedUser user) {
        accountEmailService.sendVerification(user.getId());
    }

    /**
     * Ends this browser's session and every other session of the account (all devices). Without
     * this, other devices would stay logged in after a password reset or account deletion.
     */
    private void logOutEverywhere(String email, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        sessionRepository.ifAvailable(repository -> repository.findByPrincipalName(email)
                .keySet()
                .forEach(repository::deleteById));
        SecurityContextHolder.clearContext();
        new CookieClearingLogoutHandler("SESSION", "JSESSIONID").logout(httpRequest, httpResponse, null);
    }

    /** Fresh from the database: the session's copy doesn't know about a later email verification. */
    private UserResponse response(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Account not found"));
        return new UserResponse(user.getId(), user.getEmail(), user.isEmailVerified(), user.isUploadReminders());
    }

    /** Who is logged in. Requires login, so anonymous requests get 401 before reaching here. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return response(user.getId());
    }
}
