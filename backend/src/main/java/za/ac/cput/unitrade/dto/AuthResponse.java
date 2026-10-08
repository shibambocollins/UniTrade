package za.ac.cput.unitrade.dto;

/** Answer to register and login: the JWT the frontend sends as "Authorization: Bearer ..." plus the user. */
public record AuthResponse(String token, UserResponse user) {
}
