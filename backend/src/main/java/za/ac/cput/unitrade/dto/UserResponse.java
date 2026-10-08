package za.ac.cput.unitrade.dto;

import za.ac.cput.unitrade.domain.User;

import java.time.Instant;

/** What the API shows about a user. The password hash is never included. */
public record UserResponse(Long id, String fullName, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getCreatedAt());
    }
}
