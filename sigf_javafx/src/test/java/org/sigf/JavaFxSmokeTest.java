package org.sigf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.ui.Workspace;

@EnabledIfEnvironmentVariable(named = "SIGF_GUI_TEST", matches = "true")
class JavaFxSmokeTest {
    @Test
    void groupedNavigationOpensEachModule() throws Exception {
        try (Database db = new Database()) {
            Security security = new Security(db);
            if (!security.hasUsers()) {
                security.bootstrap("admin_gui_test", "contraseñaPrueba123");
            }
            Security.User user = security.login("admin_gui_test", "contraseñaPrueba123");
            CountDownLatch ready = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicReference<Stage> stageRef = new AtomicReference<>();
            AtomicReference<TabPane> navRef = new AtomicReference<>();
            Platform.startup(
                    () -> {
                        try {
                            Stage stage = new Stage();
                            stage.setScene(new Scene(new Pane(), 1150, 760));
                            stage.getScene()
                                    .getStylesheets()
                                    .add(
                                            org.sigf.app.Main.class
                                                    .getResource("/css/style.css")
                                                    .toExternalForm());
                            stage.show();
                            new Workspace(stage, db, user).show();
                            stageRef.set(stage);
                            navRef.set(
                                    (TabPane)
                                            ((BorderPane) stage.getScene().getRoot()).getCenter());
                            assertEquals(7, navRef.get().getTabs().size());
                        } catch (Throwable e) {
                            failure.set(e);
                        } finally {
                            ready.countDown();
                        }
                    });
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            if (failure.get() != null) {
                throw new AssertionError(failure.get());
            }
            Thread.sleep(1500);
            if ("true".equals(System.getenv("SIGF_GUI_SCREENSHOT"))) {
                CountDownLatch snap = new CountDownLatch(1);
                Platform.runLater(
                        () -> {
                            try {
                                var image = stageRef.get().getScene().snapshot(null);
                                int width = (int) image.getWidth(),
                                        height = (int) image.getHeight();
                                var out =
                                        new java.awt.image.BufferedImage(
                                                width,
                                                height,
                                                java.awt.image.BufferedImage.TYPE_INT_ARGB);
                                var pixels = image.getPixelReader();
                                for (int y = 0; y < height; y++) {
                                    for (int x = 0; x < width; x++) {
                                        out.setRGB(x, y, pixels.getArgb(x, y));
                                    }
                                }
                                javax.imageio.ImageIO.write(
                                        out,
                                        "png",
                                        new java.io.File("target-v031/dashboard-check.png"));
                            } catch (Exception ex) {
                                failure.set(ex);
                            } finally {
                                snap.countDown();
                            }
                        });
                assertTrue(snap.await(10, TimeUnit.SECONDS));
                if (failure.get() != null) {
                    throw new AssertionError(failure.get());
                }
            }
            for (String name :
                    new String[] {
                        "Ventas", "Inventario", "Compras", "Contactos", "Reportes", "Administración"
                    }) {
                CountDownLatch done = new CountDownLatch(1);
                Platform.runLater(
                        () -> {
                            try {
                                var group =
                                        navRef.get().getTabs().stream()
                                                .filter(t -> name.equals(t.getText()))
                                                .findFirst()
                                                .orElseThrow();
                                navRef.get().getSelectionModel().select(group);
                                TabPane nested = (TabPane) group.getContent();
                                for (var tab : nested.getTabs()) {
                                    nested.getSelectionModel().select(tab);
                                    assertNotNull(tab.getContent(), name + " / " + tab.getText());
                                }
                            } catch (Throwable t) {
                                failure.set(t);
                            } finally {
                                done.countDown();
                            }
                        });
                assertTrue(done.await(10, TimeUnit.SECONDS));
                if (failure.get() != null) {
                    throw new AssertionError(failure.get());
                }
            }
            CountDownLatch closed = new CountDownLatch(1);
            Platform.runLater(
                    () -> {
                        stageRef.get().close();
                        closed.countDown();
                    });
            assertTrue(closed.await(10, TimeUnit.SECONDS));
            Platform.exit();
        }
    }
}
