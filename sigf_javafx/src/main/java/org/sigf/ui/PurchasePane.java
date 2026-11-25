package org.sigf.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.sigf.config.Database;
import org.sigf.domain.PurchaseItem;
import org.sigf.security.Security;
import org.sigf.services.ContactsService;
import org.sigf.services.DesktopData;
import org.sigf.services.InventoryService;
import org.sigf.services.PurchaseDraftService;
import org.sigf.services.PurchaseRecovery;

public final class PurchasePane extends VBox {
    private final DesktopData data;
    private final Security.User user;
    private final PurchaseRecovery recovery;
    private final PurchaseDraftService drafts;
    private final EntityPicker supplier;
    private final List<PurchaseItem> lines = new ArrayList<>();
    private final Map<Long, String> names = new HashMap<>();
    private final TableView<PurchaseItem> table =
            Views.table("Busca y agrega productos a la recepción");
    private final TableView<DesktopData.Product> results =
            Views.table("Escribe para buscar un producto");
    private final TextField search = new TextField();
    private final Label status = new Label(), summary = new Label();
    private final VBox editor;
    private UUID pending;
    private boolean busy, dirty;
    private int generation;
    private final InventoryService inventory;
    private final Button newProduct;
    private boolean loadingDraft = true;
    private String requestedCode;

    public PurchasePane(Database db, Security.User user) {
        this.user = user;
        data = new DesktopData(db);
        inventory = new InventoryService(db);
        recovery = new PurchaseRecovery(db);
        drafts = new PurchaseDraftService(db);
        supplier = new EntityPicker(data, "supplier", "Buscar proveedor");
        if (user.role().equals("ADMIN")) {
            supplier.allowCreate(
                    "Nuevo proveedor",
                    text -> {
                        if (!locked()) {
                            ContactEditor.create(
                                    getScene().getWindow(),
                                    new ContactsService(db),
                                    user,
                                    "supplier",
                                    text,
                                    option -> {
                                        supplier.select(option);
                                        status.setText("Proveedor seleccionado: " + option.name());
                                    });
                        }
                    });
        }
        newProduct = Views.button("Nuevo producto", "plus", this::createProduct, false);
        newProduct.setId("purchaseNewProduct");
        setSpacing(16);
        getStyleClass().add("workspace");
        supplier.setId("purchaseSupplier");
        supplier.onChange(
                () -> {
                    if (!busy) {
                        dirty = true;
                    }
                });
        table.setId("purchaseDraft");
        results.setId("purchaseProducts");
        Button commit = Views.button("Registrar recepción", "Compras", this::commit, true);
        commit.setId("purchaseCommit");
        Button save = Views.button("Guardar borrador", "save", () -> save(false), false);
        MenuButton more = new MenuButton("Más acciones", UiIcons.icon("more", "action-icon", 20));
        MenuItem clear = new MenuItem("Vaciar borrador", UiIcons.icon("delete", "action-icon", 20));
        clear.setOnAction(e -> clear());
        more.getItems().add(clear);
        HBox info =
                new HBox(
                        24,
                        new VBox(
                                6,
                                Views.heading("Proveedor", "field-caption", "Contactos"),
                                supplier),
                        new VBox(
                                8,
                                Views.heading("Recepción", "field-caption", "calendar"),
                                Views.label("Fecha de registro: " + LocalDate.now(), "muted"),
                                Views.label("Borrador · pendiente de registrar", "warning")));
        search.setPromptText("Buscar producto por nombre o código");
        search.setId("purchaseSearch");
        PauseTransition pause = new PauseTransition(Duration.millis(250));
        search.textProperty()
                .addListener(
                        (o, a, b) -> {
                            results.getItems().clear();
                            results.setDisable(true);
                            pause.setOnFinished(e -> search());
                            pause.playFromStart();
                        });
        search.setOnAction(e -> search());
        Views.column(results, "Código", DesktopData.Product::code);
        Views.column(results, "Producto / presentación", p -> p.name() + " · " + p.presentation());
        Views.column(results, "Costo de catálogo", p -> Views.money(p.cost()));
        TableColumn<DesktopData.Product, Void> add = new TableColumn<>("");
        add.setCellFactory(
                c ->
                        new TableCell<>() {
                            final Button b =
                                    Views.button(
                                            "Agregar",
                                            "plus",
                                            () -> add(getTableView().getItems().get(getIndex())),
                                            false);

                            protected void updateItem(Void v, boolean empty) {
                                super.updateItem(v, empty);
                                setGraphic(empty ? null : b);
                            }
                        });
        results.getColumns().add(add);
        Views.widths(results, 1, 3, 1.5, 1);
        results.setPrefHeight(150);
        results.setMinHeight(130);
        Views.column(
                table,
                "Producto",
                p -> names.getOrDefault(p.productId(), "Producto " + p.productId()));
        editable(
                "Lote",
                PurchaseItem::lot,
                (p, v) ->
                        new PurchaseItem(
                                p.productId(), v.trim(), p.expiry(), p.quantity(), p.cost()));
        expiryColumn();
        editable(
                "Cantidad",
                p -> Integer.toString(p.quantity()),
                (p, v) ->
                        new PurchaseItem(
                                p.productId(), p.lot(), p.expiry(), Integer.parseInt(v), p.cost()));
        editable(
                "Costo unitario C$",
                p -> p.cost().toPlainString(),
                (p, v) ->
                        new PurchaseItem(
                                p.productId(),
                                p.lot(),
                                p.expiry(),
                                p.quantity(),
                                new BigDecimal(v).setScale(2, RoundingMode.UNNECESSARY)));
        Views.column(
                table,
                "Subtotal",
                p -> Views.money(p.cost().multiply(BigDecimal.valueOf(p.quantity()))));
        TableColumn<PurchaseItem, Void> actions = new TableColumn<>("");
        actions.setCellFactory(
                c ->
                        new TableCell<>() {
                            final Button remove =
                                    Views.button(
                                            "Quitar",
                                            () -> {
                                                if (locked()) {
                                                    return;
                                                }
                                                lines.remove(
                                                        getTableView().getItems().get(getIndex()));
                                                dirty = true;
                                                render();
                                            },
                                            false);

                            protected void updateItem(Void v, boolean empty) {
                                super.updateItem(v, empty);
                                setGraphic(empty ? null : remove);
                            }
                        });
        table.getColumns().add(actions);
        Views.widths(table, 2.6, 1.6, 1.8, 1, 1.7, 1.7, 1);
        table.setEditable(true);
        table.setMinHeight(160);
        summary.getStyleClass().add("sale-total");
        SplitPane purchaseTables = new SplitPane(results, table);
        purchaseTables.setOrientation(javafx.geometry.Orientation.VERTICAL);
        purchaseTables.setDividerPositions(.35);
        Views.adjustable(purchaseTables, "purchase-tables");
        results.setMinHeight(100);
        table.setMinHeight(120);
        VBox.setVgrow(purchaseTables, Priority.ALWAYS);
        editor =
                Views.cardWithIcon(
                        "Nueva compra",
                        "Compras",
                        info,
                        new HBox(10, Views.searchBox(search, "search"), newProduct),
                        purchaseTables,
                        summary,
                        new HBox(12, more, save, commit));
        ScrollPane editorScroll = new ScrollPane(editor);
        editorScroll.setFitToWidth(true);
        editorScroll.setFitToHeight(true);
        VBox.setVgrow(editorScroll, Priority.ALWAYS);
        getChildren()
                .addAll(
                        Views.headerWithIcon(
                                "Compras",
                                "Proveedor primero, recepción clara y totales visibles",
                                "Compras"),
                        editorScroll,
                        status);
        loadDraft();
    }

    private boolean locked() {
        if (busy || pending != null) {
            status.setText("Resuelve la compra pendiente antes de modificar el borrador");
            return true;
        }
        return false;
    }

    private void search() {
        int version = ++generation;
        String text = search.getText();
        Views.async(
                () -> data.products(text, "", "", "Activo", "Todas", 0),
                rows -> {
                    if (version == generation) {
                        results.getItems().setAll(rows.subList(0, Math.min(50, rows.size())));
                        results.setDisable(false);
                    }
                },
                ex -> status.setText(ex.getMessage()));
    }

    private void add(DesktopData.Product product) {
        add(product, 1);
    }

    private void add(DesktopData.Product product, int suggestedQuantity) {
        if (locked()) {
            return;
        }
        FormControls.show(
                        "Lote · " + product.name(),
                        new String[] {"Lote", "Vence AAAA-MM-DD", "Cantidad", "Costo unitario"},
                        new String[] {
                            "",
                            "",
                            Integer.toString(suggestedQuantity),
                            product.cost().toPlainString()
                        },
                        Set.of())
                .ifPresent(
                        v -> {
                            try {
                                var line =
                                        new PurchaseItem(
                                                product.id(),
                                                v.get(0).trim(),
                                                LocalDate.parse(v.get(1)),
                                                Integer.parseInt(v.get(2)),
                                                new BigDecimal(v.get(3)));
                                if (lines.stream()
                                        .anyMatch(
                                                p ->
                                                        p.productId() == line.productId()
                                                                && p.lot().equals(line.lot()))) {
                                    throw new IllegalArgumentException(
                                            "El producto y lote ya están en el borrador; edita su cantidad");
                                }
                                lines.add(line);
                                names.put(product.id(), product.name());
                                dirty = true;
                                render();
                                search.requestFocus();
                            } catch (Exception ex) {
                                Views.error(ex);
                            }
                        });
    }

    private void expiryColumn() {
        TableColumn<PurchaseItem, LocalDate> col = new TableColumn<>("Vencimiento");
        col.setMinWidth(150);
        col.setCellValueFactory(
                x -> new javafx.beans.property.ReadOnlyObjectWrapper<>(x.getValue().expiry()));
        col.setCellFactory(
                c ->
                        new TableCell<>() {
                            final DatePicker picker = new DatePicker();
                            boolean updating;

                            {
                                Views.date(picker);
                                picker.setOnAction(
                                        e -> {
                                            if (updating || locked()) {
                                                return;
                                            }
                                            try {
                                                picker.commitValue();
                                                LocalDate date = picker.getValue();
                                                if (date == null
                                                        || !date.isAfter(LocalDate.now())) {
                                                    throw new IllegalArgumentException(
                                                            "El vencimiento debe ser futuro");
                                                }
                                                var old = getTableView().getItems().get(getIndex());
                                                int index = lines.indexOf(old);
                                                if (index >= 0) {
                                                    lines.set(
                                                            index,
                                                            new PurchaseItem(
                                                                    old.productId(),
                                                                    old.lot(),
                                                                    date,
                                                                    old.quantity(),
                                                                    old.cost()));
                                                    dirty = true;
                                                    render();
                                                }
                                            } catch (Exception ex) {
                                                Views.error(ex);
                                                render();
                                            }
                                        });
                            }

                            protected void updateItem(LocalDate value, boolean empty) {
                                super.updateItem(value, empty);
                                updating = true;
                                picker.setValue(empty ? null : value);
                                setGraphic(empty ? null : picker);
                                updating = false;
                            }
                        });
        table.getColumns().add(col);
    }

    private void editable(
            String name,
            java.util.function.Function<PurchaseItem, String> value,
            java.util.function.BiFunction<PurchaseItem, String, PurchaseItem> change) {
        TableColumn<PurchaseItem, String> col = new TableColumn<>(name);
        col.setCellValueFactory(
                x -> new javafx.beans.property.ReadOnlyStringWrapper(value.apply(x.getValue())));
        col.setCellFactory(c -> Views.editCell());
        col.setOnEditCommit(
                e -> {
                    try {
                        if (locked()) {
                            render();
                            return;
                        }
                        var next = change.apply(e.getRowValue(), e.getNewValue());
                        if (next.lot().isBlank()
                                || !next.expiry().isAfter(LocalDate.now())
                                || lines.stream()
                                        .anyMatch(
                                                l ->
                                                        l != e.getRowValue()
                                                                && l.productId() == next.productId()
                                                                && l.lot().equals(next.lot()))) {
                            throw new IllegalArgumentException(
                                    "Revisa lote, duplicados y vencimiento futuro");
                        }
                        if (next.quantity() < 1 || next.cost().signum() < 0) {
                            throw new IllegalArgumentException("Cantidad o costo inválido");
                        }
                        lines.set(lines.indexOf(e.getRowValue()), next);
                        dirty = true;
                        render();
                    } catch (Exception ex) {
                        render();
                        Views.error(ex);
                    }
                });
        table.getColumns().add(col);
    }

    private void render() {
        supplier.setDisable(pending != null || busy);
        newProduct.setDisable(pending != null || busy || !user.role().equals("ADMIN"));
        table.getItems().setAll(lines);
        table.refresh();
        long units = lines.stream().mapToLong(PurchaseItem::quantity).sum();
        BigDecimal total =
                lines.stream()
                        .map(p -> p.cost().multiply(BigDecimal.valueOf(p.quantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setText(
                lines.size() + " líneas · " + units + " unidades · Total " + Views.money(total));
    }

    private void loadDraft() {
        busy = true;
        editor.setDisable(true);
        Views.async(
                () -> drafts.load(user),
                saved -> {
                    busy = false;
                    editor.setDisable(false);
                    saved.ifPresent(
                            d -> {
                                lines.clear();
                                lines.addAll(d.items());
                                names.putAll(d.names());
                                pending = d.request();
                                if (d.supplier() != null) {
                                    supplier.select(
                                            new DesktopData.Option(d.supplier(), d.supplierName()));
                                }
                                status.setText(
                                        pending == null
                                                ? "Borrador recuperado"
                                                : "Compra pendiente recuperada: pulsa Registrar recepción para comprobarla");
                            });
                    dirty = false;
                    render();
                    search();
                    loadingDraft = false;
                    if (requestedCode != null) {
                        String code = requestedCode;
                        requestedCode = null;
                        prepareProduct(code);
                    }
                },
                ex -> {
                    busy = false;
                    loadingDraft = false;
                    requestedCode = null;
                    editor.setDisable(false);
                    status.setText("No se pudo recuperar el borrador: " + ex.getMessage());
                });
    }

    private void save(boolean submit) {
        if (busy) {
            return;
        }
        Long provider = supplier.id();
        List<PurchaseItem> snapshot = List.copyOf(lines);
        UUID key = pending;
        busy = true;
        editor.setDisable(true);
        Views.async(
                () -> {
                    drafts.save(user, provider, key, snapshot);
                    return null;
                },
                x -> {
                    busy = false;
                    editor.setDisable(false);
                    dirty = false;
                    status.setText("Borrador guardado");
                    if (submit) {
                        submit(provider, snapshot, key);
                    }
                },
                ex -> {
                    busy = false;
                    editor.setDisable(false);
                    Views.error(ex);
                });
    }

    private void commit() {
        if (busy || lines.isEmpty()) {
            return;
        }
        if (pending == null) {
            if (supplier.id() == null) {
                Views.error(new IllegalArgumentException("Selecciona el proveedor"));
                return;
            }
            if (lines.stream().anyMatch(p -> !p.expiry().isAfter(LocalDate.now()))) {
                Views.error(
                        new IllegalArgumentException("Revisa los vencimientos antes de recibir"));
                return;
            }
            if (!Views.confirm(
                    "Registrar recepción de " + lines.size() + " líneas. " + summary.getText())) {
                return;
            }
            pending = UUID.randomUUID();
        }
        save(true);
    }

    private void submit(Long provider, List<PurchaseItem> snapshot, UUID key) {
        busy = true;
        editor.setDisable(true);
        Views.async(
                () -> {
                    var request = new PurchaseRecovery.Pending(key, provider, snapshot);
                    recovery.save(user, request);
                    long id = recovery.resolve(user, key);
                    recovery.acknowledge(user, key, false);
                    drafts.clear(user);
                    return id;
                },
                id -> {
                    busy = false;
                    editor.setDisable(false);
                    pending = null;
                    lines.clear();
                    dirty = false;
                    render();
                    status.setText(
                            "Compra #" + id + " recibida. El inventario ya fue actualizado.");
                },
                ex -> {
                    busy = false;
                    editor.setDisable(false);
                    supplier.setDisable(true);
                    status.setText(
                            "Resultado pendiente. Reintenta la misma compra o revisa Pendientes. "
                                    + ex.getMessage());
                });
    }

    private void clear() {
        if (busy || !Views.confirm("¿Descartar este borrador de compra?")) {
            return;
        }
        busy = true;
        editor.setDisable(true);
        UUID key = pending;
        Views.async(
                () -> {
                    if (key != null) {
                        recovery.cancelOrDiscard(user, key);
                    }
                    drafts.clear(user);
                    return null;
                },
                x -> {
                    busy = false;
                    editor.setDisable(false);
                    pending = null;
                    lines.clear();
                    dirty = false;
                    render();
                    status.setText("Borrador descartado");
                },
                ex -> {
                    busy = false;
                    editor.setDisable(false);
                    Views.error(ex);
                });
    }

    public void resolvedElsewhere(String kind, UUID id) {
        if (kind.equals("purchase") && id.equals(pending)) {
            pending = null;
            lines.clear();
            dirty = false;
            render();
            Views.async(
                    () -> {
                        drafts.clear(user);
                        return null;
                    },
                    x -> status.setText("Operación resuelta desde Pendientes"),
                    Views::error);
        }
    }

    public boolean isBusy() {
        return busy;
    }

    public void reload() {
        search();
    }

    public boolean hasDraft() {
        return pending == null && dirty;
    }

    private void createProduct() {
        if (locked() || !user.role().equals("ADMIN")) {
            return;
        }
        busy = true;
        editor.setDisable(true);
        Views.async(
                inventory::catalog,
                catalog -> {
                    busy = false;
                    editor.setDisable(false);
                    ProductEditor.open(
                            getScene().getWindow(),
                            inventory,
                            user,
                            null,
                            null,
                            catalog,
                            (Long id) -> prepare(() -> data.product(id)));
                },
                ex -> {
                    busy = false;
                    editor.setDisable(false);
                    Views.error(ex);
                });
    }

    public void prepareProduct(String code) {
        if (loadingDraft) {
            requestedCode = code;
            return;
        }
        prepare(() -> data.productByCode(code));
    }

    private void prepare(
            java.util.concurrent.Callable<java.util.Optional<DesktopData.Product>> lookup) {
        if (locked()) {
            return;
        }
        busy = true;
        editor.setDisable(true);
        ++generation;
        Views.async(
                lookup,
                found -> {
                    busy = false;
                    editor.setDisable(false);
                    var product = found.filter(DesktopData.Product::active);
                    if (product.isEmpty()) {
                        status.setText(
                                "El producto ya no está activo o disponible en el catálogo.");
                        return;
                    }
                    var p = product.get();
                    search.setText(p.code());
                    results.getItems().setAll(p);
                    results.getSelectionModel().select(p);
                    int suggested =
                            (int)
                                    Math.min(
                                            100_000_000L,
                                            Math.max(1L, (long) p.minimum() + 1 - p.available()));
                    add(p, suggested);
                },
                ex -> {
                    busy = false;
                    editor.setDisable(false);
                    Views.error(ex);
                });
    }
}
