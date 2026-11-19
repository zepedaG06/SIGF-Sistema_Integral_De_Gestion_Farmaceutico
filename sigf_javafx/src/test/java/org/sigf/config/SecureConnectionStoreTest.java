package org.sigf.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.util.UUID;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class SecureConnectionStoreTest {
 @Test void protectsAndReloadsPasswordForCurrentUser() throws Exception {
  Preferences prefs=Preferences.userRoot().node("org/sigf/test/"+UUID.randomUUID());
  try {SecureConnectionStore store=new SecureConnectionStore(prefs);String password="contraseña de prueba ";store.save("jdbc:postgresql://127.0.0.1:55439/sigf_javafx_db","test_user",password);assertNotEquals(password,prefs.get("protected",""));var saved=new SecureConnectionStore(prefs).load().orElseThrow();assertEquals(password,saved.password());assertEquals("test_user",saved.user());store.clear();assertTrue(store.load().isEmpty());}finally{prefs.removeNode();prefs.flush();}
 }
}
