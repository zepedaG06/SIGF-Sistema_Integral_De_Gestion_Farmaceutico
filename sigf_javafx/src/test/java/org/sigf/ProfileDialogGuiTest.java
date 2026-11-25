package org.sigf;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.security.Security;
import org.sigf.services.PharmacyProfileService;
import org.sigf.ui.ProfileDialog;

@EnabledIfEnvironmentVariable(named = "SIGF_SETUP_GUI_TEST", matches = "true")
class ProfileDialogGuiTest {
    @Test
    void closeAndEscapeKeepSetupOpenUntilConfirmed() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean();
        Platform.startup(
                () -> {
                    try {
                        Stage owner = new Stage();
                        owner.setScene(new Scene(new Pane(), 900, 650));
                        owner.show();
                        Method show =
                                ProfileDialog.class.getDeclaredMethod(
                                        "show",
                                        javafx.stage.Window.class,
                                        PharmacyProfileService.class,
                                        Security.User.class,
                                        Optional.class,
                                        boolean.class,
                                        Runnable.class);
                        show.setAccessible(true);
                        show.invoke(
                                null,
                                owner,
                                null,
                                null,
                                Optional.empty(),
                                true,
                                (Runnable) () -> finished.set(true));
                        Stage dialog =
                                (Stage)
                                        javafx.stage.Window.getWindows().stream()
                                                .filter(w -> w != owner && w.isShowing())
                                                .findFirst()
                                                .orElseThrow();
                        assertNotNull(dialog.getScene().lookup("#profileLegal"));
                        Platform.runLater(ProfileDialogGuiTest::keepOpen);
                        Event.fireEvent(
                                dialog, new WindowEvent(dialog, WindowEvent.WINDOW_CLOSE_REQUEST));
                        assertTrue(dialog.isShowing());
                        assertFalse(finished.get());
                        Platform.runLater(ProfileDialogGuiTest::keepOpen);
                        Event.fireEvent(
                                dialog.getScene(),
                                new KeyEvent(
                                        KeyEvent.KEY_PRESSED,
                                        "",
                                        "",
                                        KeyCode.ESCAPE,
                                        false,
                                        false,
                                        false,
                                        false));
                        assertTrue(dialog.isShowing());
                        assertFalse(finished.get());
                        ((Button) dialog.getScene().lookup("#profileSave")).fire();
                        assertFalse(
                                ((Label) dialog.getScene().lookup("#profileStatus"))
                                        .getText()
                                        .isBlank());
                        assertTrue(dialog.isShowing());
                        assertFalse(finished.get());
                        dialog.hide();
                        owner.hide();
                    } catch (Throwable t) {
                        error.set(t);
                    } finally {
                        done.countDown();
                    }
                });
        assertTrue(done.await(20, TimeUnit.SECONDS));
        Platform.exit();
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    private static void keepOpen() {
        javafx.stage.Window.getWindows().stream()
                .filter(w -> w.isShowing() && w.getScene() != null)
                .flatMap(w -> w.getScene().getRoot().lookupAll(".button").stream())
                .filter(n -> n instanceof Button b && "Continuar configurando".equals(b.getText()))
                .map(n -> (Button) n)
                .findFirst()
                .orElseThrow()
                .fire();
    }
}
