package za.ac.cput.unitrade.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * FR1 rule: only university addresses may register. The email is trimmed and lower-cased first, then it must be
 * "something@" + the configured domain, matching the WHOLE domain: "x@evilmycput.ac.za" is rejected.
 * (This proves the address looks right, not that the person owns the mailbox. See limitation L6.)
 */
@Component
public class StudentEmailPolicy {

    private final String requiredSuffix;

    public StudentEmailPolicy(@Value("${app.auth.allowed-email-domain}") String allowedDomain) {
        this.requiredSuffix = "@" + allowedDomain.trim().toLowerCase(Locale.ROOT);
    }

    /** Trimmed, lower-case form used for storing and comparing emails. */
    public static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isAllowed(String email) {
        String normalised = normalise(email);
        // Exactly one "@" (so "a@b@mycput.ac.za" fails), a non-empty name before it, and the exact domain after it.
        int at = normalised.indexOf('@');
        return at > 0 && at == normalised.lastIndexOf('@') && normalised.endsWith(requiredSuffix);
    }

    public String requiredSuffix() {
        return requiredSuffix;
    }
}
