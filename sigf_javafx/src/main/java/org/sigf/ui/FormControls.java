package org.sigf.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/** Formularios tipados compartidos para evitar escribir valores internos. */
final class FormControls {
    private FormControls() {}

    @FunctionalInterface
    interface Save {
        void accept(List<String> values) throws Exception;
    }

    static Optional<List<String>> show(
            String title, String[] labels, String[] values, Set<Integer> secret) {
        return show(title, labels, values, secret, Map.of());
    }

    static Optional<List<String>> show(
            String title,
            String[] labels,
            String[] values,
            Set<Integer> secret,
            Map<String, List<String>> choices) {
        return save(title, labels, values, secret, choices, null);
    }

    static Optional<List<String>> save(
            String title,
            String[] labels,
            String[] values,
            Set<Integer> secret,
            Map<String, List<String>> choices,
            Save work) {
        Dialog<List<String>> d = new Dialog<>();
        d.getDialogPane()
                .getStylesheets()
                .add(FormControls.class.getResource("/css/style.css").toExternalForm());
        d.setTitle(title);
        d.getDialogPane()
                .getButtonTypes()
                .addAll(org.sigf.ui.SpanishButtons.OK, org.sigf.ui.SpanishButtons.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(18));
        List<java.util.function.Supplier<String>> readers = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i], value = values[i];
            Node control;
            if (secret.contains(i)) {
                PasswordField f = new PasswordField();
                f.setText(value);
                control = f;
                readers.add(f::getText);
            } else if (label.toLowerCase(Locale.ROOT).contains("true/false")) {
                CheckBox c = new CheckBox("Activo");
                c.setSelected(Boolean.parseBoolean(value));
                control = c;
                readers.add(() -> Boolean.toString(c.isSelected()));
            } else if (label.equals("Rol") || label.contains("Rol ADMIN")) {
                ComboBox<String> c = new ComboBox<>();
                c.getItems()
                        .addAll(
                                org.sigf.security.Permissions.roles().stream()
                                        .map(org.sigf.security.Permissions::label)
                                        .toList());
                c.setValue(org.sigf.security.Permissions.label(value));
                control = c;
                readers.add(
                        () ->
                                org.sigf.security.Permissions.roles().stream()
                                        .filter(
                                                role ->
                                                        org.sigf.security.Permissions.label(role)
                                                                .equals(c.getValue()))
                                        .findFirst()
                                        .orElseThrow(
                                                () ->
                                                        new IllegalArgumentException(
                                                                "Seleccione un rol válido")));
            } else if (label.startsWith("Tasa IVA")) {
                ComboBox<String> c = new ComboBox<>();
                c.getItems().addAll("Sin clasificar", "Exento (0%)", "Gravado (15%)");
                c.setEditable(true);
                String tax = value;
                try {
                    if (!value.isBlank()) {
                        var rate = new java.math.BigDecimal(value);
                        if (rate.compareTo(java.math.BigDecimal.ZERO) == 0) {
                            tax = "Exento (0%)";
                        } else if (rate.compareTo(new java.math.BigDecimal("0.15")) == 0) {
                            tax = "Gravado (15%)";
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
                c.setValue(value.isBlank() ? "Sin clasificar" : tax);
                control = c;
                readers.add(
                        () -> {
                            String v = c.getEditor().getText().trim();
                            return v.equals("Sin clasificar")
                                    ? ""
                                    : v.equals("Exento (0%)")
                                            ? "0"
                                            : v.equals("Gravado (15%)") ? "0.15" : v;
                        });
            } else if (label.startsWith("Vence")) {
                DatePicker c = new DatePicker();
                Views.date(c);
                if (!value.isBlank()) {
                    try {
                        c.setValue(java.time.LocalDate.parse(value));
                    } catch (Exception ignored) {
                    }
                }
                control = c;
                readers.add(
                        () -> {
                            c.commitValue();
                            return c.getValue() == null ? "" : c.getValue().toString();
                        });
            } else if (label.equals("Cantidad") || label.equals("Existencia mínima")) {
                int initial;
                try {
                    initial = Integer.parseInt(value);
                } catch (Exception ex) {
                    initial = label.equals("Cantidad") ? 1 : 0;
                }
                int minimum = label.equals("Cantidad") ? 1 : 0;
                Spinner<Integer> c = new Spinner<>(minimum, 100000000, Math.max(minimum, initial));
                c.setEditable(true);
                control = c;
                readers.add(() -> c.getEditor().getText().trim());
            } else if (label.equals("Categoría")
                    || label.equals("Presentación")
                    || label.equals("Laboratorio")) {
                List<String> options = new ArrayList<>(choices.getOrDefault(label, List.of()));
                if (label.equals("Presentación")) {
                    options.addAll(
                            List.of(
                                    "Tabletas",
                                    "Cápsulas",
                                    "Jarabe",
                                    "Crema",
                                    "Solución",
                                    "Inyectable"));
                }
                ComboBox<String> c = SearchableChoices.create(options, value);
                control = c;
                readers.add(() -> c.getEditor().getText());
            } else {
                TextField f = new TextField(value);
                control = f;
                readers.add(f::getText);
            }
            control.setId("formField" + i);
            if (control instanceof javafx.scene.layout.Region r) {
                r.setPrefWidth(300);
            }
            grid.addRow(
                    i,
                    new Label(
                            label.replace(" true/false", "")
                                    .replace(" (true/false)", "")
                                    .replace("Rol ADMIN o CASHIER", "Rol")),
                    control);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(600, labels.length * 52 + 35));
        d.getDialogPane().setContent(scroll);
        Label error = new Label();
        error.setWrapText(true);
        error.getStyleClass().add("warning");
        d.getDialogPane().setContent(new javafx.scene.layout.VBox(10, scroll, error));
        Button ok = (Button) d.getDialogPane().lookupButton(org.sigf.ui.SpanishButtons.OK);
        ok.setGraphic(UiIcons.icon(work == null ? "check" : "save", "action-icon", 20));
        ((Button) d.getDialogPane().lookupButton(org.sigf.ui.SpanishButtons.CANCEL))
                .setGraphic(UiIcons.icon("close", "action-icon", 20));
        d.getDialogPane()
                .addEventFilter(
                        javafx.scene.input.KeyEvent.KEY_PRESSED,
                        e -> {
                            if (ok.isDisabled()
                                    && e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                                e.consume();
                            }
                        });
        d.setOnCloseRequest(
                e -> {
                    if (ok.isDisabled()) {
                        e.consume();
                    }
                });
        ok.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    List<String> result;
                    try {
                        result = readers.stream().map(java.util.function.Supplier::get).toList();
                        validate(labels, result);
                    } catch (Exception ex) {
                        event.consume();
                        error.setText(Objects.toString(ex.getMessage(), "Revise los datos"));
                        return;
                    }
                    if (work == null) {
                        return;
                    }
                    event.consume();
                    ok.setDisable(true);
                    d.getDialogPane()
                            .lookupButton(org.sigf.ui.SpanishButtons.CANCEL)
                            .setDisable(true);
                    error.setText("Guardando…");
                    javafx.concurrent.Task<Void> task =
                            new javafx.concurrent.Task<>() {
                                protected Void call() throws Exception {
                                    work.accept(result);
                                    return null;
                                }
                            };
                    task.setOnSucceeded(
                            e -> {
                                ok.setDisable(false);
                                d.setResult(result);
                                d.close();
                            });
                    task.setOnFailed(
                            e -> {
                                ok.setDisable(false);
                                d.getDialogPane()
                                        .lookupButton(org.sigf.ui.SpanishButtons.CANCEL)
                                        .setDisable(false);
                                error.setText(
                                        Objects.toString(
                                                task.getException().getMessage(),
                                                "No se pudo guardar"));
                            });
                    Thread thread = new Thread(task, "sigf-form-save");
                    thread.setDaemon(true);
                    thread.start();
                });
        d.setResultConverter(
                b ->
                        b == org.sigf.ui.SpanishButtons.OK
                                ? readers.stream().map(java.util.function.Supplier::get).toList()
                                : null);
        return d.showAndWait();
    }

    private static void validate(String[] labels, List<String> values) {
        for (int i = 0; i < labels.length; i++) {
            String l = labels[i], v = values.get(i).trim();
            if (Set.of("Código", "Nombre", "Usuario", "Motivo", "Lote", "Serie").contains(l)
                    && v.isEmpty()) {
                throw new IllegalArgumentException("Complete " + l);
            }
            if (l.startsWith("Vence")
                    && (v.isEmpty()
                            || !java.time.LocalDate.parse(v).isAfter(java.time.LocalDate.now()))) {
                throw new IllegalArgumentException("El vencimiento debe ser futuro");
            }
            if (l.equals("Cantidad") || l.equals("Existencia mínima")) {
                int n = Integer.parseInt(v);
                if (n < (l.equals("Cantidad") ? 1 : 0)) {
                    throw new IllegalArgumentException("Cantidad inválida");
                }
            }
            if (l.equals("Costo") || l.equals("Costo unitario") || l.equals("Precio sin IVA")) {
                java.math.BigDecimal n = new java.math.BigDecimal(v);
                if (n.signum() < 0) {
                    throw new IllegalArgumentException("Importe negativo");
                }
                n.setScale(2, java.math.RoundingMode.UNNECESSARY);
            }
            if (l.startsWith("Tasa IVA")) {
                java.math.BigDecimal n = new java.math.BigDecimal(v);
                if (n.signum() < 0 || n.compareTo(java.math.BigDecimal.ONE) > 0) {
                    throw new IllegalArgumentException("Seleccione el IVA");
                }
            }
        }
    }
}
