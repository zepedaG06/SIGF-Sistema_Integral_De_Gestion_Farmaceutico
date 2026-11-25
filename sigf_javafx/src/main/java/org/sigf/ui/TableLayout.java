package org.sigf.ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.prefs.Preferences;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Duration;

/** Tamaños locales: no contienen datos comerciales ni bloquean el ancho de las columnas. */
final class TableLayout {
    private static final String KEY = "sigf.table.layout";
    private final TableView<?> table;
    private final Preferences prefs = Preferences.userRoot().node("org/sigf/javafx/layout/tables");
    private final Map<TableColumn<?, ?>, ChangeListener<Number>> listeners = new HashMap<>();
    private final PauseTransition save = new PauseTransition(Duration.millis(350));
    private final List<TableColumn<?, ?>> configuredColumns = new ArrayList<>();
    private String schema;
    private double[] weights;
    private boolean updating, customized;

    private TableLayout(TableView<?> table) {
        this.table = table;
        table.widthProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!customized) {
                                fit();
                            }
                        });
        save.setOnFinished(e -> persist());
        MenuItem reset = new MenuItem("Restablecer anchos de columnas");
        reset.setOnAction(
                e -> {
                    save.stop();
                    prefs.remove(schema);
                    customized = false;
                    fit();
                });
        table.setContextMenu(new ContextMenu(reset));
        table.setTableMenuButtonVisible(true);
    }

    static void configure(TableView<?> table, double[] weights) {
        TableLayout layout =
                (TableLayout)
                        table.getProperties().computeIfAbsent(KEY, k -> new TableLayout(table));
        layout.configure(weights);
    }

    static void beforeRefresh(TableView<?> table) {
        TableLayout layout = (TableLayout) table.getProperties().get(KEY);
        if (layout != null && layout.customized) {
            layout.save.stop();
            layout.persist();
        }
    }

    private void configure(double[] weights) {
        configuredColumns.clear();
        for (var col : table.getColumns()) {
            configuredColumns.add(col);
        }
        listeners.forEach((c, l) -> c.widthProperty().removeListener(l));
        listeners.clear();
        save.stop();
        this.weights = weights.clone();
        schema =
                UUID.nameUUIDFromBytes(
                                (Objects.toString(table.getId(), "")
                                                + table.getColumns().stream()
                                                        .map(TableColumn::getText)
                                                        .toList())
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        String saved = prefs.get(schema, "");
        String[] widths = saved.split(",");
        customized = widths.length == weights.length && !saved.isBlank();
        updating = true;
        try {
            for (int i = 0; i < weights.length; i++) {
                var col = table.getColumns().get(i);
                col.prefWidthProperty().unbind();
                col.setResizable(true);
                if (customized) {
                    try {
                        double w = Double.parseDouble(widths[i]);
                        if (!Double.isFinite(w) || w < 30 || w > 4000) {
                            throw new NumberFormatException();
                        }
                        col.setPrefWidth(w);
                    } catch (NumberFormatException ex) {
                        customized = false;
                    }
                }
            }
        } finally {
            updating = false;
        }
        fit();
        for (var col : table.getColumns()) {
            ChangeListener<Number> listener =
                    (o, a, b) -> {
                        if (!updating) {
                            customized = true;
                            updating = true;
                            try {
                                col.setPrefWidth(b.doubleValue());
                            } finally {
                                updating = false;
                            }
                            save.playFromStart();
                        }
                    };
            col.widthProperty().addListener(listener);
            listeners.put(col, listener);
        }
    }

    private void fit() {
        if (customized || table.getWidth() < 100) {
            return;
        }
        updating = true;
        try {
            double sum = Arrays.stream(weights).sum();
            for (int i = 0; i < weights.length; i++) {
                table.getColumns()
                        .get(i)
                        .setPrefWidth(
                                Math.max(
                                        table.getColumns().get(i).getMinWidth(),
                                        (table.getWidth() - 22) * weights[i] / sum));
            }
        } finally {
            updating = false;
        }
    }

    private void persist() {
        prefs.put(
                schema,
                configuredColumns.stream()
                        .map(c -> Double.toString(c.getWidth()))
                        .collect(java.util.stream.Collectors.joining(",")));
    }
}
