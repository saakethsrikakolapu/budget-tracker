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
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 254, message = "Email is too long") String email,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password) {

        /** Compact constructor: runs on creation, before validation. Strips copy-paste spaces. */
        public RegisterRequest {
            email = email == null ? null : email.trim();
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required") @Size(max = 254) String email,
            @NotBlank(message = "Password is required") @Size(max = 72) String password) {

        public LoginRequest {
            email = email == null ? null : email.trim();
        }
    }

    /** What the frontend gets back about a user. Never includes the password hash. */
    public record UserResponse(Long id, String email) {
    }
}
