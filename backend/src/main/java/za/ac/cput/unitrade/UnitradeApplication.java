package za.ac.cput.unitrade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

// UserDetailsServiceAutoConfiguration is excluded: it would create a throw-away "user" with a random password.
// We authenticate students ourselves (AuthService + JWT), so that default account must not exist.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class UnitradeApplication {

    /** Secrets that must be set as environment variables when running with the "prod" profile (Render). */
    static final List<String> REQUIRED_IN_PRODUCTION = List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET");

    public static void main(String[] args) {
        List<String> missing = missingProductionSettings(System.getenv());
        if (!missing.isEmpty()) {
            // Fail fast with one clear line instead of a long stack trace from the database pool
            System.err.println("UniTrade cannot start in production: missing environment variable(s) " + missing
                    + ". Set them in Render -> service -> Environment (see docs/DEPLOYMENT.md).");
            System.exit(1);
        }
        SpringApplication.run(UnitradeApplication.class, args);
    }

    /** Required production settings that are missing; always empty unless the "prod" profile is active. */
    static List<String> missingProductionSettings(Map<String, String> env) {
        List<String> profiles = Arrays.stream(env.getOrDefault("SPRING_PROFILES_ACTIVE", "").split(","))
                .map(String::trim)
                .toList();
        if (!profiles.contains("prod")) {
            return List.of();
        }
        return REQUIRED_IN_PRODUCTION.stream()
                .filter(key -> env.get(key) == null || env.get(key).isBlank())
                .toList();
    }
}
