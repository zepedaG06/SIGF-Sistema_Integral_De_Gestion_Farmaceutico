package org.sigf.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.sigf.app.Main;

@EnabledOnOs(OS.WINDOWS)
@EnabledIfEnvironmentVariable(named = "SIGF_AUTOCONNECT_GUI_TEST", matches = "true")
class AutoConnectGuiTest {
    @Test
    void connectsFromSecureStoreAtStartup() throws Exception {
        Preferences prefs = Preferences.userRoot().node("org/sigf/test/" + UUID.randomUUID());
        SecureConnectionStore store = new SecureConnectionStore(prefs);
        try {
            store.save("jdbc:postgresql://127.0.0.1:55439/sigf_javafx_db", "sigftest", "test-only");
            CountDownLatch started = new CountDownLatch(1);
            AtomicReference<Stage> stage = new AtomicReference<>();
            AtomicReference<Main> app = new AtomicReference<>();
            Platform.startup(
                    () -> {
                        Main main =
                                new Main() {
                                    @Override
                                    protected SecureConnectionStore connectionStore() {
                                        return store;
                                    }
                                };
                        Stage window = new Stage();
                        main.start(window);
                        stage.set(window);
                        app.set(main);
                        started.countDown();
                    });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            AtomicBoolean login = new AtomicBoolean();
            for (int i = 0; i < 120 && !login.get(); i++) {
                Thread.sleep(100);
                CountDownLatch done = new CountDownLatch(1);
                Platform.runLater(
                        () -> {
                            login.set(stage.get().getScene().lookup("#login") instanceof Button);
                            done.countDown();
                        });
                assertTrue(done.await(5, TimeUnit.SECONDS));
            }
            assertTrue(login.get(), "Debe abrir el login usando la conexión guardada");
            CountDownLatch closed = new CountDownLatch(1);
            Platform.runLater(
                    () -> {
                        stage.get().close();
                        app.get().stop();
                        closed.countDown();
                    });
            assertTrue(closed.await(5, TimeUnit.SECONDS));
            Platform.exit();
        } finally {
            store.clear();
            prefs.removeNode();
        }
    }
}
