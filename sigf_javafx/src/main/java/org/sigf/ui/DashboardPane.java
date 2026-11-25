package org.sigf.ui;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.sigf.config.Database;
import org.sigf.domain.ReportGrid;
import org.sigf.services.Operations;

/** Consultas reales y accesos que mantienen el contexto de cada alerta. */
public final class DashboardPane extends VBox {
    private final Operations ops;
    private final BiConsumer<String, String> navigate;
    private final Label state = Views.label("Consultando…", "dashboard-status");
    private final HBox cards = new HBox(14);
    private final TableView<List<String>> low = Views.table("Sin productos con existencias bajas"),
            expiry = Views.table("Sin lotes próximos a vencer"),
            recent = Views.table("Todavía no hay movimientos registrados");
    private int version;

    private record Summary(
            ReportGrid indicators, ReportGrid low, ReportGrid expiry, ReportGrid recent) {}

    public DashboardPane(Database db, BiConsumer<String, String> navigate) {
        this(db, navigate, false);
    }

    public DashboardPane(Database db, BiConsumer<String, String> navigate, boolean canPurchase) {
        this.ops = new Operations(db);
        this.navigate = navigate;
        low.setId("dashboardLowStock");
        expiry.setId("dashboardExpiring");
        setSpacing(18);
        getStyleClass().add("dashboard");
        cards.setMinHeight(110);
        cards.setPrefHeight(110);
        cards.setMaxHeight(110);
        Button reload = Views.button("Actualizar", "refresh", this::reload, false),
                sale =
                        Views.button(
                                "Nueva venta",
                                "Ventas",
                                () -> navigate.accept("Punto de venta", ""),
                                true);
        getChildren()
                .add(
                        Views.headerWithIcon(
                                "Resumen de la farmacia",
                                "Tu operación de hoy, en un solo lugar",
                                "Inicio",
                                reload,
                                sale));
        TabPane alerts =
                new TabPane(
                        new Tab("Existencias bajas", low),
                        new Tab("Próximos vencimientos", expiry));
        alerts.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        alerts.getStyleClass().add("alert-tabs");
        alerts.getTabs().get(0).setGraphic(UiIcons.icon("alert", "tab-icon", 18));
        alerts.getTabs().get(1).setGraphic(UiIcons.icon("calendar", "tab-icon", 18));
        Button attend =
                Views.button(
                        "Revisar alerta seleccionada",
                        "alert",
                        () -> {
                            if (alerts.getSelectionModel().getSelectedIndex() == 0) {
                                var r = low.getSelectionModel().getSelectedItem();
                                navigate.accept(
                                        "Productos", r == null ? "Stock bajo" : r.getFirst());
                            } else {
                                var r = expiry.getSelectionModel().getSelectedItem();
                                navigate.accept("Lotes", r == null ? "Por vencer" : r.get(1));
                            }
                        },
                        false);
        Button prepare =
                Views.button(
                        "Preparar compra",
                        "Compras",
                        () -> {
                            var row = low.getSelectionModel().getSelectedItem();
                            if (row != null) {
                                navigate.accept("Nueva compra", row.getFirst());
                            }
                        },
                        false);
        prepare.setId("dashboardPreparePurchase");
        prepare.disableProperty().bind(low.getSelectionModel().selectedItemProperty().isNull());
        prepare.visibleProperty()
                .bind(
                        alerts.getSelectionModel()
                                .selectedIndexProperty()
                                .isEqualTo(0)
                                .and(new javafx.beans.property.SimpleBooleanProperty(canPurchase)));
        prepare.managedProperty().bind(prepare.visibleProperty());
        VBox left =
                Views.cardWithIcon(
                        "Atención necesaria", "alert", alerts, new FlowPane(8, 8, attend, prepare));
        VBox.setVgrow(alerts, Priority.ALWAYS);
        VBox right =
                Views.cardWithIcon(
                        "Actividad reciente",
                        "activity",
                        recent,
                        Views.button(
                                "Ver movimientos",
                                "activity",
                                () -> navigate.accept("Movimientos", ""),
                                false));
        VBox.setVgrow(recent, Priority.ALWAYS);
        SplitPane panels = new SplitPane(left, right);
        panels.setDividerPositions(.48);
        Views.responsive(panels, "dashboard-width");
        VBox.setVgrow(panels, Priority.ALWAYS);
        SplitPane body = new SplitPane(cards, panels);
        body.setOrientation(javafx.geometry.Orientation.VERTICAL);
        body.setDividerPositions(.2);
        Views.adjustable(body, "dashboard-height");
        panels.setMinHeight(220);
        cards.setMinHeight(90);
        cards.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(body, state);
        reload();
    }

    public void reload() {
        int request = ++version;
        state.setText("Consultando datos…");
        Views.async(
                () ->
                        new Summary(
                                ops.grid("dashboard"),
                                ops.grid("dashboard_low"),
                                ops.grid("dashboard_expiring"),
                                ops.grid("dashboard_recent")),
                x -> {
                    if (request != version) {
                        return;
                    }
                    cards.getChildren().clear();
                    if (!x.indicators.rows().isEmpty()) {
                        String[] icons = {"cash", "Inventario", "alert", "calendar"};
                        String[]
                                names =
                                        {
                                            "Ventas de hoy",
                                            "Productos activos",
                                            "Existencias bajas",
                                            "Lotes por vencer"
                                        },
                                targets =
                                        {"Historial de ventas", "Productos", "Productos", "Lotes"},
                                filters = {"", "", "Stock bajo", "Por vencer"};
                        int[] indexes = {4, 0, 3, 2};
                        for (int i = 0; i < names.length; i++) {
                            int n = i, idx = indexes[i];
                            String raw = x.indicators.rows().getFirst().get(idx);
                            Label value =
                                    Views.label(
                                            idx == 4
                                                    ? Views.money(new java.math.BigDecimal(raw))
                                                    : raw,
                                            "dashboard-card-value");
                            VBox content =
                                    new VBox(
                                            6,
                                            Views.label(names[i], "dashboard-card-label"),
                                            value);
                            Button card =
                                    Views.button(
                                            "",
                                            () -> navigate.accept(targets[n], filters[n]),
                                            false);
                            StackPane glyph =
                                    new StackPane(UiIcons.icon(icons[i], "metric-icon", 27));
                            glyph.setMinSize(46, 46);
                            glyph.setPrefSize(46, 46);
                            glyph.setMaxSize(46, 46);
                            glyph.getStyleClass().add("metric-icon-badge");
                            HBox graphic = new HBox(12, glyph, content);
                            graphic.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                            card.setGraphic(graphic);
                            card.setAccessibleText(names[i] + ": " + value.getText());
                            card.getStyleClass().add("dashboard-card");
                            card.setMinWidth(0);
                            card.setPrefWidth(0);
                            card.setMaxHeight(Double.MAX_VALUE);
                            HBox.setHgrow(card, Priority.ALWAYS);
                            card.setMaxWidth(Double.MAX_VALUE);
                            if (idx == 2 || idx == 3) {
                                card.getStyleClass().add("dashboard-card-warning");
                            }
                            cards.getChildren().add(card);
                        }
                    }
                    Views.grid(low, x.low);
                    Views.grid(expiry, x.expiry);
                    Views.grid(recent, x.recent);
                    state.setText(
                            "Actualizado a las "
                                    + LocalTime.now()
                                            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                                    + " · "
                                    + x.indicators.rows().getFirst().get(1)
                                    + " unidades vendibles");
                },
                ex -> {
                    if (request == version) {
                        state.setText("No se pudo consultar: " + ex.getMessage());
                    }
                });
    }
}
