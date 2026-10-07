package com.saaketh.budget.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies for /api/auth endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * Password length: at least 8 (NIST guidance), at most 72 because BCrypt ignores
     * everything past 72 bytes. AuthService also checks the byte length for non-ASCII input.
     */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {

        /** Compact constructor: runs on creation, before validation. Strips copy-paste spaces. */
        public RegisterRequest {
            email = email == null ? null : email.trim();
        }
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {

        public LoginRequest {
            email = email == null ? null : email.trim();
        }
    }

    /** What the frontend gets back about a user. Never includes the password hash. */
    public record UserResponse(Long id, String email) {
    }
}
