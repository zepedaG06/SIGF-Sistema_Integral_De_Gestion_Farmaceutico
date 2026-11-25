package org.sigf;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.DesktopData;
import org.sigf.services.InventoryService;
import org.sigf.services.Operations;
import org.sigf.services.PharmacyProfileService;
import org.sigf.ui.Workspace;

/** Prueba de los controles visibles; utiliza únicamente el clúster desechable. */
@EnabledIfEnvironmentVariable(named = "SIGF_CHECKOUT_GUI_TEST", matches = "true")
class CheckoutGuiTest {
    private static final AtomicReference<Stage> stage = new AtomicReference<>();

    static void fx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable e) {
                        failure.set(e);
                    } finally {
                        done.countDown();
                    }
                });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    static void until(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 150; i++) {
            AtomicBoolean yes = new AtomicBoolean();
            fx(() -> yes.set(condition.getAsBoolean()));
            if (yes.get()) {
                return;
            }
            Thread.sleep(100);
        }
        snapshot("fallo");
        fx(
                () -> {
                    for (Window w : Window.getWindows()) {
                        if (w.isShowing() && w.getScene() != null) {
                            System.out.println(
                                    "Controles visibles: "
                                            + activeRoot(w).lookupAll(".button").stream()
                                                    .filter(n -> n instanceof Button)
                                                    .map(
                                                            n ->
                                                                    ((Button) n).getText()
                                                                            + " [deshabilitado="
                                                                            + n.isDisabled()
                                                                            + "]")
                                                    .toList());
                        }
                    }
                });
        fail("No se cumplió la condición de interfaz");
    }

    static Parent activeRoot(Window window) {
        if (window == stage.get()
                && window.getScene().getRoot() instanceof BorderPane root
                && root.getCenter() instanceof TabPane main) {
            Node content = main.getSelectionModel().getSelectedItem().getContent();
            if (content instanceof TabPane nested) {
                content = nested.getSelectionModel().getSelectedItem().getContent();
            }
            return (Parent) content;
        }
        return window.getScene().getRoot();
    }

    static Node find(String id) throws Exception {
        AtomicReference<Node> out = new AtomicReference<>();
        until(
                () -> {
                    for (Window w : Window.getWindows()) {
                        if (w.isShowing() && w.getScene() != null) {
                            Node node = activeRoot(w).lookup(id);
                            if (node != null) {
                                out.set(node);
                                return true;
                            }
                        }
                    }
                    return false;
                });
        return out.get();
    }

    static Button button(String text) throws Exception {
        AtomicReference<Button> out = new AtomicReference<>();
        until(
                () -> {
                    for (Window w : Window.getWindows()) {
                        if (w.isShowing() && w.getScene() != null) {
                            for (Node n : activeRoot(w).lookupAll(".button")) {
                                if (n instanceof Button b
                                        && text.equals(b.getText())
                                        && !b.isDisabled()) {
                                    out.set(b);
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                });
        return out.get();
    }

    static void fire(String text) throws Exception {
        System.out.println("Acción de interfaz: " + text);
        Button b = button(text);
        Platform.runLater(b::fire);
    }

    static void tab(String group, String child) throws Exception {
        fx(
                () -> {
                    TabPane main =
                            (TabPane) ((BorderPane) stage.get().getScene().getRoot()).getCenter();
                    Tab selected =
                            main.getTabs().stream()
                                    .filter(t -> t.getText().equals(group))
                                    .findFirst()
                                    .orElseThrow();
                    main.getSelectionModel().select(selected);
                    if (selected.getContent() instanceof TabPane nested) {
                        nested.getSelectionModel()
                                .select(
                                        nested.getTabs().stream()
                                                .filter(t -> t.getText().equals(child))
                                                .findFirst()
                                                .orElseThrow());
                    }
                });
        Thread.sleep(450);
    }

    static void snapshot(String name) throws Exception {
        Thread.sleep(450);
        fx(
                () -> {
                    try {
                        Scene scene = stage.get().getScene();
                        scene.getRoot().applyCss();
                        scene.getRoot().layout();
                        var image = scene.snapshot(null);
                        var out =
                                new java.awt.image.BufferedImage(
                                        (int) image.getWidth(),
                                        (int) image.getHeight(),
                                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < image.getHeight(); y++) {
                            for (int x = 0; x < image.getWidth(); x++) {
                                out.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                            }
                        }
                        javax.imageio.ImageIO.write(
                                out,
                                "png",
                                new java.io.File("target-v031/redesign-" + name + ".png"));
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }

    static long scalar(Database db, String sql) throws Exception {
        try (var c = db.connection();
                var q = c.createStatement();
                var r = q.executeQuery(sql)) {
            r.next();
            return r.getLong(1);
        }
    }

    static void databaseUntil(Database db, String sql, long expected) throws Exception {
        for (int i = 0; i < 150; i++) {
            if (scalar(db, sql) == expected) {
                return;
            }
            Thread.sleep(100);
        }
        assertEquals(expected, scalar(db, sql));
    }

    @Test
    void desktopWorkflowsSaveDraftReceiveSellCashAndShowReports() throws Exception {
        assertTrue(System.getenv("SIGF_DB_URL").contains(":55439/"));
        try (Database db = new Database()) {
            Security auth = new Security(db);
            auth.bootstrap("ui_admin", "contraseñaPrueba123");
            var admin = auth.login("ui_admin", "contraseñaPrueba123");
            InventoryService inventory = new InventoryService(db);
            Operations ops = new Operations(db);
            for (int i = 1; i <= 5; i++) {
                inventory.saveDetails(
                        admin,
                        null,
                        new InventoryService.ProductDetails(
                                "UI-" + i,
                                "789000" + i,
                                "Producto de prueba " + i,
                                "Categoría de prueba",
                                "",
                                "Caja de 10",
                                "Laboratorio de prueba",
                                new BigDecimal("5.00"),
                                new BigDecimal("10.00"),
                                2,
                                true,
                                i == 1 ? new BigDecimal("0.15") : BigDecimal.ZERO));
                long id = inventory.products("UI-" + i).getFirst().id();
                inventory.receive(
                        admin,
                        id,
                        "INICIAL-" + i,
                        LocalDate.now().plusDays(i == 2 ? 10 : 90),
                        i == 3 ? 1 : 5,
                        new BigDecimal("5.00"));
            }
            ops.contact(admin, "supplier", null, "Proveedor de prueba", "22222222", true);
            ops.customer(admin, null, "Cliente de prueba", "88888888", "");
            new PharmacyProfileService(db)
                    .save(
                            admin,
                            new PharmacyProfileService.Profile(
                                    "Farmacia de prueba",
                                    "Titular de prueba",
                                    "J0000000000000",
                                    "Managua",
                                    "22222222",
                                    "prueba@example.test",
                                    null));
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(
                    () -> {
                        Stage w = new Stage();
                        Scene scene = new Scene(new Pane(), 1420, 960);
                        scene.getStylesheets()
                                .add(
                                        org.sigf.app.Main.class
                                                .getResource("/css/style.css")
                                                .toExternalForm());
                        w.setScene(scene);
                        new Workspace(w, db, admin).show();
                        w.show();
                        stage.set(w);
                        ready.countDown();
                    });
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            Thread.sleep(900);
            snapshot("inicio");
            tab("Inventario", "Productos");
            TableView<?> catalog = (TableView<?>) find(".table-view");
            until(() -> !catalog.getItems().isEmpty());
            fx(() -> catalog.getSelectionModel().selectFirst());
            snapshot("inventario");
            fire("Nuevo producto");
            TextField code = (TextField) find("#productCode"),
                    name = (TextField) find("#productName"),
                    price = (TextField) find("#productPrice");
            ComboBox<?> vat = (ComboBox<?>) find("#productTax");
            fx(
                    () -> {
                        code.setText("UI-EXTRA");
                        name.setText("Producto creado desde el formulario");
                        price.setText("12.50");
                        vat.getSelectionModel().select(1);
                    });
            fire("Guardar producto");
            databaseUntil(db, "select count(*) from product where code='UI-EXTRA'", 1);

            tab("Compras", "Nueva compra");
            Node supplier = find("#purchaseSupplier");
            until(
                    () -> {
                        ComboBox<?> c = (ComboBox<?>) ((Parent) supplier).lookup(".combo-box");
                        return c != null && !c.getItems().isEmpty();
                    });
            fx(
                    () ->
                            ((ComboBox<?>) ((Parent) supplier).lookup(".combo-box"))
                                    .getSelectionModel()
                                    .selectFirst());
            for (int i = 1; i <= 2; i++) {
                TextField q = (TextField) find("#purchaseSearch");
                final int n = i;
                fx(() -> q.setText("UI-" + n));
                TableView<?> products = (TableView<?>) find("#purchaseProducts");
                until(
                        () ->
                                products.getItems().size() == 1
                                        && ((DesktopData.Product) products.getItems().getFirst())
                                                .code()
                                                .equals("UI-" + n));
                fire("Agregar");
                TextField lot = (TextField) find("#formField0");
                DatePicker expiry = (DatePicker) find("#formField1");
                Spinner<?> quantity = (Spinner<?>) find("#formField2");
                fx(
                        () -> {
                            lot.setText("RECEPCION-" + n);
                            expiry.setValue(LocalDate.now().plusDays(120));
                            quantity.getEditor().setText("2");
                        });
                fire("OK");
                TableView<?> draft = (TableView<?>) find("#purchaseDraft");
                until(() -> draft.getItems().size() == n);
            }
            fire("Guardar borrador");
            databaseUntil(db, "select count(*) from purchase_draft_line", 2);
            snapshot("compras");
            // Simula reabrir la sesión: el borrador procede de PostgreSQL, no de los controles.
            fx(() -> new Workspace(stage.get(), db, admin).show());
            tab("Compras", "Nueva compra");
            TableView<?> restored = (TableView<?>) find("#purchaseDraft");
            until(() -> restored.getItems().size() == 2);
            fire("Registrar recepción");
            fire("OK");
            databaseUntil(
                    db,
                    "select sum(quantity) from batch b join product p on p.id=b.product_id where p.code='UI-1'",
                    7);
            assertEquals(0, scalar(db, "select count(*) from purchase_draft_line"));
            tab("Ventas", "Punto de venta");
            TextField scan = (TextField) find("#saleSearch");
            fx(
                    () -> {
                        scan.setText("7890001");
                        scan.fireEvent(new javafx.event.ActionEvent());
                    });
            TableView<?> cart = (TableView<?>) find("#saleCart");
            until(() -> cart.getItems().size() == 1);
            until(
                    () ->
                            cart.lookupAll(".button").stream()
                                    .anyMatch(
                                            n -> n instanceof Button b && b.getText().equals("+")));
            fx(
                    () -> {
                        Button plus =
                                cart.lookupAll(".button").stream()
                                        .filter(
                                                n ->
                                                        n instanceof Button b
                                                                && b.getText().equals("+"))
                                        .map(n -> (Button) n)
                                        .findFirst()
                                        .orElseThrow();
                        plus.fire();
                    });
            until(() -> "2".equals(cart.getColumns().get(1).getCellData(0)));
            until(
                    () ->
                            cart.lookupAll(".button").stream()
                                    .anyMatch(
                                            n -> n instanceof Button b && b.getText().equals("−")));
            fx(
                    () ->
                            cart.lookupAll(".button").stream()
                                    .filter(n -> n instanceof Button b && b.getText().equals("−"))
                                    .map(n -> (Button) n)
                                    .findFirst()
                                    .orElseThrow()
                                    .fire());
            until(() -> "1".equals(cart.getColumns().get(1).getCellData(0)));
            TextField cash = (TextField) find("#cashReceived");
            fx(() -> cash.setText("20.00"));
            snapshot("ventas");
            Platform.runLater(
                    () ->
                            activeRoot(stage.get())
                                    .fireEvent(
                                            new javafx.scene.input.KeyEvent(
                                                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                                                    "",
                                                    "",
                                                    javafx.scene.input.KeyCode.ENTER,
                                                    false,
                                                    true,
                                                    false,
                                                    false)));
            fire("OK");
            databaseUntil(db, "select count(*) from sale", 1);
            databaseUntil(
                    db,
                    "select sum(quantity) from batch b join product p on p.id=b.product_id where p.code='UI-1'",
                    6);
            until(() -> cart.getItems().isEmpty());
            try (var c = db.connection();
                    var q = c.createStatement();
                    var r =
                            q.executeQuery(
                                    "select amount,received_amount,change_amount from payment")) {
                assertTrue(r.next());
                assertEquals(new BigDecimal("11.50"), r.getBigDecimal(1));
                assertEquals(new BigDecimal("20.00"), r.getBigDecimal(2));
                assertEquals(new BigDecimal("8.50"), r.getBigDecimal(3));
            }
            fire("Ver último comprobante");
            find(".text-area");
            fire("Cerrar");
            tab("Reportes", "Reportes");
            ComboBox<?> type = (ComboBox<?>) find("#reportType");
            fx(() -> type.getSelectionModel().select(1));
            fire("Consultar");
            Thread.sleep(500);
            snapshot("reportes");
            tab("Administración", "Configuración");
            snapshot("configuracion");
            fire("Operación");
            Thread.sleep(300);
            fire("Documentos internos");
            Thread.sleep(300);
            fire("Conexión");
            Thread.sleep(500);
            assertEquals(1, scalar(db, "select count(*) from sale"));
            tab("Ventas", "Historial de ventas");
            TableView<?> history = (TableView<?>) find("#operationalTable");
            until(() -> !history.getItems().isEmpty());
            fx(() -> history.getSelectionModel().selectFirst());
            fire("Ver comprobante");
            find(".text-area");
            fire("Cerrar");
            tab("Inventario", "Lotes");
            TableView<?> lots = (TableView<?>) find("#operationalTable");
            until(() -> !lots.getItems().isEmpty());
            fx(() -> lots.getSelectionModel().selectFirst());
            fire("Ajustar existencias");
            fire("Cancel");
            fx(
                    () -> {
                        stage.get().setWidth(1150);
                        stage.get().setHeight(760);
                    });
            tab("Ventas", "Punto de venta");
            snapshot("ventas-1150");
            fx(() -> stage.get().close());
            Platform.exit();
        }
    }
}
