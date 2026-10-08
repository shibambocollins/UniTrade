package za.ac.cput.unitrade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/register. The @mycput.ac.za rule is checked in AuthService (the domain is configurable). */
public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters")
        String fullName,

        // Format and domain are checked in AuthService (after trimming), so no @Email here.
        @NotBlank(message = "Email is required")
        @Size(max = 254, message = "Email is too long")
        String email,

        // BCrypt only looks at the first 72 bytes, so longer passwords are refused instead of silently cut.
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String password) {
}
