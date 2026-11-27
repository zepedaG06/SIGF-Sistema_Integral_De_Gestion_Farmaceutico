package org.sigf.ui;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.sigf.config.Database;
import org.sigf.domain.ReportGrid;
import org.sigf.repositories.ReportRepository;
import org.sigf.security.Security;
import org.sigf.services.DesktopData;
import org.sigf.services.Operations;
import org.sigf.services.PurchaseRecovery;
import org.sigf.services.SaleRecovery;

/** Historiales, contactos, usuarios y operaciones sobre lotes seleccionados. */
public final class OperationalPane extends VBox {
    private final Database db;
    private final Security.User user;
    private final String mode;
    private final Operations ops;
    private final DesktopData data;
    private final ReportRepository reports;
    private final SaleRecovery sales;
    private final PurchaseRecovery purchases;
    private final java.util.function.BiConsumer<String, UUID> onResolved;
    private final Runnable sessionChanged;
    private final TableView<List<String>> table = Views.table("Sin registros para estos filtros");
    private final Label status = new Label(), pageLabel = new Label();
    private final TextField search = new TextField();
    private final ComboBox<String> batchState = new ComboBox<>();
    private final DatePicker from = new DatePicker(LocalDate.now().withDayOfMonth(1)),
            to = new DatePicker(LocalDate.now());
    private int page;
    private boolean next, busy, reloadQueued;
    private ReportGrid current;
    private final Button previous, nextButton;
    private Long productFilter;
    private final Label productScope = Views.label("", "status-chip");
    private final Button allProducts =
            Views.button("Todos los productos", "close", this::clearProductFilter, false);

    public OperationalPane(
            Database db,
            Security.User user,
            String mode,
            String title,
            Runnable identity,
            java.util.function.BiConsumer<String, UUID> resolved) {
        this.db = db;
        this.user = user;
        this.mode = mode;
        this.onResolved = resolved;
        this.sessionChanged = identity;
        ops = new Operations(db, user);
        data = new DesktopData(db, user);
        reports = new ReportRepository(db, user);
        sales = new SaleRecovery(db);
        purchases = new PurchaseRecovery(db);
        setSpacing(16);
        getStyleClass().add("workspace");
        table.setId("operationalTable");
        Views.date(from);
        Views.date(to);
        FlowPane actions = new FlowPane(8, 8);
        Button refresh = Views.button("Actualizar", "refresh", this::reload, false);
        actions.getChildren().add(refresh);
        switch (mode) {
            case "suppliers", "customers" -> {
                if (admin()) {
                    actions.getChildren()
                            .addAll(
                                    action("Nuevo", () -> contact(false), true, false),
                                    action("Editar", () -> contact(true), false, true));
                } else if (mode.equals("customers")
                        && user.can(org.sigf.security.Permission.CREATE_CUSTOMER)) {
                    actions.getChildren()
                            .add(
                                    action(
                                            "Nuevo",
                                            () ->
                                                    ContactEditor.create(
                                                            getScene().getWindow(),
                                                            new org.sigf.services.ContactsService(
                                                                    db),
                                                            user,
                                                            "customer",
                                                            "",
                                                            option -> reload()),
                                            true,
                                            false));
                }
                if (!admin()
                        && mode.equals("customers")
                        && user.can(org.sigf.security.Permission.EDIT_CUSTOMER)) {
                    actions.getChildren().add(action("Editar", () -> contact(true), false, true));
                }
                actions.getChildren()
                        .add(
                                action(
                                        mode.equals("suppliers")
                                                ? "Compras del proveedor"
                                                : "Ventas del cliente",
                                        this::contactHistory,
                                        false,
                                        true));
            }
            case "users" -> {
                if (admin()) {
                    actions.getChildren()
                            .addAll(
                                    action("+ Nuevo usuario", () -> account(false), true, false),
                                    action("Editar usuario", () -> account(true), false, true));
                }
            }
            case "batches" -> {
                batchState
                        .getItems()
                        .addAll("Todos", "Disponible", "Por vencer", "Vencido", "Bloqueado");
                batchState.setValue("Todos");
                batchState.setOnAction(
                        e -> {
                            page = 0;
                            reload();
                        });
                actions.getChildren().add(batchState);
                if (user.can(org.sigf.security.Permission.ADJUST_STOCK)) {
                    actions.getChildren()
                            .add(action("Ajustar existencias", this::adjust, true, true));
                }
                if (admin()) {
                    actions.getChildren()
                            .add(action("Bloquear / habilitar", this::block, false, true));
                } else if (user.can(org.sigf.security.Permission.BLOCK_BATCH)) {
                    actions.getChildren().add(action("Bloquear lote", this::block, false, true));
                }
            }
            case "sales" -> {
                actions.getChildren()
                        .add(action("Ver detalle de venta", this::operationDetail, true, true));
                actions.getChildren().add(action("Ver comprobante", this::receipt, true, true));
                if (admin()) {
                    actions.getChildren().add(action("Anular venta", this::voidSale, false, true));
                }
            }
            case "purchases" ->
                    actions.getChildren()
                            .add(action("Ver detalle de compra", this::purchaseDetail, true, true));
            case "pending" ->
                    actions.getChildren()
                            .addAll(
                                    action(
                                            "Comprobar y completar",
                                            () -> pending(false, false),
                                            true,
                                            true),
                                    action(
                                            "Descartar solicitud",
                                            () -> pending(false, true),
                                            false,
                                            true));
            case "purchase_pending" ->
                    actions.getChildren()
                            .addAll(
                                    action(
                                            "Comprobar y completar",
                                            () -> pending(true, false),
                                            true,
                                            true),
                                    action(
                                            "Descartar solicitud",
                                            () -> pending(true, true),
                                            false,
                                            true));
            case "payments" -> {}
            case "movements" -> {
                actions.getChildren()
                        .add(
                                action(
                                        "Ver detalle del movimiento",
                                        this::operationDetail,
                                        false,
                                        true));
                productScope.setId("movementProductScope");
                allProducts.setId("movementClearProduct");
                allProducts.setVisible(false);
                allProducts.setManaged(false);
                actions.getChildren().addAll(productScope, allProducts);
            }
            default -> throw new IllegalArgumentException("Módulo desconocido: " + mode);
        }
        search.setPromptText("Buscar en todos los registros");
        search.setPrefWidth(280);
        search.setOnAction(
                e -> {
                    page = 0;
                    reload();
                });
        Button find =
                Views.button(
                        "Buscar",
                        "search",
                        () -> {
                            page = 0;
                            reload();
                        },
                        false);
        FlowPane filters = new FlowPane(10, 10, Views.searchBox(search, "search"), find);
        if (dated()) {
            Button today =
                    Views.button(
                            "Hoy",
                            "calendar",
                            () -> {
                                from.setValue(LocalDate.now());
                                to.setValue(LocalDate.now());
                                page = 0;
                                reload();
                            },
                            false);
            filters.getChildren().addAll(new Label("Desde"), from, new Label("Hasta"), to, today);
        }
        previous =
                Views.button(
                        "Anterior",
                        "chevron-left",
                        () -> {
                            if (page > 0) {
                                page--;
                                reload();
                            }
                        },
                        false);
        nextButton =
                Views.button(
                        "Siguiente",
                        "chevron-right",
                        () -> {
                            if (next) {
                                page++;
                                reload();
                            }
                        },
                        false);
        HBox paging = new HBox(10, previous, pageLabel, nextButton);
        VBox card = Views.card("", actions, filters, table, paging);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(card, Priority.ALWAYS);
        getChildren()
                .addAll(
                        Views.headerWithIcon(
                                title,
                                Set.of("pending", "purchase_pending").contains(mode)
                                        ? "Recupera un resultado incierto sin duplicar la operación"
                                        : "Selecciona un registro para consultar sus acciones",
                                switch (mode) {
                                    case "sales", "pending", "payments" -> "receipt";
                                    case "purchases", "purchase_pending" -> "Compras";
                                    case "suppliers", "customers", "users" -> "Contactos";
                                    case "movements" -> "activity";
                                    default -> "Inventario";
                                }),
                        card,
                        status);
        reload();
    }

    private boolean admin() {
        return user.role().equals("ADMIN");
    }

    private boolean dated() {
        return Set.of("sales", "purchases", "movements", "payments").contains(mode);
    }

    private Button action(String text, Runnable work, boolean primary, boolean selection) {
        String icon =
                switch (text) {
                    case "Nuevo", "+ Nuevo usuario" -> "plus";
                    case "Editar", "Editar usuario" -> "edit";
                    case "Anular venta", "Descartar solicitud" -> "close";
                    case "Ajustar existencias" -> "sliders";
                    case "Bloquear / habilitar" -> "lock";
                    case "Comprobar y completar" -> "refresh";
                    case "Compras del proveedor" -> "Compras";
                    default -> "receipt";
                };
        Button b = Views.button(text, icon, work, primary);
        if (selection) {
            b.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        }
        return b;
    }

    private <T> void run(Callable<T> work, java.util.function.Consumer<T> done) {
        if (busy) {
            return;
        }
        busy = true;
        setDisable(true);
        status.setText("Procesando…");
        Views.async(
                work,
                x -> {
                    busy = false;
                    setDisable(false);
                    done.accept(x);
                    if (reloadQueued) {
                        reloadQueued = false;
                        reload();
                    }
                },
                ex -> {
                    busy = false;
                    setDisable(false);
                    status.setText("No se completó: " + ex.getMessage());
                    if (reloadQueued) {
                        reloadQueued = false;
                        reload();
                    }
                });
    }

    public void reload() {
        if (busy) {
            reloadQueued = true;
            return;
        }
        if (dated()) {
            try {
                from.commitValue();
                to.commitValue();
            } catch (Exception ex) {
                status.setText("Fecha inválida. Usa DD/MM/AAAA");
                return;
            }
        }
        String text = search.getText(), state = batchState.getValue();
        LocalDate a = from.getValue(), b = to.getValue();
        int offset = page * 50;
        Long product = productFilter;
        run(
                () ->
                        mode.equals("pending") || mode.equals("purchase_pending")
                                ? data.pending(user, mode.equals("purchase_pending"))
                                : product != null && mode.equals("movements")
                                        ? data.productMovements(product, a, b, text, offset)
                                        : mode.equals("batches")
                                                ? data.lots(text, state, offset)
                                                : dated()
                                                        ? reports.reportSearch(
                                                                mode, a, b, offset, 51, text)
                                                        : reports.searchPage(
                                                                mode, text, offset, 51),
                g -> {
                    boolean pending = mode.equals("pending") || mode.equals("purchase_pending");
                    if (pending && !text.isBlank()) {
                        g =
                                new ReportGrid(
                                        g.columns(),
                                        g.rows().stream()
                                                .filter(
                                                        r ->
                                                                r.stream()
                                                                        .anyMatch(
                                                                                v ->
                                                                                        v.toLowerCase(
                                                                                                        Locale
                                                                                                                .ROOT)
                                                                                                .contains(
                                                                                                        text
                                                                                                                .toLowerCase(
                                                                                                                        Locale
                                                                                                                                .ROOT))))
                                                .toList());
                    }
                    next = !pending && g.rows().size() > 50;
                    current =
                            new ReportGrid(
                                    g.columns(),
                                    pending
                                            ? g.rows()
                                            : g.rows().subList(0, Math.min(50, g.rows().size())));
                    String selectedId =
                            table.getSelectionModel().getSelectedItem() == null
                                    ? null
                                    : table.getSelectionModel().getSelectedItem().getFirst();
                    Views.grid(table, current);
                    if (selectedId != null) {
                        current.rows().stream()
                                .filter(r -> r.getFirst().equals(selectedId))
                                .findFirst()
                                .ifPresent(r -> table.getSelectionModel().select(r));
                    }
                    if (pending && !table.getColumns().isEmpty()) {
                        @SuppressWarnings("unchecked")
                        TableColumn<List<String>, Object> first =
                                (TableColumn<List<String>, Object>)
                                        (TableColumn<?, ?>) table.getColumns().getFirst();
                        first.setCellValueFactory(
                                v ->
                                        new javafx.beans.property.ReadOnlyObjectWrapper<>(
                                                v.getValue()
                                                        .getFirst()
                                                        .substring(0, 8)
                                                        .toUpperCase(Locale.ROOT)));
                    }
                    previous.setDisable(page == 0 || pending);
                    nextButton.setDisable(!next);
                    pageLabel.setText(
                            "Página " + (page + 1) + " · " + current.rows().size() + " registros");
                    status.setText("Datos actualizados");
                });
    }

    public void setFilter(String value) {
        if (value.equals("Por vencer") && mode.equals("batches")) {
            search.clear();
            batchState.setValue(value);
        } else {
            search.setText(value);
        }
        page = 0;
        reload();
    }

    public void setProductFilter(long id) {
        if (!mode.equals("movements") || id < 1) {
            return;
        }
        productFilter = id;
        search.clear();
        page = 0;
        productScope.setText("Producto N.º " + id);
        allProducts.setVisible(true);
        allProducts.setManaged(true);
        Views.async(
                () -> data.product(id),
                product -> {
                    if (java.util.Objects.equals(productFilter, id)) {
                        product.ifPresent(p -> productScope.setText(p.code() + " · " + p.name()));
                    }
                },
                Views::error);
        reload();
    }

    public void clearProductFilter() {
        if (productFilter == null) {
            return;
        }
        productFilter = null;
        productScope.setText("");
        allProducts.setVisible(false);
        allProducts.setManaged(false);
        page = 0;
        reload();
    }

    private List<String> selected() {
        var row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            throw new IllegalArgumentException("Selecciona un registro");
        }
        return row;
    }

    private void contact(boolean edit) {
        try {
            var row = edit ? selected() : null;
            boolean supplier = mode.equals("suppliers");
            String[] labels =
                    supplier
                            ? new String[] {"Nombre", "Teléfono", "Activo true/false"}
                            : new String[] {"Nombre", "Teléfono", "RUC (opcional)"};
            String[] values = {
                edit ? row.get(1) : "",
                edit ? row.get(2) : "",
                supplier ? (edit ? row.get(3) : "true") : (edit ? row.get(3) : "")
            };
            FormControls.save(
                            supplier ? "Proveedor" : "Cliente",
                            labels,
                            values,
                            Set.of(),
                            Map.of(),
                            v -> {
                                Long id = edit ? Long.valueOf(row.getFirst()) : null;
                                if (supplier) {
                                    ops.contact(
                                            user,
                                            "supplier",
                                            id,
                                            v.get(0),
                                            v.get(1),
                                            Boolean.parseBoolean(v.get(2)));
                                } else {
                                    ops.customer(user, id, v.get(0), v.get(1), v.get(2));
                                }
                            })
                    .ifPresent(v -> reload());
        } catch (Exception ex) {
            Views.error(ex);
        }
    }

    private void account(boolean edit) {
        try {
            var row = edit ? selected() : null;
            boolean self = edit && Long.parseLong(row.getFirst()) == user.id();
            if (self
                    && !Views.confirm(
                            "Al guardar su propia cuenta se cerrará la sesión. Guarde primero los carritos y borradores que necesite conservar. ¿Continuar?")) {
                return;
            }
            FormControls.save(
                            "Usuario",
                            new String[] {
                                "Usuario", "Contraseña (vacía conserva)", "Rol", "Activo true/false"
                            },
                            new String[] {
                                edit ? row.get(1) : "",
                                "",
                                edit ? row.get(2) : "CASHIER",
                                edit ? row.get(3) : "true"
                            },
                            Set.of(1),
                            Map.of(),
                            v ->
                                    ops.user(
                                            user,
                                            edit ? Long.valueOf(row.getFirst()) : null,
                                            v.get(0),
                                            v.get(1),
                                            v.get(2),
                                            Boolean.parseBoolean(v.get(3))))
                    .ifPresent(
                            v -> {
                                if (self) {
                                    sessionChanged.run();
                                } else {
                                    reload();
                                }
                            });
        } catch (Exception ex) {
            Views.error(ex);
        }
    }

    private void adjust() {
        try {
            var row = selected();
            long id = Long.parseLong(row.getFirst());
            int current = Integer.parseInt(row.get(5));
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(getScene().getWindow());
            dialog.setTitle("Ajuste de inventario · " + row.get(2));
            dialog.getDialogPane()
                    .getButtonTypes()
                    .addAll(org.sigf.ui.SpanishButtons.OK, org.sigf.ui.SpanishButtons.CANCEL);
            ComboBox<String> kind = new ComboBox<>();
            kind.getItems().addAll("Entrada", "Salida");
            kind.setValue("Salida");
            Spinner<Integer> quantity = new Spinner<>(1, 100000000, 1);
            quantity.setEditable(true);
            TextField reason = new TextField();
            reason.setPromptText("Motivo obligatorio del ajuste");
            Label preview = new Label(), error = new Label();
            Runnable update =
                    () -> {
                        try {
                            long n = Long.parseLong(quantity.getEditor().getText());
                            preview.setText(
                                    "Antes: "
                                            + current
                                            + " → Después: "
                                            + (current
                                                    + (kind.getValue().equals("Salida") ? -n : n))
                                            + " unidades");
                        } catch (Exception ex) {
                            preview.setText("Revisa la cantidad");
                        }
                    };
            quantity.getEditor().textProperty().addListener((o, a, b) -> update.run());
            kind.setOnAction(e -> update.run());
            update.run();
            VBox form =
                    new VBox(
                            12,
                            new Label("Lote " + row.get(3)),
                            kind,
                            quantity,
                            reason,
                            preview,
                            error);
            dialog.getDialogPane().setContent(form);
            Button ok = (Button) dialog.getDialogPane().lookupButton(org.sigf.ui.SpanishButtons.OK);
            ok.setText("Guardar ajuste");
            ok.addEventFilter(
                    javafx.event.ActionEvent.ACTION,
                    e -> {
                        e.consume();
                        try {
                            int n = Integer.parseInt(quantity.getEditor().getText());
                            if (n < 1 || reason.getText().isBlank()) {
                                throw new IllegalArgumentException(
                                        "Cantidad positiva y motivo obligatorios");
                            }
                            int delta = kind.getValue().equals("Salida") ? -n : n;
                            if (current + (long) delta < 0) {
                                throw new IllegalArgumentException(
                                        "La salida supera la existencia del lote");
                            }
                            ok.setDisable(true);
                            form.setDisable(true);
                            dialog.getDialogPane()
                                    .lookupButton(org.sigf.ui.SpanishButtons.CANCEL)
                                    .setDisable(true);
                            Views.async(
                                    () -> {
                                        ops.adjust(user, id, delta, reason.getText());
                                        return null;
                                    },
                                    x -> {
                                        ok.setDisable(false);
                                        dialog.close();
                                        reload();
                                    },
                                    ex -> {
                                        ok.setDisable(false);
                                        form.setDisable(false);
                                        dialog.getDialogPane()
                                                .lookupButton(org.sigf.ui.SpanishButtons.CANCEL)
                                                .setDisable(false);
                                        error.setText(ex.getMessage());
                                    });
                        } catch (Exception ex) {
                            error.setText(ex.getMessage());
                        }
                    });
            dialog.setOnCloseRequest(
                    e -> {
                        if (ok.isDisabled()) {
                            e.consume();
                        }
                    });
            dialog.showAndWait();
        } catch (Exception ex) {
            Views.error(ex);
        }
    }

    private void block() {
        try {
            var row = selected();
            boolean block = !Boolean.parseBoolean(row.get(6));
            if (!block && !admin()) {
                Views.error(
                        new SecurityException(
                                "Solo administración puede habilitar un lote bloqueado."));
                return;
            }
            FormControls.save(
                            (block ? "Bloquear" : "Habilitar") + " lote " + row.get(3),
                            new String[] {"Motivo"},
                            new String[] {""},
                            Set.of(),
                            Map.of(),
                            values ->
                                    new org.sigf.services.StockService(db)
                                            .block(
                                                    user,
                                                    Long.parseLong(row.getFirst()),
                                                    block,
                                                    values.getFirst()))
                    .ifPresent(values -> reload());
        } catch (Exception ex) {
            Views.error(ex);
        }
    }

    private void receipt() {
        try {
            long id = Long.parseLong(selected().getFirst());
            run(
                    () -> ops.receipt(id),
                    text ->
                            DocumentPreview.show(
                                    "Comprobante interno #" + id, text, getScene().getWindow()));
        } catch (Exception ex) {
            Views.error(ex);
        }
    }

    private void voidSale() {
        try {
            long id = Long.parseLong(selected().getFirst());
            FormControls.save(
                            "Anular venta #" + id,
                            new String[] {"Motivo"},
                            new String[] {""},
                            Set.of(),
                            Map.of(),
                            v -> ops.voidSale(user, id, v.getFirst()))
                    .ifPresent(v -> reload());
        } catch (Exception ex) {
            Views.error(ex);
        }
    }

    private void operationDetail() {
        try {
            long id = Long.parseLong(selected().getFirst());
            run(
                    () ->
                            new org.sigf.repositories.OperationDetailRepository(db)
                                    .load(user, mode, id),
                    detail -> {
                        Dialog<ButtonType> dialog = new Dialog<>();
                        dialog.initOwner(getScene().getWindow());
                        dialog.setTitle(
                                "Detalle de "
                                        + (mode.equals("sales")
                                                ? "venta"
                                                : mode.equals("purchases")
                                                        ? "compra"
                                                        : "movimiento")
                                        + " #"
                                        + id);
                        dialog.setResizable(true);
                        dialog.getDialogPane().getButtonTypes().add(SpanishButtons.CLOSE);
                        TableView<List<String>> rows = Views.table("Sin líneas");
                        Views.grid(rows, detail.lines());
                        rows.setPrefSize(1000, 350);
                        Label header = Views.label(detail.header(), "muted");
                        header.setMaxWidth(980);
                        VBox content = new VBox(14, header, rows);
                        VBox.setVgrow(rows, Priority.ALWAYS);
                        dialog.getDialogPane().setContent(content);
                        dialog.showAndWait();
                    });
        } catch (Exception error) {
            Views.error(error);
        }
    }

    private void purchaseDetail() {
        try {
            long id = Long.parseLong(selected().getFirst());
            operationDetail();
        } catch (Exception ex) {
            Views.error(ex);
        }
    }

    private void contactHistory() {
        try {
            var row = selected();
            long id = Long.parseLong(row.getFirst());
            run(
                    () -> data.contactHistory(mode.equals("suppliers"), id),
                    g -> showGrid("Historial · " + row.get(1), g));
        } catch (Exception ex) {
            Views.error(ex);
        }
    }

    private void showGrid(String title, ReportGrid grid) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().add(org.sigf.ui.SpanishButtons.CLOSE);
        TableView<List<String>> view = Views.table("Sin documentos asociados");
        Views.grid(view, grid);
        view.setPrefSize(850, 420);
        dialog.getDialogPane().setContent(view);
        dialog.showAndWait();
    }

    private void pending(boolean purchase, boolean cancel) {
        try {
            UUID id = UUID.fromString(selected().getFirst());
            if (cancel
                    && !Views.confirm(
                            "¿Descartar esta solicitud? Una operación que ya se registró debe completarse; no se anulará el documento.")) {
                return;
            }
            run(
                    () -> {
                        if (purchase) {
                            if (cancel) {
                                purchases.acknowledge(user, id, true);
                            } else {
                                purchases.resolve(user, id);
                                purchases.acknowledge(user, id, false);
                            }
                        } else {
                            if (cancel) {
                                sales.acknowledge(user, id, true);
                            } else {
                                sales.resolve(user, id);
                                sales.acknowledge(user, id, false);
                            }
                        }
                        return null;
                    },
                    x -> {
                        onResolved.accept(purchase ? "purchase" : "sale", id);
                        reload();
                    });
        } catch (Exception ex) {
            Views.error(ex);
        }
    }
}
