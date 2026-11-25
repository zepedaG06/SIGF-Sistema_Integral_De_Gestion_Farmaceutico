package org.sigf.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.sigf.config.Database;
import org.sigf.domain.CartItem;
import org.sigf.domain.Receipt;
import org.sigf.security.Security;
import org.sigf.services.ContactsService;
import org.sigf.services.DesktopData;
import org.sigf.services.SaleRecovery;
import org.sigf.services.SalesService;

/** Punto de venta con carrito y cobro permanentes. */
public final class SalesPane extends VBox {
    private record Line(DesktopData.Product product, int quantity) {
        BigDecimal base() {
            return product.price().multiply(BigDecimal.valueOf(quantity));
        }

        BigDecimal tax() {
            return base().multiply(product.tax()).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total() {
            return base().add(tax());
        }
    }

    private final DesktopData data;
    private final SalesService service;
    private final SaleRecovery recovery;
    private final Security.User user;
    private final TextField search = new TextField(), received = new TextField();
    private final TableView<DesktopData.Product> products =
            Views.table("Busca un producto por nombre o escanea su código");
    private final TableView<Line> cart = Views.table("Agrega productos para comenzar");
    private final List<Line> lines = new ArrayList<>();
    private final ComboBox<String> payment = new ComboBox<>();
    private final EntityPicker customer;
    private final Label subtotal = new Label(),
            tax = new Label(),
            total = new Label(),
            change = new Label(),
            status = new Label();
    private final Button charge;
    private final VBox paymentBox;
    private SaleRecovery.Pending pending;
    private boolean busy;
    private int request;
    private long lastSale;
    private int page;
    private boolean hasNext;

    public SalesPane(Database db, Security.User user) {
        this.user = user;
        data = new DesktopData(db);
        service = new SalesService(db);
        recovery = new SaleRecovery(db);
        customer = new EntityPicker(data, "customer", "Buscar cliente (opcional)");
        customer.setId("saleCustomer");
        if (user.role().equals("ADMIN")) {
            customer.allowCreate(
                    "Nuevo cliente",
                    text -> {
                        if (!locked()) {
                            ContactEditor.create(
                                    getScene().getWindow(),
                                    new ContactsService(db),
                                    user,
                                    "customer",
                                    text,
                                    option -> {
                                        customer.select(option);
                                        status.setText("Cliente seleccionado: " + option.name());
                                    });
                        }
                    });
        }
        setSpacing(16);
        getStyleClass().add("workspace");
        search.setId("saleSearch");
        search.setPromptText("Buscar nombre o escanear código · Enter para agregar");
        search.setOnAction(e -> search(true));
        PauseTransition debounce = new PauseTransition(Duration.millis(250));
        search.setOnAction(
                e -> {
                    debounce.stop();
                    search(true);
                });
        search.textProperty()
                .addListener(
                        (o, a, b) -> {
                            page = 0;
                            debounce.setOnFinished(e -> search(false));
                            debounce.playFromStart();
                        });
        products.setId("saleProducts");
        cart.setId("saleCart");
        Views.column(products, "Código", DesktopData.Product::code);
        Views.column(products, "Producto / presentación", p -> p.name() + " · " + p.presentation());
        Views.column(products, "Precio final", p -> Views.money(p.finalPrice()));
        Views.column(products, "Disponible", DesktopData.Product::available);
        TableColumn<DesktopData.Product, Void> add = new TableColumn<>("");
        add.setMaxWidth(55);
        add.setCellFactory(
                c ->
                        new TableCell<>() {
                            final Button b =
                                    Views.button(
                                            "+",
                                            () -> add(getTableView().getItems().get(getIndex())),
                                            false);

                            protected void updateItem(Void value, boolean empty) {
                                super.updateItem(value, empty);
                                setGraphic(empty ? null : b);
                            }
                        });
        products.getColumns().add(add);
        Views.widths(products, 1, 3, 1.5, 1.2, .6);
        products.setRowFactory(
                t -> {
                    TableRow<DesktopData.Product> row = new TableRow<>();
                    row.setOnMouseClicked(
                            e -> {
                                if (e.getClickCount() == 2 && !row.isEmpty()) {
                                    add(row.getItem());
                                }
                            });
                    return row;
                });
        HBox paging =
                new HBox(
                        8,
                        Views.button(
                                "Anterior",
                                "chevron-left",
                                () -> {
                                    if (page > 0) {
                                        page--;
                                        search(false);
                                    }
                                },
                                false),
                        Views.button(
                                "Siguiente",
                                "chevron-right",
                                () -> {
                                    if (hasNext) {
                                        page++;
                                        search(false);
                                    }
                                },
                                false));
        VBox catalog =
                Views.cardWithIcon(
                        "Catálogo de productos",
                        "Inventario",
                        Views.searchBox(search, "barcode"),
                        products,
                        paging);
        VBox.setVgrow(products, Priority.ALWAYS);
        Views.column(cart, "Producto", l -> l.product().name());
        TableColumn<Line, String> qty = new TableColumn<>("Cantidad");
        qty.setCellValueFactory(
                x ->
                        new javafx.beans.property.ReadOnlyStringWrapper(
                                Integer.toString(x.getValue().quantity())));
        qty.setCellFactory(
                c ->
                        new TableCell<>() {
                            final TextField number = new TextField();
                            final Button minus = Views.button("−", () -> change(-1), false),
                                    plus = Views.button("+", () -> change(1), false);
                            final HBox box = new HBox(3, minus, number, plus);

                            {
                                number.setPrefWidth(42);
                                number.setMinWidth(34);
                                number.setOnAction(e -> apply());
                                number.focusedProperty()
                                        .addListener(
                                                (o, a, b) -> {
                                                    if (!b) {
                                                        apply();
                                                    }
                                                });
                            }

                            private Line row() {
                                return getIndex() < 0
                                                || getIndex() >= getTableView().getItems().size()
                                        ? null
                                        : getTableView().getItems().get(getIndex());
                            }

                            private void change(int delta) {
                                Line line = row();
                                if (line != null) {
                                    quantity(line, line.quantity() + delta);
                                }
                            }

                            private void apply() {
                                Line line = row();
                                if (line == null
                                        || number.getText()
                                                .equals(Integer.toString(line.quantity()))) {
                                    return;
                                }
                                try {
                                    quantity(line, Integer.parseInt(number.getText()));
                                } catch (Exception ex) {
                                    number.setText(Integer.toString(line.quantity()));
                                    status.setText("Usa una cantidad entera disponible");
                                }
                            }

                            protected void updateItem(String value, boolean empty) {
                                super.updateItem(value, empty);
                                number.setText(empty ? "" : value);
                                setGraphic(empty ? null : box);
                            }
                        });
        qty.setOnEditCommit(
                e -> {
                    try {
                        if (locked()) {
                            throw new IllegalStateException("Resuelve la venta pendiente");
                        }
                        int n = Integer.parseInt(e.getNewValue());
                        if (n < 1 || n > e.getRowValue().product().available()) {
                            throw new IllegalArgumentException(
                                    "Cantidad mayor que la disponibilidad");
                        }
                        lines.set(
                                lines.indexOf(e.getRowValue()),
                                new Line(e.getRowValue().product(), n));
                        render();
                    } catch (Exception ex) {
                        render();
                        Views.error(ex);
                    }
                });
        cart.getColumns().add(qty);
        cart.setEditable(true);
        Views.column(cart, "Precio final", l -> Views.money(l.product().finalPrice()));
        Views.column(cart, "Importe", l -> Views.money(l.total()));
        TableColumn<Line, Void> remove = new TableColumn<>("");
        remove.setMaxWidth(75);
        remove.setCellFactory(
                c ->
                        new TableCell<>() {
                            final Button b =
                                    Views.iconButton(
                                            "Quitar producto",
                                            "delete",
                                            () -> {
                                                if (!locked()) {
                                                    lines.remove(
                                                            getTableView()
                                                                    .getItems()
                                                                    .get(getIndex()));
                                                    render();
                                                }
                                            });

                            protected void updateItem(Void value, boolean empty) {
                                super.updateItem(value, empty);
                                setGraphic(empty ? null : b);
                            }
                        });
        cart.getColumns().add(remove);
        Views.widths(cart, 3, 2, 1.5, 1.5, 1);
        qty.setMinWidth(122);
        cart.setMinHeight(140);
        cart.setPrefHeight(180);
        payment.getItems().addAll("Efectivo", "Tarjeta", "Transferencia");
        payment.setValue("Efectivo");
        payment.setId("salePayment");
        payment.setMaxWidth(Double.MAX_VALUE);
        payment.setOnAction(e -> renderTotals());
        received.setId("cashReceived");
        received.setPromptText("Importe recibido en C$");
        received.textProperty().addListener((o, a, b) -> renderTotals());
        charge = Views.button("Cobrar", "cash", this::checkout, true);
        charge.setId("cartCheckout");
        charge.setMaxWidth(Double.MAX_VALUE);
        charge.getStyleClass().add("charge-button");
        total.getStyleClass().add("sale-total");
        HBox cash =
                new HBox(
                        8,
                        received,
                        Views.button(
                                "Exacto",
                                "check",
                                () -> received.setText(totals().total().toPlainString()),
                                false));
        HBox.setHgrow(received, Priority.ALWAYS);
        paymentBox =
                new VBox(
                        7,
                        new HBox(10, Views.heading("Pago", "field-caption", "card"), payment),
                        cash,
                        change);
        MenuButton more = new MenuButton("Más acciones", UiIcons.icon("more", "action-icon", 20));
        MenuItem clear = new MenuItem("Vaciar carrito", UiIcons.icon("delete", "action-icon", 20));
        clear.setOnAction(e -> clear());
        more.getItems().add(clear);
        Button receipt =
                Views.button(
                        "Ver último comprobante",
                        "receipt",
                        () -> {
                            if (lastSale > 0) {
                                Views.async(
                                        () -> service.receipt(lastSale),
                                        text ->
                                                DocumentPreview.show(
                                                        "Comprobante interno",
                                                        text,
                                                        getScene().getWindow()),
                                        Views::error);
                            }
                        },
                        false);
        receipt.setTooltip(new Tooltip("Se habilita después de registrar una venta"));
        receipt.disableProperty()
                .bind(
                        javafx.beans.binding.Bindings.createBooleanBinding(
                                () -> lastSale == 0, status.textProperty()));
        VBox checkoutBody =
                new VBox(
                        12,
                        customer,
                        cart,
                        subtotal,
                        tax,
                        paymentBox,
                        new FlowPane(8, 8, more, receipt),
                        Views.label(
                                "F2 Buscar · Ctrl/Cmd+Enter Cobrar · doble clic o + para agregar",
                                "muted"));
        ScrollPane checkoutScroll = new ScrollPane(checkoutBody);
        checkoutScroll.setFitToWidth(true);
        checkoutScroll.setMinHeight(60);
        checkoutScroll.setId("checkoutDetails");
        VBox footer = new VBox(8, total, charge);
        footer.setId("checkoutFooter");
        footer.getStyleClass().add("checkout-footer");
        VBox right = Views.cardWithIcon("Venta actual", "Ventas", checkoutScroll, footer);
        VBox.setVgrow(checkoutScroll, Priority.ALWAYS);
        right.getStyleClass().add("checkout-card");
        SplitPane split = new SplitPane(catalog, right);
        split.setDividerPositions(.52);
        Views.responsive(split, "sales");
        VBox.setVgrow(split, Priority.ALWAYS);
        getChildren()
                .addAll(
                        Views.headerWithIcon(
                                "Punto de venta",
                                "Captura rápida y cobro en una sola pantalla",
                                "Ventas"),
                        split,
                        status);
        setOnKeyPressed(
                e -> {
                    if (e.getCode() == KeyCode.F2) {
                        search.requestFocus();
                        e.consume();
                    } else if ((e.isControlDown() || e.isShortcutDown())
                            && e.getCode() == KeyCode.ENTER) {
                        checkout();
                        e.consume();
                    }
                });
        render();
        reload();
    }

    private boolean locked() {
        return busy || pending != null;
    }

    private Receipt totals() {
        BigDecimal sub = BigDecimal.ZERO, iva = BigDecimal.ZERO;
        for (Line l : lines) {
            sub = sub.add(l.base());
            iva = iva.add(l.tax());
        }
        return new Receipt(0, sub, iva, sub.add(iva));
    }

    private void render() {
        cart.getItems().setAll(lines);
        cart.refresh();
        renderTotals();
    }

    private void renderTotals() {
        var t = totals();
        subtotal.setText("Subtotal sin IVA: " + Views.money(t.subtotal()));
        tax.setText("IVA según producto: " + Views.money(t.tax()));
        total.setText("Total: " + Views.money(t.total()));
        boolean cash = payment.getValue().equals("Efectivo");
        received.setDisable(!cash || locked());
        try {
            BigDecimal amount = new BigDecimal(received.getText().trim());
            change.setText(
                    cash
                            ? (amount.compareTo(t.total()) >= 0
                                    ? "Cambio: " + Views.money(amount.subtract(t.total()))
                                    : "Falta: " + Views.money(t.total().subtract(amount)))
                            : "Pago electrónico por el importe exacto");
        } catch (Exception e) {
            change.setText(
                    cash
                            ? "Ingresa el efectivo recibido o pulsa Exacto"
                            : "Pago electrónico por el importe exacto");
        }
        if (charge != null) {
            charge.setText(
                    pending != null
                            ? "Comprobar y completar venta"
                            : "Cobrar " + Views.money(t.total()));
            charge.setDisable(busy || lines.isEmpty());
        }
        if (paymentBox != null) {
            paymentBox.setDisable(locked());
        }
        customer.setDisable(locked());
    }

    private void quantity(Line line, int n) {
        if (locked()) {
            return;
        }
        if (n < 1 || n > line.product().available()) {
            status.setText("La cantidad debe ser positiva y no superar la disponibilidad");
            cart.refresh();
            return;
        }
        int index = lines.indexOf(line);
        if (index >= 0) {
            lines.set(index, new Line(line.product(), n));
            render();
        }
    }

    private void add(DesktopData.Product p) {
        if (locked()) {
            return;
        }
        if (!p.active() || p.available() < 1 || p.tax() == null) {
            status.setText("Este producto no está disponible o no tiene IVA clasificado");
            return;
        }
        int index = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).product().id() == p.id()) {
                index = i;
            }
        }
        int count = index < 0 ? 1 : lines.get(index).quantity() + 1;
        if (count > p.available()) {
            status.setText("No hay más unidades disponibles");
            return;
        }
        if (index < 0) {
            lines.add(new Line(p, count));
        } else {
            lines.set(index, new Line(p, count));
        }
        render();
        search.requestFocus();
    }

    private void search(boolean scan) {
        int version = ++request;
        String text = search.getText();
        Views.async(
                () -> data.products(text, "", "", "Activo", "Disponible", page * 50),
                rows -> {
                    if (version != request) {
                        return;
                    }
                    hasNext = rows.size() > 50;
                    var visible = rows.subList(0, Math.min(50, rows.size()));
                    products.getItems().setAll(visible);
                    if (scan) {
                        var exact =
                                rows.stream()
                                        .filter(
                                                p ->
                                                        p.code().equalsIgnoreCase(text)
                                                                || p.barcode().equals(text))
                                        .findFirst();
                        if (exact.isPresent()) {
                            add(exact.get());
                            search.clear();
                        } else if (rows.size() == 1) {
                            add(rows.getFirst());
                        } else {
                            status.setText("Selecciona un producto de los resultados");
                        }
                    } else {
                        status.setText(
                                "Página " + (page + 1) + " · " + visible.size() + " resultados");
                    }
                },
                ex -> status.setText("No se pudieron cargar productos: " + ex.getMessage()));
    }

    public void reload() {
        search(false);
        if (!locked() && !lines.isEmpty()) {
            refreshCart();
        }
    }

    private void refreshCart() {
        List<Line> snapshot = List.copyOf(lines);
        busy = true;
        renderTotals();
        Views.async(
                () -> {
                    List<Line> fresh = new ArrayList<>();
                    for (Line line : snapshot) {
                        var p =
                                data.product(line.product().id())
                                        .orElseThrow(
                                                () ->
                                                        new IllegalArgumentException(
                                                                "Producto ya no disponible"));
                        if (p.tax() == null) {
                            throw new IllegalArgumentException(
                                    "Clasifica el IVA del producto antes de continuar");
                        }
                        fresh.add(new Line(p, line.quantity()));
                    }
                    return fresh;
                },
                updated -> {
                    busy = false;
                    lines.clear();
                    lines.addAll(updated);
                    render();
                    status.setText("Precios y disponibilidad del carrito actualizados");
                },
                ex -> {
                    busy = false;
                    renderTotals();
                    Views.error(ex);
                });
    }

    public boolean isBusy() {
        return busy;
    }

    private void checkout() {
        if (busy || lines.isEmpty()) {
            return;
        }
        if (pending != null) {
            submit();
            return;
        }
        try {
            Long client = customer.id();
            String method =
                    switch (payment.getValue()) {
                        case "Tarjeta" -> "CARD";
                        case "Transferencia" -> "TRANSFER";
                        default -> "CASH";
                    };
            BigDecimal tendered =
                    method.equals("CASH")
                            ? new BigDecimal(received.getText().trim())
                                    .setScale(2, RoundingMode.UNNECESSARY)
                            : null;
            List<CartItem> items =
                    lines.stream().map(l -> new CartItem(l.product().id(), l.quantity())).toList();
            busy = true;
            renderTotals();
            Views.async(
                    () -> service.quoteDetailed(items),
                    quote -> {
                        busy = false;
                        renderTotals();
                        if (quote.lines().stream()
                                .anyMatch(
                                        q ->
                                                lines.stream()
                                                        .noneMatch(
                                                                l ->
                                                                        l.product().id()
                                                                                        == q
                                                                                                .productId()
                                                                                && l.quantity()
                                                                                        == q
                                                                                                .quantity()
                                                                                && l.product()
                                                                                                .price()
                                                                                                .compareTo(
                                                                                                        q
                                                                                                                .price())
                                                                                        == 0
                                                                                && l.product().tax()
                                                                                        != null
                                                                                && l.product()
                                                                                                .tax()
                                                                                                .compareTo(
                                                                                                        q
                                                                                                                .taxRate())
                                                                                        == 0))) {
                            refreshCart();
                            status.setText(
                                    "Cambió el precio o IVA. Revisa el carrito actualizado antes de cobrar.");
                            return;
                        }
                        if (tendered != null && tendered.compareTo(quote.totals().total()) < 0) {
                            status.setText("El efectivo recibido no cubre el total");
                            return;
                        }
                        if (!Views.confirm(
                                "Registrar venta por "
                                        + Views.money(quote.totals().total())
                                        + ". Comprobante interno sin valor fiscal.")) {
                            return;
                        }
                        pending =
                                new SaleRecovery.Pending(
                                        UUID.randomUUID(),
                                        client,
                                        method,
                                        quote.totals().total(),
                                        items,
                                        quote.lines(),
                                        tendered);
                        submit();
                    },
                    ex -> {
                        busy = false;
                        renderTotals();
                        Views.error(ex);
                    });
        } catch (Exception ex) {
            Views.error(
                    new IllegalArgumentException("Revisa el importe recibido: " + ex.getMessage()));
        }
    }

    private void submit() {
        busy = true;
        renderTotals();
        var request = pending;
        Views.async(
                () -> {
                    recovery.save(user, request);
                    Receipt receipt = recovery.resolve(user, request.id());
                    recovery.acknowledge(user, request.id(), false);
                    return receipt;
                },
                receipt -> {
                    busy = false;
                    pending = null;
                    lines.clear();
                    lastSale = receipt.id();
                    received.clear();
                    customer.clear();
                    render();
                    reload();
                    status.setText(
                            "Venta #"
                                    + lastSale
                                    + " registrada · "
                                    + Views.money(receipt.total())
                                    + " · Puedes comenzar una nueva venta");
                },
                ex -> {
                    busy = false;
                    renderTotals();
                    status.setText(
                            "Resultado pendiente: comprueba la misma operación o revisa Ventas pendientes. "
                                    + ex.getMessage());
                });
    }

    private void clear() {
        if (busy) {
            return;
        }
        if (!Views.confirm("¿Vaciar el carrito?")) {
            return;
        }
        if (pending == null) {
            lines.clear();
            render();
            return;
        }
        busy = true;
        Views.async(
                () -> {
                    recovery.cancelOrDiscard(user, pending.id());
                    return null;
                },
                x -> {
                    busy = false;
                    pending = null;
                    lines.clear();
                    render();
                },
                ex -> {
                    busy = false;
                    renderTotals();
                    Views.error(ex);
                });
    }

    public void resolvedElsewhere(String kind, UUID id) {
        if (kind.equals("sale") && pending != null && pending.id().equals(id)) {
            pending = null;
            lines.clear();
            render();
            status.setText("La operación fue resuelta desde Pendientes");
        }
    }

    public boolean hasDraft() {
        return pending == null && !lines.isEmpty();
    }
}
