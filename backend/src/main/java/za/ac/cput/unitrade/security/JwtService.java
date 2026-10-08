package za.ac.cput.unitrade.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import za.ac.cput.unitrade.domain.User;

/**
 * Creates and checks login tokens (JWT = a signed string that says "this is user 7 until tomorrow").
 * Anyone can read a JWT, but only someone with the secret key can sign one, so the server can trust it.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration lifetime;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(keyBytes(secret));
        this.lifetime = Duration.ofMinutes(expirationMinutes);
    }

    /** Signs a token whose subject is the user's id. */
    public String createToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    /** The user's id and email if the token is genuine and not expired; empty otherwise (never throws). */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedUser(Long.parseLong(claims.getSubject()), claims.get("email", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            // bad signature, expired, malformed, wrong subject... all mean "not logged in"
            return Optional.empty();
        }
    }

    /**
     * Derives a fixed-length 256-bit key from the secret text (SHA-256), so any long string works as JWT_SECRET.
     * Without a secret (local development) a random key is generated.
     */
    private static byte[] keyBytes(String secret) {
        try {
            if (secret == null || secret.isBlank()) {
                log.warn("app.jwt.secret / JWT_SECRET is not set: using a random key. Logins stop working when the backend restarts.");
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                return random;
            }
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
