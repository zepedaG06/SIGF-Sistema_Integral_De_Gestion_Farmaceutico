package org.sigf.ui;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.sigf.services.DesktopData;

/** Selector que consulta el servidor sin cargar el catálogo completo. */
final class EntityPicker extends VBox {
    private final TextField search = new TextField();
    private final ComboBox<DesktopData.Option> choices = new ComboBox<>();
    private final DesktopData data;
    private final String kind;
    private int version;
    private boolean updating;
    private final Button create = Views.button("", "plus", () -> {}, false);
    private final Label hint = Views.label("", "muted");

    EntityPicker(DesktopData data, String kind, String caption) {
        super(4);
        this.data = data;
        this.kind = kind;
        search.setPromptText(caption);
        choices.setPromptText(
                kind.equals("customer") ? "Consumidor final" : "Seleccione proveedor");
        choices.setMaxWidth(Double.MAX_VALUE);
        search.setId(kind + "Search");
        choices.setId(kind + "Choice");
        create.setId(kind + "Create");
        create.setVisible(false);
        create.setManaged(false);
        var searchBox = Views.searchBox(search, "search");
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        hint.setVisible(false);
        hint.setManaged(false);
        getChildren().addAll(new HBox(8, searchBox, create), choices, hint);
        PauseTransition pause = new PauseTransition(Duration.millis(250));
        search.textProperty()
                .addListener(
                        (o, a, b) -> {
                            pause.setOnFinished(e -> load());
                            pause.playFromStart();
                        });
        load();
    }

    private void load() {
        int request = ++version;
        String text = search.getText();
        Views.async(
                () -> data.options(kind, text),
                rows -> {
                    if (request != version) {
                        return;
                    }
                    updating = true;
                    var selected = choices.getValue();
                    choices.getItems().setAll(rows);
                    if (selected != null && !rows.contains(selected)) {
                        choices.getItems().add(0, selected);
                    }
                    choices.setValue(selected);
                    updating = false;
                    boolean empty = rows.isEmpty() && !text.isBlank();
                    hint.setText(
                            "Sin coincidencias."
                                    + (create.isVisible()
                                            ? " Puedes usar «" + create.getText() + "»."
                                            : ""));
                    hint.setVisible(empty);
                    hint.setManaged(empty);
                },
                ex -> search.setPromptText("No se pudo cargar; escriba para reintentar"));
    }

    void allowCreate(String caption, Consumer<String> action) {
        create.setText(caption);
        create.setGraphic(
                UiIcons.icon(kind.equals("customer") ? "user-plus" : "plus", "action-icon", 20));
        create.setOnAction(event -> action.accept(search.getText().trim()));
        create.setVisible(true);
        create.setManaged(true);
        choices.setMinWidth(0);
        search.setMinWidth(80);
    }

    void onChange(Runnable changed) {
        choices.valueProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!updating && !Objects.equals(a, b)) {
                                changed.run();
                            }
                        });
    }

    Long id() {
        return choices.getValue() == null ? null : choices.getValue().id();
    }

    void select(DesktopData.Option option) {
        if (option != null && !choices.getItems().contains(option)) {
            choices.getItems().add(option);
        }
        choices.setValue(option);
    }

    void clear() {
        choices.setValue(null);
        search.clear();
    }
}
