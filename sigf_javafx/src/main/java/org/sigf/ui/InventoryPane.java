package org.sigf.ui;

import java.util.List;
import java.util.Map;
import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.DesktopData;
import org.sigf.services.InventoryService;

public final class InventoryPane extends VBox {
    private final DesktopData data;
    private final InventoryService service;
    private final Security.User user;
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>(),
            lab = new ComboBox<>(),
            state = new ComboBox<>(),
            stock = new ComboBox<>();
    private final TableView<DesktopData.Product> table =
            Views.table(
                    "No hay productos que coincidan. Prueba otros filtros o registra un producto.");
    private final VBox detail = new VBox(12);
    private final Label status = new Label(), pageInfo = new Label();
    private final java.util.function.BiConsumer<String, String> navigate;
    private int page, version, detailVersion;
    private boolean hasNext;
    private Map<String, List<String>> catalog = Map.of();

    public InventoryPane(
            Database db,
            Security.User user,
            java.util.function.BiConsumer<String, String> navigate) {
        this.user = user;
        this.navigate = navigate;
        data = new DesktopData(db);
        service = new InventoryService(db);
        getStyleClass().add("workspace");
        setSpacing(16);
        Button add = Views.button("Nuevo producto", "plus", () -> edit(null), true);
        add.setDisable(!user.role().equals("ADMIN"));
        getChildren()
                .add(
                        Views.headerWithIcon(
                                "Inventario de productos",
                                "Disponibilidad, clasificación y lotes en el mismo espacio",
                                "Inventario",
                                add));
        search.setPromptText("Buscar nombre, código o código de barras");
        search.setId("inventorySearch");
        category.getItems().add("Todas las categorías");
        category.setValue("Todas las categorías");
        lab.getItems().add("Todos los laboratorios");
        lab.setValue("Todos los laboratorios");
        state.getItems().addAll("Todos", "Activo", "Inactivo");
        state.setValue("Todos");
        stock.getItems().addAll("Todas", "Disponible", "Agotado", "Stock bajo");
        stock.setValue("Todas");
        FlowPane filters =
                new FlowPane(8, 8, Views.searchBox(search, "search"), category, lab, state, stock);
        search.setPrefWidth(310);
        PauseTransition pause = new PauseTransition(Duration.millis(250));
        search.textProperty()
                .addListener(
                        (o, a, b) -> {
                            page = 0;
                            pause.setOnFinished(e -> reload());
                            pause.playFromStart();
                        });
        for (var combo : List.of(category, lab, state, stock)) {
            combo.setOnAction(
                    e -> {
                        page = 0;
                        reload();
                    });
        }
        Views.column(table, "Código", DesktopData.Product::code);
        Views.column(table, "Producto / presentación", p -> p.name() + " · " + p.presentation());
        Views.column(table, "Precio final", p -> Views.money(p.finalPrice()));
        Views.column(table, "Vendible", p -> p.active() ? p.available() : 0);
        Views.column(table, "Estado", DesktopData.Product::state);
        Views.widths(table, 1, 3, 1.5, 1, 1.6);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> show(b));
        table.setRowFactory(
                t -> {
                    TableRow<DesktopData.Product> row = new TableRow<>();
                    row.setOnMouseClicked(
                            e -> {
                                if (e.getClickCount() == 2 && !row.isEmpty()) {
                                    edit(row.getItem());
                                }
                            });
                    return row;
                });
        table.setOnKeyPressed(
                e -> {
                    if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                        edit(table.getSelectionModel().getSelectedItem());
                    }
                });
        Button
                prev =
                        Views.button(
                                "Anterior",
                                "chevron-left",
                                () -> {
                                    if (page > 0) {
                                        page--;
                                        reload();
                                    }
                                },
                                false),
                next =
                        Views.button(
                                "Siguiente",
                                "chevron-right",
                                () -> {
                                    if (hasNext) {
                                        page++;
                                        reload();
                                    }
                                },
                                false);
        VBox list =
                Views.cardWithIcon(
                        "Catálogo",
                        "Inventario",
                        filters,
                        table,
                        new HBox(10, prev, pageInfo, next));
        VBox.setVgrow(table, Priority.ALWAYS);
        detail.getStyleClass().add("work-card");
        detail.setMinWidth(280);
        detail.getChildren()
                .add(
                        Views.label(
                                "Selecciona un producto para consultar su ficha y sus lotes",
                                "muted"));
        ScrollPane details = new ScrollPane(detail);
        details.setFitToWidth(true);
        SplitPane split = new SplitPane(list, details);
        split.setDividerPositions(.66);
        Views.responsive(split, "inventory");
        VBox.setVgrow(split, Priority.ALWAYS);
        getChildren().addAll(split, status);
        Views.async(
                service::catalog,
                options -> {
                    catalog = options;
                    category.getItems().addAll(options.getOrDefault("Categoría", List.of()));
                    lab.getItems().addAll(options.getOrDefault("Laboratorio", List.of()));
                },
                ex -> status.setText(ex.getMessage()));
        reload();
    }

    public void setFilter(String value) {
        if (value.equals("Stock bajo")) {
            stock.setValue("Stock bajo");
            state.setValue("Activo");
        } else {
            search.setText(value);
        }
        page = 0;
        reload();
    }

    public void reload() {
        int ticket = ++version;
        String text = search.getText(),
                c = category.getSelectionModel().getSelectedIndex() < 1 ? "" : category.getValue(),
                l = lab.getSelectionModel().getSelectedIndex() < 1 ? "" : lab.getValue(),
                s = state.getValue(),
                st = stock.getValue();
        status.setText("Consultando inventario…");
        Views.async(
                () -> data.products(text, c, l, s, st, page * 50),
                rows -> {
                    if (ticket != version) {
                        return;
                    }
                    hasNext = rows.size() > 50;
                    long selected =
                            table.getSelectionModel().getSelectedItem() == null
                                    ? -1
                                    : table.getSelectionModel().getSelectedItem().id();
                    table.getItems().setAll(rows.subList(0, Math.min(50, rows.size())));
                    table.getItems().stream()
                            .filter(p -> p.id() == selected)
                            .findFirst()
                            .ifPresent(p -> table.getSelectionModel().select(p));
                    pageInfo.setText(
                            "Página "
                                    + (page + 1)
                                    + " · "
                                    + table.getItems().size()
                                    + " productos");
                    status.setText("Datos actualizados · doble clic para editar");
                },
                ex -> {
                    if (ticket == version) {
                        status.setText("No se pudo consultar: " + ex.getMessage());
                    }
                });
    }

    private void show(DesktopData.Product p) {
        int ticket = ++detailVersion;
        detail.getChildren().clear();
        if (p == null) {
            detail.getChildren().add(Views.label("Selecciona un producto", "muted"));
            return;
        }
        detail.getChildren()
                .addAll(
                        Views.heading(p.name(), "panel-heading", "Inventario"),
                        Views.label(p.code() + " · " + p.state(), "status-chip"),
                        new Label("Presentación: " + p.presentation()),
                        new Label("Laboratorio: " + p.laboratory()),
                        new Label("Precio sin IVA: " + Views.money(p.price())),
                        new Label(
                                "IVA: "
                                        + (p.tax() == null
                                                ? "Sin clasificar"
                                                : p.tax()
                                                                .multiply(
                                                                        new java.math.BigDecimal(
                                                                                "100"))
                                                                .stripTrailingZeros()
                                                                .toPlainString()
                                                        + " %")),
                        Views.label("Precio final: " + Views.money(p.finalPrice()), "sale-total"),
                        new Label(
                                "Mínimo: "
                                        + p.minimum()
                                        + " · Vendible: "
                                        + (p.active() ? p.available() : 0)),
                        new Label("Existencia física total: " + p.total()));
        Button edit = Views.button("Editar producto", "edit", () -> edit(p), true);
        edit.setDisable(!user.role().equals("ADMIN"));
        Button lots =
                Views.button(
                        "Ver lotes y ajustar",
                        "Inventario",
                        () -> navigate.accept("Lotes", p.code()),
                        false);
        detail.getChildren().addAll(edit, lots);
        Views.async(
                () -> data.productLots(p.id()),
                grid -> {
                    if (ticket != detailVersion) {
                        return;
                    }
                    TableView<List<String>> table = Views.table("Sin lotes registrados");
                    Views.grid(table, grid);
                    table.setPrefHeight(190);
                    detail.getChildren()
                            .addAll(
                                    Views.heading(
                                            "Lotes del producto", "panel-heading", "calendar"),
                                    table);
                },
                ex -> status.setText(ex.getMessage()));
        Views.async(
                () -> data.productMoves(p.id()),
                grid -> {
                    if (ticket != detailVersion) {
                        return;
                    }
                    TableView<List<String>> table = Views.table("Sin movimientos");
                    Views.grid(table, grid);
                    table.setPrefHeight(170);
                    detail.getChildren()
                            .addAll(
                                    Views.heading(
                                            "Últimos movimientos", "panel-heading", "activity"),
                                    table);
                },
                ex -> status.setText(ex.getMessage()));
    }

    private void edit(DesktopData.Product p) {
        if (!user.role().equals("ADMIN")) {
            return;
        }
        status.setText("Cargando formulario…");
        Views.async(
                () -> new Object[] {p == null ? null : service.details(p.id()), service.catalog()},
                result -> {
                    @SuppressWarnings("unchecked")
                    Map<String, List<String>> options = (Map<String, List<String>>) result[1];
                    ProductEditor.open(
                            getScene().getWindow(),
                            service,
                            user,
                            p == null ? null : p.id(),
                            (InventoryService.ProductDetails) result[0],
                            options,
                            () -> {
                                reload();
                                Views.async(
                                        service::catalog,
                                        v -> {
                                            catalog = v;
                                            String c = category.getValue(), l = lab.getValue();
                                            category.getItems().setAll("Todas las categorías");
                                            category.getItems().addAll(v.get("Categoría"));
                                            category.setValue(c);
                                            lab.getItems().setAll("Todos los laboratorios");
                                            lab.getItems().addAll(v.get("Laboratorio"));
                                            lab.setValue(l);
                                        },
                                        Views::error);
                            });
                },
                Views::error);
    }
}
