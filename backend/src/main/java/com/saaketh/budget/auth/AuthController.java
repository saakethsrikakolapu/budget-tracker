package com.saaketh.budget.auth;

import com.saaketh.budget.auth.AuthDtos.DeleteAccountRequest;
import com.saaketh.budget.auth.AuthDtos.LoginRequest;
import com.saaketh.budget.auth.AuthDtos.RegisterRequest;
import com.saaketh.budget.auth.AuthDtos.UserResponse;
import com.saaketh.budget.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            ObjectProvider<FindByIndexNameSessionRepository<?>> sessionRepository) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionRepository = sessionRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return new UserResponse(user.getId(), user.getEmail());
    }

    /**
     * Checks the password, then stores the logged-in user in the session. Wrong email and wrong
     * password both throw BadCredentialsException, so the response never reveals which emails exist.
     */
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        AuthService.normalizeEmail(request.email()), request.password()));

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
        return new UserResponse(user.getId(), user.getUsername());
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

        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        sessionRepository.ifAvailable(repository -> repository.findByPrincipalName(user.getUsername())
                .keySet()
                .forEach(repository::deleteById));
        SecurityContextHolder.clearContext();
        new CookieClearingLogoutHandler("SESSION", "JSESSIONID").logout(httpRequest, httpResponse, null);
    }

    /** Who is logged in. Requires login, so anonymous requests get 401 before reaching here. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new UserResponse(user.getId(), user.getUsername());
    }
}
