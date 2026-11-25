package org.sigf.ui;

import java.util.Objects;
import javafx.animation.PauseTransition;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
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

    EntityPicker(DesktopData data, String kind, String caption) {
        super(4);
        this.data = data;
        this.kind = kind;
        search.setPromptText(caption);
        choices.setPromptText(
                kind.equals("customer") ? "Consumidor final" : "Seleccione proveedor");
        choices.setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(Views.searchBox(search, "search"), choices);
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
                },
                ex -> search.setPromptText("No se pudo cargar; escriba para reintentar"));
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
