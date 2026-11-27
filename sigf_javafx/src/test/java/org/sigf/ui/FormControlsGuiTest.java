package org.sigf.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SIGF_FORMS_GUI_TEST", matches = "true")
class FormControlsGuiTest {
    @Test
    void typedControlsReturnValues() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(
                () -> {
                    Platform.setImplicitExit(false);
                    try {
                        Platform.runLater(
                                () -> {
                                    try {
                                        var root =
                                                Window.getWindows().stream()
                                                        .filter(Window::isShowing)
                                                        .findFirst()
                                                        .orElseThrow()
                                                        .getScene()
                                                        .getRoot();
                                        @SuppressWarnings("unchecked")
                                        ComboBox<String> category =
                                                (ComboBox<String>) root.lookup("#formField0");
                                        assertTrue(category.getItems().contains("Analgésicos"));
                                        category.setValue("Analgésicos");
                                        ((DatePicker) root.lookup("#formField1"))
                                                .setValue(LocalDate.of(2030, 1, 2));
                                        ((CheckBox) root.lookup("#formField2")).setSelected(false);
                                        @SuppressWarnings("unchecked")
                                        ComboBox<String> role =
                                                (ComboBox<String>) root.lookup("#formField3");
                                        role.setValue("Administrador");
                                        @SuppressWarnings("unchecked")
                                        ComboBox<String> tax =
                                                (ComboBox<String>) root.lookup("#formField4");
                                        tax.setValue("Gravado (15%)");
                                        root.lookupAll(".button").stream()
                                                .filter(
                                                        n ->
                                                                n instanceof Button b
                                                                        && b.getText()
                                                                                .equals("Aceptar"))
                                                .map(n -> (Button) n)
                                                .findFirst()
                                                .orElseThrow()
                                                .fire();
                                    } catch (Throwable e) {
                                        failure.set(e);
                                        e.printStackTrace();
                                    }
                                });
                        var result =
                                FormControls.show(
                                        "Prueba",
                                        new String[] {
                                            "Categoría",
                                            "Vence AAAA-MM-DD",
                                            "Activo true/false",
                                            "Rol ADMIN o CASHIER",
                                            "Tasa IVA"
                                        },
                                        new String[] {"", "", "true", "CASHIER", ""},
                                        Set.of(),
                                        Map.of("Categoría", List.of("Analgésicos")));
                        assertEquals(
                                List.of("Analgésicos", "2030-01-02", "false", "ADMIN", "0.15"),
                                result.orElseThrow());
                        Platform.runLater(
                                () -> {
                                    var root =
                                            Window.getWindows().stream()
                                                    .filter(Window::isShowing)
                                                    .findFirst()
                                                    .orElseThrow()
                                                    .getScene()
                                                    .getRoot();
                                    root.lookupAll(".button").stream()
                                            .filter(
                                                    n ->
                                                            n instanceof Button b
                                                                    && b.getText()
                                                                            .equals("Aceptar"))
                                            .map(n -> (Button) n)
                                            .findFirst()
                                            .orElseThrow()
                                            .fire();
                                    Thread delayed =
                                            new Thread(
                                                    () -> {
                                                        try {
                                                            Thread.sleep(800);
                                                        } catch (InterruptedException interrupted) {
                                                            Thread.currentThread().interrupt();
                                                        }
                                                        Platform.runLater(
                                                                () -> {
                                                                    try {
                                                                        assertEquals(
                                                                                "Conservar",
                                                                                ((TextField)
                                                                                                root
                                                                                                        .lookup(
                                                                                                                "#formField0"))
                                                                                        .getText());
                                                                        assertTrue(
                                                                                root
                                                                                        .lookupAll(
                                                                                                ".label")
                                                                                        .stream()
                                                                                        .anyMatch(
                                                                                                n ->
                                                                                                        n
                                                                                                                        instanceof
                                                                                                                        Label
                                                                                                                                        label
                                                                                                                && label.getText()
                                                                                                                        .contains(
                                                                                                                                "Fallo simulado")));
                                                                    } catch (Throwable ex) {
                                                                        failure.set(ex);
                                                                    } finally {
                                                                        root
                                                                                .lookupAll(
                                                                                        ".button")
                                                                                .stream()
                                                                                .filter(
                                                                                        n ->
                                                                                                n
                                                                                                                instanceof
                                                                                                                Button
                                                                                                                                b
                                                                                                        && b
                                                                                                                .isCancelButton())
                                                                                .map(
                                                                                        n ->
                                                                                                (Button)
                                                                                                        n)
                                                                                .findFirst()
                                                                                .orElseThrow()
                                                                                .fire();
                                                                    }
                                                                });
                                                    });
                                    delayed.setDaemon(true);
                                    delayed.start();
                                });
                        assertTrue(
                                FormControls.save(
                                                "Persistencia",
                                                new String[] {"Nombre"},
                                                new String[] {"Conservar"},
                                                Set.of(),
                                                Map.of(),
                                                v -> {
                                                    throw new java.sql.SQLException(
                                                            "Fallo simulado");
                                                })
                                        .isEmpty());

                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        Platform.exit();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
