package za.ac.cput.unitrade;

import org.junit.jupiter.api.Test;
import za.ac.cput.unitrade.service.StudentEmailPolicy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR1: the university-email rule in isolation (plain unit test, no Spring). */
class Fr1EmailPolicyTest {

    private final StudentEmailPolicy policy = new StudentEmailPolicy("mycput.ac.za");

    @Test
    void fr1_12_acceptsStudentAddressesIgnoringCaseAndSpaces() {
        assertTrue(policy.isAllowed("230093183@mycput.ac.za"));
        assertTrue(policy.isAllowed("  Jane.Dlamini@MyCPUT.AC.ZA  "));
    }

    @Test
    void fr1_13_rejectsEverythingElse() {
        assertFalse(policy.isAllowed(null));
        assertFalse(policy.isAllowed(""));
        assertFalse(policy.isAllowed("mycput.ac.za"));          // no @
        assertFalse(policy.isAllowed("@mycput.ac.za"));         // nothing before the @
        assertFalse(policy.isAllowed("jane@gmail.com"));
        assertFalse(policy.isAllowed("jane@cput.ac.za"));       // the old charter domain
        assertFalse(policy.isAllowed("x@evilmycput.ac.za"));    // domain only ends the same way
        assertFalse(policy.isAllowed("jane@mail.mycput.ac.za")); // sub-domain is not the configured domain
        assertFalse(policy.isAllowed("a@b@mycput.ac.za"));      // two @ signs
    }
}
