package org.sigf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.sigf.security.Security;

class PasswordPolicyTest {
    @Test
    void rejectsPasswordsThatBcryptWouldTruncate() {
        assertDoesNotThrow(() -> Security.validateNewPassword("a".repeat(72)));
        assertThrows(
                IllegalArgumentException.class, () -> Security.validateNewPassword("a".repeat(73)));
        assertThrows(
                IllegalArgumentException.class, () -> Security.validateNewPassword("ñ".repeat(37)));
        assertDoesNotThrow(() -> Security.validateNewPassword("ñ".repeat(24)));
        assertThrows(IllegalArgumentException.class, () -> Security.validateNewPassword("corta"));
    }
}
