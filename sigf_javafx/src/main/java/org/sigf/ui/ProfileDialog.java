package org.sigf.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.PharmacyProfileService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;

/** Formulario de identidad; solo comunica éxito después de guardar. */
public final class ProfileDialog {
    private ProfileDialog() {}

    public static void open(Window owner, Database db, Security.User user, Runnable finished) {
        open(owner, db, user, false, finished, () -> {});
    }

    public static void openInitial(Window owner, Database db, Security.User user, Runnable finished, Runnable failed) {
        open(owner, db, user, true, finished, failed);
    }

    private static void open(Window owner, Database db, Security.User user, boolean initial, Runnable finished, Runnable failed) {
        PharmacyProfileService service = new PharmacyProfileService(db);
        Task<Optional<PharmacyProfileService.Profile>> load = new Task<>() {
            protected Optional<PharmacyProfileService.Profile> call() throws Exception { return service.load(); }
        };
        load.setOnSucceeded(e -> show(owner, service, user, load.getValue(), initial, finished));
        load.setOnFailed(e -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No se pudo cargar la configuración: " + message(load.getException()));
            alert.initOwner(owner);
            alert.showAndWait();
            failed.run();
        });
        start(load);
    }

    private static void show(Window owner, PharmacyProfileService service, Security.User user,
                             Optional<PharmacyProfileService.Profile> saved, boolean initial, Runnable finished) {
        var old = saved.orElse(new PharmacyProfileService.Profile("", "", "", "", "", "", null));
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(initial ? "Configuración inicial de la farmacia" : "Datos de la farmacia");
        dialog.setMinWidth(760);
        dialog.setMinHeight(600);

        TextField name = field("profileName", old.name(), "Ej. Farmacia Salud Total");
        TextField legal = field("profileLegal", old.legalName(), "Nombre del titular o razón social");
        TextField ruc = field("profileRuc", old.ruc(), "RUC de la farmacia");
        TextField address = field("profileAddress", old.address(), "Dirección de la farmacia");
        TextField phone = field("profilePhone", old.phone(), "Número de teléfono");
        TextField email = field("profileEmail", old.email(), "Ej. farmacia@ejemplo.com");
        VBox details = new VBox(12, section("DATOS DE LA FARMACIA"), row("Nombre de farmacia *", name),
                row("Razón social o titular *", legal), row("RUC *", ruc),
                section("INFORMACIÓN DE CONTACTO"), row("Dirección *", address),
                row("Teléfono *", phone), row("Correo electrónico", email));
        details.getStyleClass().add("setup-card");
        HBox.setHgrow(details, Priority.ALWAYS);
        details.setMinWidth(0);
        details.setPrefWidth(620);

        ImageView preview = new ImageView();
        preview.setFitWidth(190); preview.setFitHeight(160); preview.setPreserveRatio(true); preview.setSmooth(true);
        Label placeholder = new Label("✚"); placeholder.getStyleClass().add("setup-logo-mark");
        StackPane previewBox = new StackPane(placeholder, preview);
        previewBox.getStyleClass().add("setup-logo-preview");
        previewBox.setPrefHeight(210); previewBox.setMinHeight(120);
        byte[][] logo = {old.logo()};
        setPreview(preview, placeholder, logo[0]);
        Label logoStatus = new Label(logo[0] == null ? "Sin logo seleccionado" : "Logo guardado");
        logoStatus.getStyleClass().add("muted");
        Button select = new Button("Seleccionar logo"); select.setGraphic(UiIcons.icon("upload","action-icon",20));select.getStyleClass().add("primary"); select.setMaxWidth(Double.MAX_VALUE);
        Button remove = new Button("Quitar logo"); remove.setGraphic(UiIcons.icon("delete","action-icon",20));remove.getStyleClass().add("setup-secondary");
        select.setOnAction(e -> {
            FileChooser chooser = new FileChooser(); chooser.setTitle("Seleccionar logo");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG o JPEG", "*.png", "*.jpg", "*.jpeg"));
            File file = chooser.showOpenDialog(dialog);
            if (file == null) return;
            try {
                if (Files.size(file.toPath()) > 2097152) throw new IllegalArgumentException("El logo no puede superar 2 MB");
                byte[] bytes = Files.readAllBytes(file.toPath()); PharmacyProfileService.validateLogo(bytes);
                logo[0] = bytes; setPreview(preview, placeholder, bytes); logoStatus.setText("Logo seleccionado");
            } catch (Exception ex) { statusAlert(dialog, message(ex)); }
        });
        remove.setOnAction(e -> { logo[0] = null; setPreview(preview, placeholder, null); logoStatus.setText("Sin logo seleccionado"); });
        Label hint = new Label("PNG o JPG · Máximo 2 MB"); hint.getStyleClass().add("muted");
        VBox identity = new VBox(14, section("IDENTIDAD VISUAL"), previewBox, select, hint, logoStatus, remove);
        identity.getStyleClass().add("setup-card"); identity.setAlignment(Pos.TOP_CENTER); identity.setPrefWidth(300); identity.setMinWidth(230);
        HBox columns = new HBox(18, details, identity); columns.setFillHeight(true);

        Label brand = new Label("✚  SIGF  /  FARMACIA"); brand.getStyleClass().add("setup-brand");
        Label title = new Label(initial ? "Configura tu farmacia" : "Datos de tu farmacia"); title.getStyleClass().add("setup-title");
        Label subtitle = new Label("Ingresa la información básica de tu farmacia para comenzar a usar SIGF."); subtitle.getStyleClass().add("setup-subtitle"); subtitle.setWrapText(true);
        Label notice = new Label("* Campos obligatorios. Estos datos no autorizan por sí solos la emisión de facturas oficiales.");
        notice.getStyleClass().add("muted"); notice.setWrapText(true);
        Label status = new Label(); status.setId("profileStatus"); status.getStyleClass().add("warning"); status.setWrapText(true);
        Button cancel = new Button(initial ? "Abandonar configuración" : "Cancelar"); cancel.setId("profileCancel");cancel.setGraphic(UiIcons.icon("close","action-icon",20)); cancel.getStyleClass().add("setup-secondary");
        Button save = new Button("Guardar datos"); save.setId("profileSave");save.setGraphic(UiIcons.icon("save","action-icon",20)); save.getStyleClass().add("primary");
        HBox actions = new HBox(12, cancel, save); actions.setAlignment(Pos.CENTER_RIGHT);
        VBox content = new VBox(15, brand, title, subtitle, columns, notice, status, actions);
        content.getStyleClass().add("setup-root");
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.getStyleClass().add("setup-scroll");
        Scene scene = new Scene(scroll, 1040, 760);
        scene.getStylesheets().add(ProfileDialog.class.getResource("/css/style.css").toExternalForm());
        dialog.setScene(scene);
        boolean[] saving = {false};
        Runnable requestClose = () -> {
            if (saving[0]) return;
            if (!initial) {
                boolean changed = !name.getText().equals(old.name()) || !legal.getText().equals(old.legalName()) || !ruc.getText().equals(old.ruc()) || !address.getText().equals(old.address()) || !phone.getText().equals(old.phone()) || !email.getText().equals(old.email()) || !java.util.Arrays.equals(logo[0],old.logo());
                if (!changed || Views.confirm("Hay cambios sin guardar. ¿Descartarlos?")) dialog.hide(); return;
            }
            ButtonType keep = new ButtonType("Continuar configurando", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType leave = new ButtonType("Abandonar y cerrar", ButtonBar.ButtonData.OK_DONE);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "¿Seguro que quieres abandonar la configuración de la farmacia? Si abandonas, se descartarán los datos ingresados y se cerrará el sistema.", keep, leave);
            confirm.initOwner(dialog); confirm.setTitle("Abandonar configuración"); confirm.setHeaderText("La configuración aún no está completa");
            if (confirm.showAndWait().orElse(keep) == leave) { dialog.hide(); Platform.exit(); }
        };
        cancel.setOnAction(e -> requestClose.run());
        dialog.setOnCloseRequest(e -> { e.consume(); requestClose.run(); });
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) { e.consume(); requestClose.run(); } });
        save.setOnAction(e -> {
            var profile = new PharmacyProfileService.Profile(name.getText(), legal.getText(), ruc.getText(), address.getText(), phone.getText(), email.getText(), logo[0]);
            try { PharmacyProfileService.validateProfile(profile); }
            catch (IllegalArgumentException ex) { status.setText(message(ex)); return; }
            saving[0] = true; save.setDisable(true); cancel.setDisable(true); status.setText("Guardando…");
            Task<Void> task = new Task<>() { protected Void call() throws Exception { service.save(user, profile); return null; } };
            task.setOnSucceeded(event -> { saving[0] = false; dialog.hide(); finished.run(); });
            task.setOnFailed(event -> { saving[0] = false; save.setDisable(false); cancel.setDisable(false); status.setText(message(task.getException())); });
            start(task);
        });
        dialog.show();
    }

    private static TextField field(String id, String value, String prompt) { TextField f = new TextField(Objects.toString(value, "")); f.setId(id); f.setPromptText(prompt); f.setMaxWidth(Double.MAX_VALUE); return f; }
    private static VBox row(String label, TextField field) { Label l = new Label(label); l.getStyleClass().add("setup-field-label"); return new VBox(5, l, field); }
    private static Label section(String text) { Label l = new Label(text);l.setGraphic(UiIcons.icon(switch(text){case "DATOS DE LA FARMACIA"->"shop";case "INFORMACIÓN DE CONTACTO"->"phone";default->"image";},"heading-icon")); l.getStyleClass().add("setup-section"); return l; }
    private static void setPreview(ImageView view, Label placeholder, byte[] data) { view.setImage(data == null ? null : new Image(new ByteArrayInputStream(data), 190, 160, true, true)); placeholder.setVisible(data == null); }
    private static void statusAlert(Window owner, String text) { Alert alert = new Alert(Alert.AlertType.ERROR, text); alert.initOwner(owner); alert.showAndWait(); }
    private static String message(Throwable e) { return e == null ? "Error desconocido" : Objects.toString(e.getMessage(), "Error desconocido"); }
    private static void start(Task<?> task) { Thread thread = new Thread(task, "sigf-profile"); thread.setDaemon(true); thread.start(); }
}
