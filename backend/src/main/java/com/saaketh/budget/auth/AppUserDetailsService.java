package com.saaketh.budget.auth;

import com.saaketh.budget.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Tells Spring Security how to load a user by email when someone logs in. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(AuthService.normalizeEmail(email))
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with that email"));
    }
}
