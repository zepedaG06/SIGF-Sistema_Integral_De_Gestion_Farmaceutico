package org.sigf.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.AdministrationService;
import org.sigf.services.BackupService;
import org.sigf.services.ConnectionSettingsService;
import org.sigf.services.DesktopData;
import org.sigf.services.PharmacyProfileService;

public final class SettingsPane extends VBox {
    private final Database db;
    private final Security.User user;
    private final DesktopData data;
    private final AdministrationService admin;
    private final Runnable identityChanged;
    private final VBox body = new VBox(16);
    private final Label status = new Label();
    private final Preferences prefs = Preferences.userRoot().node("org/sigf/javafx/desktop");
    private Map<String, String> settings = Map.of();
    private String selected = "Farmacia";
    private int version;
    private boolean editing;
    private final Map<String, Button> sectionButtons = new HashMap<>();

    public SettingsPane(Database db, Security.User user, Runnable changed) {
        this.db = db;
        this.user = user;
        this.identityChanged = changed;
        data = new DesktopData(db);
        admin = new AdministrationService(db);
        getStyleClass().add("workspace");
        setSpacing(16);
        getChildren()
                .add(
                        Views.headerWithIcon(
                                "Configuración de la farmacia",
                                "Información, operación, documentos y respaldo",
                                "Administración"));
        VBox sections = new VBox(8);
        sections.getStyleClass().add("work-card");
        sections.setMinWidth(190);
        for (String name :
                List.of("Farmacia", "Operación", "Documentos internos", "Respaldos", "Conexión")) {
            String icon =
                    switch (name) {
                        case "Farmacia" -> "shop";
                        case "Operación" -> "sliders";
                        case "Documentos internos" -> "receipt";
                        case "Respaldos" -> "backup";
                        default -> "database";
                    };
            Button b =
                    Views.button(
                            name,
                            icon,
                            () -> {
                                if (editing
                                        && !Views.confirm(
                                                "¿Descartar los cambios de configuración sin guardar?")) {
                                    return;
                                }
                                editing = false;
                                selected = name;
                                show();
                            },
                            false);
            sectionButtons.put(name, b);
            b.setMaxWidth(Double.MAX_VALUE);
            sections.getChildren().add(b);
        }
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        HBox main = new HBox(18, sections, scroll);
        HBox.setHgrow(scroll, Priority.ALWAYS);
        VBox.setVgrow(main, Priority.ALWAYS);
        getChildren().addAll(main, status);
        reload();
    }

    public boolean hasDraft() {
        return editing;
    }

    public void reload() {
        if (editing) {
            return;
        }
        int ticket = ++version;
        Views.async(
                data::settings,
                values -> {
                    if (ticket != version) {
                        return;
                    }
                    settings = values;
                    show();
                },
                ex -> status.setText("No se pudo cargar la configuración: " + ex.getMessage()));
    }

    private void show() {
        sectionButtons.forEach(
                (name, b) -> {
                    b.getStyleClass().remove("selected-section");
                    if (name.equals(selected)) {
                        b.getStyleClass().add("selected-section");
                    }
                });
        body.getChildren().clear();
        switch (selected) {
            case "Farmacia" -> farm();
            case "Operación" -> operation();
            case "Documentos internos" -> documents();
            case "Respaldos" -> backups();
            case "Conexión" -> connection();
        }
    }

    private void farm() {
        body.getChildren()
                .add(
                        Views.cardWithIcon(
                                "Identidad de la farmacia",
                                "shop",
                                Views.label(
                                        "Nombre comercial, titular, RUC, dirección y logo",
                                        "muted"),
                                Views.button(
                                        "Editar datos de la farmacia",
                                        "edit",
                                        () ->
                                                ProfileDialog.open(
                                                        getScene().getWindow(),
                                                        db,
                                                        user,
                                                        () -> {
                                                            identityChanged.run();
                                                            reload();
                                                        }),
                                        true)));
        int ticket = version;
        Views.async(
                () -> new PharmacyProfileService(db).load(),
                profile -> {
                    if (ticket != version || !selected.equals("Farmacia")) {
                        return;
                    }
                    profile.ifPresent(
                            p ->
                                    body.getChildren()
                                            .add(
                                                    Views.card(
                                                            p.name(),
                                                            new Label("Titular: " + p.legalName()),
                                                            new Label("RUC: " + p.ruc()),
                                                            Views.label(p.address(), "muted"),
                                                            new Label("Teléfono: " + p.phone()),
                                                            new Label("Correo: " + p.email()))));
                },
                Views::error);
    }

    private void operation() {
        Spinner<Integer> days =
                new Spinner<>(
                        0,
                        3650,
                        Math.max(
                                0,
                                Math.min(
                                        3650,
                                        Integer.parseInt(
                                                settings.getOrDefault("expiry_days", "30")))));
        days.setEditable(true);
        ComboBox<String> size = new ComboBox<>();
        size.getItems().addAll("Estándar", "Grande");
        size.setValue(prefs.getBoolean("largeText", false) ? "Grande" : "Estándar");
        days.getEditor().textProperty().addListener((o, a, b) -> editing = true);
        size.valueProperty().addListener((o, a, b) -> editing = true);
        Label description =
                Views.label(
                        "Avisar cuando un lote venza dentro del número de días indicado. La moneda de operación es NIO.",
                        "muted");
        Button save =
                Views.button(
                        "Guardar cambios",
                        "save",
                        () -> {
                            try {
                                int n = Integer.parseInt(days.getEditor().getText());
                                if (n < 0 || n > 3650) {
                                    throw new IllegalArgumentException("Usa entre 0 y 3650 días");
                                }
                                boolean large = size.getValue().equals("Grande");
                                Views.async(
                                        () -> {
                                            admin.setting(user, "expiry_days", Integer.toString(n));
                                            return null;
                                        },
                                        x -> {
                                            prefs.putBoolean("largeText", large);
                                            applyTextSize(getScene());
                                            editing = false;
                                            status.setText("Parámetros guardados");
                                            reload();
                                        },
                                        Views::error);
                            } catch (Exception ex) {
                                Views.error(ex);
                            }
                        },
                        true);
        body.getChildren()
                .add(
                        Views.cardWithIcon(
                                "Parámetros de operación",
                                "sliders",
                                Views.label("Córdobas — NIO (C$)", "sale-total"),
                                description,
                                new Label("Alertas de vencimiento (días)"),
                                days,
                                new Label("Tamaño de texto"),
                                size,
                                save));
    }

    public static void applyTextSize(javafx.scene.Scene scene) {
        if (scene != null) {
            scene.getRoot()
                    .setStyle(
                            "-fx-font-size: "
                                    + (Preferences.userRoot()
                                                    .node("org/sigf/javafx/desktop")
                                                    .getBoolean("largeText", false)
                                            ? "16px"
                                            : "14px")
                                    + ";");
        }
    }

    private void documents() {
        TextField series = new TextField(settings.getOrDefault("series", "DEMO")),
                next = new TextField(settings.getOrDefault("next_number", "1"));
        series.textProperty().addListener((o, a, b) -> editing = true);
        next.textProperty().addListener((o, a, b) -> editing = true);
        Button save =
                Views.button(
                        "Guardar serie interna",
                        "save",
                        () -> {
                            try {
                                long n = Long.parseLong(next.getText());
                                if (!Views.confirm(
                                        "Cambiar la serie interna y su próximo número. No se modificarán documentos anteriores.")) {
                                    return;
                                }
                                Views.async(
                                        () -> {
                                            admin.internalSeries(user, series.getText().trim(), n);
                                            return null;
                                        },
                                        x -> {
                                            editing = false;
                                            status.setText("Serie actualizada");
                                            reload();
                                        },
                                        Views::error);
                            } catch (Exception ex) {
                                Views.error(ex);
                            }
                        },
                        true);
        body.getChildren()
                .add(
                        Views.cardWithIcon(
                                "Documentos internos",
                                "receipt",
                                Views.label(
                                        "Comprobantes internos sin valor fiscal. La configuración no constituye autorización de facturación.",
                                        "warning"),
                                new Label("Serie actual"),
                                series,
                                new Label("Próximo número"),
                                next,
                                save));
    }

    private void backups() {
        TextField binary = new TextField(prefs.get("pgDump", ""));
        binary.setEditable(false);
        Button choose =
                Views.button(
                        "Seleccionar pg_dump",
                        "file",
                        () -> {
                            FileChooser f = new FileChooser();
                            f.setTitle("Selecciona pg_dump de PostgreSQL");
                            var file = f.showOpenDialog(getScene().getWindow());
                            if (file != null) {
                                prefs.put("pgDump", file.getAbsolutePath());
                                binary.setText(file.getAbsolutePath());
                            }
                        },
                        false);
        Button create =
                Views.button(
                        "Crear respaldo",
                        "backup",
                        () -> {
                            try {
                                Path executable = Path.of(binary.getText());
                                if (!Files.isRegularFile(executable)) {
                                    throw new IllegalArgumentException(
                                            "Selecciona primero pg_dump");
                                }
                                FileChooser f = new FileChooser();
                                f.setTitle("Guardar respaldo");
                                f.setInitialFileName("sigf-" + LocalDate.now() + ".dump");
                                var file = f.showSaveDialog(getScene().getWindow());
                                if (file == null) {
                                    return;
                                }
                                status.setText("Generando respaldo…");
                                Views.async(
                                        () -> {
                                            new BackupService(db)
                                                    .create(user, executable, file.toPath());
                                            return null;
                                        },
                                        x -> {
                                            prefs.put("lastBackup", LocalDateTime.now().toString());
                                            status.setText(
                                                    "Respaldo creado. Restauración todavía no comprobada.");
                                            show();
                                        },
                                        Views::error);
                            } catch (Exception ex) {
                                Views.error(ex);
                            }
                        },
                        true);
        body.getChildren()
                .add(
                        Views.cardWithIcon(
                                "Respaldos",
                                "backup",
                                Views.label(
                                        "Último respaldo creado desde este equipo: "
                                                + prefs.get("lastBackup", "Ninguno"),
                                        "muted"),
                                new Label("Herramienta PostgreSQL"),
                                binary,
                                choose,
                                create,
                                Views.label(
                                        "La creación y la verificación de una restauración son pasos diferentes. Consulta el manual de respaldo.",
                                        "muted")));
    }

    private void connection() {
        Label connected = new Label("Comprobando conexión…");
        Views.async(
                () -> new ConnectionSettingsService(db).status(),
                text -> connected.setText("PostgreSQL conectado · " + text),
                ex -> connected.setText("Sin conexión: " + ex.getMessage()));
        body.getChildren()
                .add(
                        Views.cardWithIcon(
                                "Conexión de PostgreSQL",
                                "database",
                                connected,
                                Views.button(
                                        "Configurar para el próximo inicio",
                                        "database",
                                        this::configureConnection,
                                        true),
                                Views.label(
                                        "El cambio se aplica al volver a abrir SIGF. La contraseña se protege con el almacén seguro del sistema.",
                                        "muted")));
    }

    private void configureConnection() {
        FormControls.save(
                        "Conexión de PostgreSQL",
                        new String[] {"Servidor", "Puerto", "Usuario", "Contraseña PostgreSQL"},
                        new String[] {"127.0.0.1", "5432", "sigf_app", ""},
                        Set.of(3),
                        Map.of(),
                        v ->
                                new ConnectionSettingsService(db)
                                        .save(
                                                user,
                                                v.get(0).trim(),
                                                Integer.parseInt(v.get(1)),
                                                v.get(2).trim(),
                                                v.get(3)))
                .ifPresent(
                        v ->
                                status.setText(
                                        "Conexión guardada. Se utilizará al volver a abrir SIGF."));
    }
}
