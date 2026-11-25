package org.sigf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.app.Main;

@EnabledIfEnvironmentVariable(named = "SIGF_STARTUP_GUI_TEST", matches = "true")
class StartupFlowTest {
    @Test
    void connectionFormOpensLoginWithDisposableDatabase() throws Exception {
        CountDownLatch initial = new CountDownLatch(1);
        AtomicReference<Stage> ref = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.startup(
                () -> {
                    try {
                        Stage stage = new Stage();
                        new Main() {
                            @Override
                            protected org.sigf.config.SecureConnectionStore connectionStore() {
                                return new org.sigf.config.SecureConnectionStore(
                                        java.util.prefs.Preferences.userRoot()
                                                .node("org/sigf/test/startup"));
                            }
                        }.start(stage);
                        ref.set(stage);
                    } catch (Throwable e) {
                        error.set(e);
                    } finally {
                        initial.countDown();
                    }
                });
        assertTrue(initial.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        boolean connectReady = false;
        for (int i = 0; i < 100 && !connectReady; i++) {
            Thread.sleep(100);
            CountDownLatch wait = new CountDownLatch(1);
            AtomicBoolean ready = new AtomicBoolean();
            Platform.runLater(
                    () -> {
                        ready.set(ref.get().getScene().lookup("#connectButton") != null);
                        wait.countDown();
                    });
            assertTrue(wait.await(5, TimeUnit.SECONDS));
            connectReady = ready.get();
        }
        assertTrue(connectReady);
        CountDownLatch send = new CountDownLatch(1);
        Platform.runLater(
                () -> {
                    ((TextField) ref.get().getScene().lookup("#dbPort")).setText("55439");
                    ((TextField) ref.get().getScene().lookup("#dbUser")).setText("sigftest");
                    ((PasswordField) ref.get().getScene().lookup("#dbPassword"))
                            .setText("test-only");
                    ((Button) ref.get().getScene().lookup("#connectButton")).fire();
                    send.countDown();
                });
        assertTrue(send.await(5, TimeUnit.SECONDS));
        boolean found = false;
        for (int i = 0; i < 120 && !found; i++) {
            Thread.sleep(100);
            CountDownLatch check = new CountDownLatch(1);
            AtomicBoolean ready = new AtomicBoolean();
            Platform.runLater(
                    () -> {
                        ready.set(ref.get().getScene().lookup("#login") != null);
                        check.countDown();
                    });
            assertTrue(check.await(5, TimeUnit.SECONDS));
            found = ready.get();
        }
        assertTrue(found, "La pantalla de acceso no apareció");
        CountDownLatch closed = new CountDownLatch(1);
        Platform.runLater(
                () -> {
                    ref.get().close();
                    closed.countDown();
                });
        assertTrue(closed.await(5, TimeUnit.SECONDS));
        Platform.exit();
    }
}
