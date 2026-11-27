package org.sigf;

import static org.junit.jupiter.api.Assertions.*;
import static org.sigf.CheckoutGuiTest.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.*;
import org.sigf.ui.Workspace;

/** Flujos contextuales contra un clúster vacío y desechable, nunca la base operativa. */
@EnabledIfEnvironmentVariable(named = "SIGF_QUICK_ACTIONS_TEST", matches = "true")
class QuickActionsGuiTest {
    private static void click(String id) throws Exception {
        Button button = (Button) find(id);
        until(() -> !button.isDisabled());
        Platform.runLater(button::fire);
    }

    private static void text(String id, String value) throws Exception {
        TextField field = (TextField) find(id);
        fx(() -> field.setText(value));
    }

    private static void cancel(String field) throws Exception {
        Node node = find(field);
        Platform.runLater(
                () ->
                        ((Button)
                                        ((DialogPane) node.getScene().getRoot())
                                                .lookupButton(org.sigf.ui.SpanishButtons.CANCEL))
                                .fire());
    }

    private static void fillLot(String lot, int quantity) throws Exception {
        text("#formField0", lot);
        DatePicker expiry = (DatePicker) find("#formField1");
        Spinner<?> count = (Spinner<?>) find("#formField2");
        fx(
                () -> {
                    expiry.setValue(LocalDate.now().plusDays(60));
                    count.getEditor().setText(Integer.toString(quantity));
                });
        text("#formField3", "2.00");
        fire("OK");
    }

    @Test
    void contextualCreationPreservesOperationsAndRespectsPermissions() throws Exception {
        assertTrue(System.getenv("SIGF_DB_URL").contains(":55439/"));
        try (Database db = new Database()) {
            Security auth = new Security(db);
            auth.bootstrap("quick_admin", "PruebaAislada123!");
            var admin = auth.login("quick_admin", "PruebaAislada123!");
            new AdministrationService(db)
                    .user(admin, null, "quick_cashier", "PruebaCajero123!", "CASHIER", true);
            var cashier = auth.login("quick_cashier", "PruebaCajero123!");
            ContactsService contacts = new ContactsService(db);
            assertThrows(
                    SecurityException.class,
                    () -> contacts.createCustomer(cashier, "Sin permiso", "", ""));
            assertThrows(
                    SecurityException.class,
                    () -> contacts.createSupplier(cashier, "Sin permiso", ""));
            long first = contacts.createCustomer(admin, "Homónimo", "", "");
            long second = contacts.createCustomer(admin, "Homónimo", "", "");
            assertNotEquals(first, second);
            assertTrue(contacts.matches(admin, "customer", "", "").isEmpty());
            InventoryService inventory = new InventoryService(db);
            long product =
                    inventory.saveDetails(
                            admin,
                            null,
                            new InventoryService.ProductDetails(
                                    "Q-001",
                                    "900001",
                                    "Producto para venta",
                                    "Prueba",
                                    "",
                                    "Caja",
                                    "Prueba",
                                    BigDecimal.ONE,
                                    new BigDecimal("10.00"),
                                    1,
                                    true,
                                    BigDecimal.ZERO));
            long low =
                    inventory.saveDetails(
                            admin,
                            null,
                            new InventoryService.ProductDetails(
                                    "Q-LOW",
                                    "900002",
                                    "Producto por reponer",
                                    "Prueba",
                                    "",
                                    "Caja",
                                    "Prueba",
                                    BigDecimal.ONE,
                                    BigDecimal.TEN,
                                    3,
                                    true,
                                    BigDecimal.ZERO));
            inventory.receive(
                    admin, product, "INICIAL", LocalDate.now().plusDays(90), 6, BigDecimal.ONE);
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(
                    () -> {
                        Stage window = new Stage();
                        window.setScene(new Scene(new Pane(), 1150, 760));
                        window.getScene()
                                .getStylesheets()
                                .add(
                                        org.sigf.app.Main.class
                                                .getResource("/css/style.css")
                                                .toExternalForm());
                        stage.set(window);
                        new Workspace(window, db, admin).show();
                        window.show();
                        ready.countDown();
                    });
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            try {
                tab("Ventas", "Punto de venta");
                TextField scan = (TextField) find("#saleSearch");
                fx(
                        () -> {
                            scan.setText("900001");
                            scan.fireEvent(new javafx.event.ActionEvent());
                        });
                TableView<?> cart = (TableView<?>) find("#saleCart");
                until(() -> cart.getItems().size() == 1);
                text("#cashReceived", "20.00");
                Node footer = find("#checkoutFooter");
                Node charge = find("#cartCheckout");
                fx(
                        () -> {
                            var bounds = charge.localToScene(charge.getBoundsInLocal());
                            assertTrue(bounds.getMinY() >= 0);
                            assertTrue(
                                    bounds.getMaxY() <= stage.get().getScene().getHeight(),
                                    "Cobrar debe quedar visible");
                            assertTrue(footer.isVisible());
                        });
                text("#customerSearch", "Cliente desde venta");
                click("#customerCreate");
                text("#contactPhone", "8888-1111");
                text("#contactRuc", "J0000000000001");
                click("#contactSave");
                databaseUntil(
                        db, "select count(*) from customer where name='Cliente desde venta'", 1);
                long client =
                        scalar(db, "select id from customer where name='Cliente desde venta'");
                ComboBox<?> clientChoice = (ComboBox<?>) find("#customerChoice");
                until(
                        () ->
                                clientChoice.getValue() instanceof DesktopData.Option option
                                        && option.id() == client);
                fx(
                        () -> {
                            assertEquals(1, cart.getItems().size());
                            assertEquals(
                                    "20.00",
                                    ((TextField) stage.get().getScene().lookup("#cashReceived"))
                                            .getText());
                        });
                click("#customerCreate");
                text("#contactName", "No guardar cancelado");
                cancel("#contactName");
                databaseUntil(
                        db, "select count(*) from customer where name='No guardar cancelado'", 0);
                click("#customerCreate");
                text("#contactName", "Posible duplicado");
                text("#contactPhone", "88881111");
                click("#contactSave");
                ComboBox<?> matches = (ComboBox<?>) find("#contactMatches");
                until(() -> matches.getItems().size() == 1);
                click("#contactUseExisting");
                databaseUntil(
                        db, "select count(*) from customer where name='Posible duplicado'", 0);
                assertEquals(
                        client,
                        contacts.matches(admin, "customer", "", "j0000000000001").getFirst().id());
                snapshot("acciones-ventas");
                click("#cartCheckout");
                fire("OK");
                databaseUntil(db, "select count(*) from sale where customer_id=" + client, 1);
                until(() -> cart.getItems().isEmpty());

                tab("Compras", "Nueva compra");
                click("#supplierCreate");
                text("#contactName", "Proveedor desde compra");
                text("#contactPhone", "2222-7777");
                click("#contactSave");
                databaseUntil(
                        db, "select count(*) from supplier where name='Proveedor desde compra'", 1);
                ComboBox<?> supplier = (ComboBox<?>) find("#supplierChoice");
                until(
                        () ->
                                supplier.getValue() instanceof DesktopData.Option option
                                        && option.name().equals("Proveedor desde compra"));
                click("#purchaseNewProduct");
                text("#productCode", "Q-NEW");
                text("#productName", "Producto desde compra");
                text("#productPrice", "8.00");
                ComboBox<?> vat = (ComboBox<?>) find("#productTax");
                fx(() -> vat.getSelectionModel().select(1));
                fire("Guardar producto");
                fillLot("COMPRA", 2);
                TableView<?> draft = (TableView<?>) find("#purchaseDraft");
                until(() -> draft.getItems().size() == 1);
                click("#supplierCreate");
                text("#contactName", "Proveedor final");
                click("#contactSave");
                until(
                        () ->
                                supplier.getValue() instanceof DesktopData.Option option
                                        && option.name().equals("Proveedor final"));
                fx(() -> assertEquals(1, draft.getItems().size()));
                fire("Registrar recepción");
                fire("OK");
                databaseUntil(db, "select count(*) from purchase", 2);
                until(() -> draft.getItems().isEmpty());
                long created = scalar(db, "select id from product where code='Q-NEW'");
                assertEquals(2, new DesktopData(db).product(created).orElseThrow().available());

                tab("Inventario", "Productos");
                text("#inventorySearch", "Q-NEW");
                TableView<?> catalog = (TableView<?>) find(".table-view");
                until(
                        () ->
                                catalog.getItems().size() == 1
                                        && catalog.getItems().getFirst()
                                                instanceof DesktopData.Product p
                                        && p.id() == created);
                fx(() -> catalog.getSelectionModel().selectFirst());
                click("#inventoryProductMovements");
                TableView<?> movements = (TableView<?>) find("#operationalTable");
                until(() -> !movements.getItems().isEmpty());
                Label scope = (Label) find("#movementProductScope");
                until(() -> scope.getText().contains("Q-NEW"));
                assertEquals(
                        1,
                        new DesktopData(db)
                                .productMovements(created, LocalDate.now(), LocalDate.now(), "", 0)
                                .rows()
                                .size());
                assertTrue(
                        new DesktopData(db)
                                .productMovements(low, LocalDate.now(), LocalDate.now(), "", 0)
                                .rows()
                                .isEmpty());
                tab("Inventario", "Productos");
                text("#inventorySearch", "Q-LOW");
                until(
                        () ->
                                catalog.getItems().size() == 1
                                        && catalog.getItems().getFirst()
                                                instanceof DesktopData.Product p
                                        && p.id() == low);
                fx(() -> catalog.getSelectionModel().selectFirst());
                click("#inventoryPreparePurchase");
                Spinner<?> suggested = (Spinner<?>) find("#formField2");
                fx(() -> assertEquals("4", suggested.getEditor().getText()));
                cancel("#formField0");
                tab("Inicio", "");
                Button prepare = (Button) find("#dashboardPreparePurchase");
                TableView<?> alerts = (TableView<?>) find("#dashboardLowStock");
                until(() -> !alerts.getItems().isEmpty());
                fx(() -> alerts.getSelectionModel().selectFirst());
                until(() -> !prepare.isDisabled());
                Platform.runLater(prepare::fire);
                find("#formField0");
                cancel("#formField0");
                fx(() -> new Workspace(stage.get(), db, cashier).show());
                tab("Ventas", "Punto de venta");
                Button forbidden = (Button) find("#customerCreate");
                fx(() -> assertFalse(forbidden.isVisible()));
            } finally {
                fx(
                        () -> {
                            stage.get().setOnCloseRequest(null);
                            stage.get().close();
                        });
                Platform.exit();
            }
        }
    }
}
