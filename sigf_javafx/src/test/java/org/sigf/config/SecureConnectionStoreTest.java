package org.sigf.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs({OS.WINDOWS, OS.MAC})
class SecureConnectionStoreTest {
    @Test
    void protectsAndReloadsPasswordForCurrentUser() throws Exception {
        Preferences prefs = Preferences.userRoot().node("org/sigf/test/" + UUID.randomUUID());
        try {
            SecureConnectionStore store = new SecureConnectionStore(prefs);
            for (String password :
                    new String[] {"contraseña de prueba ", "deadbeef", " \"\\'ñ🙂 "}) {
                store.save(
                        "jdbc:postgresql://127.0.0.1:55439/sigf_javafx_db", "test_user", password);
                assertNotEquals(password, prefs.get("protected", ""));
                var saved = new SecureConnectionStore(prefs).load().orElseThrow();
                assertEquals(password, saved.password());
                assertEquals("test_user", saved.user());
            }
            store.clear();
            assertTrue(store.load().isEmpty());
        } finally {
            prefs.removeNode();
            prefs.flush();
        }
    }
}
