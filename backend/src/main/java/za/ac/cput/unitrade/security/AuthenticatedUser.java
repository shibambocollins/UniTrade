package za.ac.cput.unitrade.security;

/**
 * Who is making the current request, taken from a valid token. Controllers receive it with
 * {@code @AuthenticationPrincipal AuthenticatedUser user} and use {@code user.id()} (e.g. to check listing ownership).
 */
public record AuthenticatedUser(Long id, String email) {
}
