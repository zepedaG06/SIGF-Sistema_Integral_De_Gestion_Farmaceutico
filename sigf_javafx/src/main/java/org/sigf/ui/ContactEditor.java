package org.sigf.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.sigf.security.Security;
import org.sigf.services.ContactsService;
import org.sigf.services.DesktopData;

/** Alta contextual con selección por identificador y revisión opcional de coincidencias. */
final class ContactEditor {
    private final Dialog<Void> dialog = new Dialog<>();
    private final TextField name = field("contactName", 200),
            phone = field("contactPhone", 60),
            ruc = field("contactRuc", 30);
    private final Label message = Views.label("", "warning");
    private final ComboBox<ContactsService.Match> matches = new ComboBox<>();
    private final Button use = Views.button("Usar seleccionado", "check", this::useExisting, false);
    private final VBox alternatives =
            new VBox(
                    8,
                    new Label(
                            "Coincidencias por teléfono o RUC. Puedes usar un registro existente:"),
                    matches,
                    use);
    private final Button save;
    private final VBox form = new VBox(8);
    private final ContactsService service;
    private final Security.User user;
    private final String kind;
    private final Consumer<DesktopData.Option> selected;
    private List<String> reviewed;
    private boolean busy;

    static void create(
            Window owner,
            ContactsService service,
            Security.User user,
            String kind,
            String initialName,
            Consumer<DesktopData.Option> selected) {
        user.admin();
        new ContactEditor(owner, service, user, kind, initialName, selected).dialog.showAndWait();
    }

    private ContactEditor(
            Window owner,
            ContactsService service,
            Security.User user,
            String kind,
            String initialName,
            Consumer<DesktopData.Option> selected) {
        this.service = service;
        this.user = user;
        this.kind = kind;
        this.selected = selected;
        boolean customer = kind.equals("customer");
        dialog.initOwner(owner);
        dialog.setTitle(customer ? "Nuevo cliente" : "Nuevo proveedor");
        dialog.getDialogPane()
                .getStylesheets()
                .add(ContactEditor.class.getResource("/css/style.css").toExternalForm());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        save = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        save.setText(customer ? "Guardar cliente" : "Guardar proveedor");
        save.setId("contactSave");
        save.setGraphic(UiIcons.icon("save", "action-icon", 20));
        save.getStyleClass().add("primary");
        name.setText(initialName);
        phone.setPromptText("Opcional");
        ruc.setPromptText("Opcional");
        form.getChildren().addAll(new Label("Nombre *"), name, new Label("Teléfono"), phone);
        if (customer) {
            form.getChildren().addAll(new Label("RUC"), ruc);
        }
        alternatives.setVisible(false);
        alternatives.setManaged(false);
        matches.setMaxWidth(Double.MAX_VALUE);
        matches.setId("contactMatches");
        use.setId("contactUseExisting");
        use.disableProperty().bind(matches.valueProperty().isNull());
        VBox body =
                new VBox(
                        12,
                        Views.label(
                                "Se seleccionará automáticamente al guardar. La operación actual se conserva.",
                                "muted"),
                        form,
                        alternatives,
                        message);
        body.setPadding(new Insets(18));
        body.setPrefWidth(460);
        dialog.getDialogPane().setContent(body);
        for (TextField field : List.of(name, phone, ruc)) {
            field.textProperty()
                    .addListener(
                            (o, a, b) -> {
                                reviewed = null;
                                alternatives.setVisible(false);
                                alternatives.setManaged(false);
                                save.setText(customer ? "Guardar cliente" : "Guardar proveedor");
                            });
        }
        save.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    event.consume();
                    attemptSave();
                });
        dialog.setOnCloseRequest(
                event -> {
                    if (busy) {
                        event.consume();
                    }
                });
    }

    private static TextField field(String id, int maximum) {
        TextField field = new TextField();
        field.setId(id);
        field.setTextFormatter(
                new TextFormatter<String>(
                        change -> change.getControlNewText().length() <= maximum ? change : null));
        return field;
    }

    private void setBusy(boolean value) {
        busy = value;
        form.setDisable(value);
        alternatives.setDisable(value);
        save.setDisable(value);
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(value);
    }

    private void attemptSave() {
        if (busy) {
            return;
        }
        List<String> values =
                List.of(name.getText().trim(), phone.getText().trim(), ruc.getText().trim());
        if (values.getFirst().isBlank()) {
            message.setText("Escribe el nombre.");
            name.requestFocus();
            return;
        }
        setBusy(true);
        message.setText("Comprobando…");
        if (values.equals(reviewed)) {
            persist(values);
            return;
        }
        Views.async(
                () -> service.matches(user, kind, values.get(1), values.get(2)),
                rows -> {
                    if (rows.isEmpty()) {
                        persist(values);
                    } else {
                        reviewed = values;
                        matches.getItems().setAll(rows);
                        matches.getSelectionModel().selectFirst();
                        alternatives.setVisible(true);
                        alternatives.setManaged(true);
                        save.setText("Guardar como nuevo");
                        message.setText("Revisa las coincidencias antes de crear otro registro.");
                        setBusy(false);
                        dialog.getDialogPane().getScene().getWindow().sizeToScene();
                    }
                },
                this::failed);
    }

    private void persist(List<String> values) {
        message.setText("Guardando…");
        Views.async(
                () ->
                        kind.equals("customer")
                                ? service.createCustomer(
                                        user, values.getFirst(), values.get(1), values.get(2))
                                : service.createSupplier(user, values.getFirst(), values.get(1)),
                id -> {
                    setBusy(false);
                    dialog.close();
                    selected.accept(new DesktopData.Option(id, values.getFirst()));
                },
                this::failed);
    }

    private void useExisting() {
        if (busy || matches.getValue() == null) {
            return;
        }
        var match = matches.getValue();
        dialog.close();
        selected.accept(new DesktopData.Option(match.id(), match.name()));
    }

    private void failed(Throwable error) {
        setBusy(false);
        message.setText(Objects.toString(error.getMessage(), "No se pudo guardar. Reintenta."));
    }
}
