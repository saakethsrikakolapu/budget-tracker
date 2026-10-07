package com.saaketh.budget.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Who can access what, how passwords are checked, and how login state is kept.
 * Login state lives in a server-side session; the browser holds only an HttpOnly session cookie.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/auth/register", "/api/auth/login").permitAll()
                        // Spring's error responses are rendered at /error; let them through.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                // CSRF protection for a single-page app: the backend sets an XSRF-TOKEN cookie,
                // and the frontend must echo it back in an X-XSRF-TOKEN header on POST/PUT/DELETE.
                // Another website can't read our cookie, so it can't forge requests.
                .csrf(csrf -> csrf.spa())
                // Not logged in -> plain 401 (instead of redirecting to an HTML login page).
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // By default a denied request is saved in a new session so a login page can redirect
                // back to it. React handles navigation, so that only creates a useless session row
                // for every anonymous visit (including bots).
                .requestCache(cache -> cache.disable())
                // We have our own JSON login endpoint (AuthController), so turn off Spring's built-in forms.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        // SESSION is Spring Session's cookie; JSESSIONID is the servlet container's.
                        .deleteCookies("SESSION", "JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()));
        return http.build();
    }

    /**
     * The login cookie written by Spring Session ("SESSION"). Configured explicitly so its security
     * flags never depend on defaults:
     * HttpOnly (JavaScript can't read it), SameSite=Lax (not sent on cross-site POSTs, a second line
     * of CSRF defense), and Secure (HTTPS only) in production.
     */
    @Bean
    CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        return serializer;
    }

    /** BCrypt by default; the stored hash is prefixed with {bcrypt} so the algorithm can change later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Checks an email + password against the users table. */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** Saves the logged-in user into the HTTP session. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
