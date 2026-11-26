package org.sigf;

import static org.junit.jupiter.api.Assertions.*;
import static org.sigf.CheckoutGuiTest.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.config.Database;
import org.sigf.domain.*;
import org.sigf.security.Security;
import org.sigf.services.*;
import org.sigf.ui.Workspace;

@EnabledIfEnvironmentVariable(named = "SIGF_REVIEW_TEST", matches = "true")
class ReviewRegressionTest {
    @Test
    void retriesLotsScanningAndOwnAccountChangesRemainConsistent() throws Exception {
        assertEquals(
                "jdbc:postgresql://127.0.0.1:55439/sigf_javafx_db", System.getenv("SIGF_DB_URL"));
        try (Database db = new Database()) {
            Security auth = new Security(db);
            auth.bootstrap("review_admin", "RevisionPrueba123!");
            var admin = auth.login("review_admin", "RevisionPrueba123!");
            InventoryService inventory = new InventoryService(db);
            ContactsService contacts = new ContactsService(db);
            long supplier = contacts.createSupplier(admin, "Proveedor A", "");
            long otherSupplier = contacts.createSupplier(admin, "Proveedor B", "");
            long customer = contacts.createCustomer(admin, "Cliente", "", "");
            long product =
                    inventory.saveDetails(
                            admin,
                            null,
                            new InventoryService.ProductDetails(
                                    "REV-1",
                                    "REV-BAR",
                                    "Producto",
                                    "",
                                    "",
                                    "Caja",
                                    "",
                                    BigDecimal.ONE,
                                    BigDecimal.TEN,
                                    1,
                                    true,
                                    BigDecimal.ZERO));
            PurchaseService purchases = new PurchaseService(db);
            LocalDate expiry = LocalDate.now().plusDays(60);
            List<PurchaseItem> lines =
                    List.of(new PurchaseItem(product, " LOTE A ", expiry, 8, BigDecimal.ONE));
            UUID key = UUID.randomUUID();
            long purchase = purchases.purchase(admin, supplier, lines, key);
            assertEquals(purchase, purchases.purchase(admin, supplier, lines, key));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> purchases.purchase(admin, otherSupplier, lines, key));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            purchases.purchase(
                                    admin,
                                    supplier,
                                    List.of(
                                            new PurchaseItem(
                                                    product, "LOTE A", expiry, 9, BigDecimal.ONE)),
                                    key));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            purchases.purchase(
                                    admin,
                                    supplier,
                                    List.of(
                                            new PurchaseItem(
                                                    product, "B", expiry, 1, BigDecimal.ONE),
                                            new PurchaseItem(
                                                    product, " B ", expiry, 1, BigDecimal.ONE)),
                                    UUID.randomUUID()));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            purchases.purchase(
                                    admin,
                                    supplier,
                                    List.of(
                                            new PurchaseItem(
                                                    product, null, expiry, 1, BigDecimal.ONE)),
                                    UUID.randomUUID()));
            PurchaseDraftService drafts = new PurchaseDraftService(db);
            drafts.save(admin, supplier, null, lines);
            assertEquals("LOTE A", drafts.load(admin).orElseThrow().items().getFirst().lot());
            drafts.clear(admin);
            PurchaseRecovery recovery = new PurchaseRecovery(db);
            UUID pendingPurchase = UUID.randomUUID();
            recovery.save(
                    admin,
                    new PurchaseRecovery.Pending(
                            pendingPurchase,
                            supplier,
                            List.of(new PurchaseItem(product, "PEND", expiry, 1, BigDecimal.ONE))));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            purchases.purchase(
                                    admin,
                                    otherSupplier,
                                    List.of(
                                            new PurchaseItem(
                                                    product, "PEND", expiry, 1, BigDecimal.ONE)),
                                    pendingPurchase));
            recovery.cancelOrDiscard(admin, pendingPurchase);
            SalesService sales = new SalesService(db);
            UUID request = UUID.randomUUID();
            List<CartItem> cart = List.of(new CartItem(product, 2));
            Receipt sale = sales.sell(admin, cart, customer, "CASH", request);
            assertEquals(sale.id(), sales.sell(admin, cart, customer, "CASH", request).id());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sales.sell(
                                    admin,
                                    List.of(new CartItem(product, 1)),
                                    customer,
                                    "CASH",
                                    request));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sales.sell(admin, cart, null, "CASH", request));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sales.sell(admin, cart, customer, "CARD", request));
            SaleRecovery salesRecovery = new SaleRecovery(db);
            UUID pending = UUID.randomUUID();
            var quote = sales.quoteDetailed(List.of(new CartItem(product, 1)));
            salesRecovery.save(
                    admin,
                    new SaleRecovery.Pending(
                            pending,
                            customer,
                            "CASH",
                            quote.totals().total(),
                            List.of(new CartItem(product, 1)),
                            quote.lines(),
                            BigDecimal.TEN));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sales.sell(admin, cart, customer, "CASH", pending));
            salesRecovery.cancelOrDiscard(admin, pending);
            assertEquals(6, new DesktopData(db).product(product).orElseThrow().available());
            assertEquals(1, scalar(db, "select count(*) from sale"));
            assertEquals(1, scalar(db, "select count(*) from purchase"));
            long tiny =
                    inventory.saveDetails(
                            admin,
                            null,
                            new InventoryService.ProductDetails(
                                    "TINY",
                                    "",
                                    "Redondeo",
                                    "",
                                    "",
                                    "",
                                    "",
                                    BigDecimal.ZERO,
                                    new BigDecimal("0.03"),
                                    0,
                                    true,
                                    new BigDecimal("0.15")));
            purchases.purchase(
                    admin,
                    supplier,
                    List.of(
                            new PurchaseItem(tiny, "UNO", expiry, 1, BigDecimal.ZERO),
                            new PurchaseItem(tiny, "DOS", expiry, 1, BigDecimal.ZERO)),
                    UUID.randomUUID());
            var splitSale =
                    sales.sell(
                            admin, List.of(new CartItem(tiny, 2)), null, "CARD", UUID.randomUUID());
            assertEquals(new BigDecimal("0.01"), splitSale.tax());
            assertEquals(new BigDecimal("0.07"), splitSale.total());
            long overlap =
                    inventory.saveDetails(
                            admin,
                            null,
                            new InventoryService.ProductDetails(
                                    "REV-BAR",
                                    "",
                                    "Otro producto",
                                    "",
                                    "",
                                    "",
                                    "",
                                    BigDecimal.ZERO,
                                    BigDecimal.ONE,
                                    0,
                                    true,
                                    BigDecimal.ZERO));
            purchases.purchase(
                    admin,
                    supplier,
                    List.of(new PurchaseItem(overlap, "OTRO", expiry, 2, BigDecimal.ONE)),
                    UUID.randomUUID());
            assertEquals(2, new DesktopData(db).scanMatches("REV-BAR").size());
            AdministrationService settings = new AdministrationService(db);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            settings.setting(
                                    admin, "expiry_days", Integer.toString(Integer.MAX_VALUE)));
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(
                    () -> {
                        Stage window = new Stage();
                        window.setScene(new Scene(new Pane(), 1250, 850));
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
                TextField search = (TextField) find("#saleSearch");
                fx(
                        () -> {
                            search.setText("REV-BAR");
                            search.fireEvent(new javafx.event.ActionEvent());
                        });
                TableView<?> results = (TableView<?>) find("#saleProducts"),
                        activeCart = (TableView<?>) find("#saleCart");
                until(() -> results.getItems().size() == 2);
                fx(() -> assertTrue(activeCart.getItems().isEmpty()));
                tab("Administración", "Usuarios");
                TableView<?> users = (TableView<?>) find("#operationalTable");
                until(() -> users.getItems().size() == 1);
                fx(() -> users.getSelectionModel().selectFirst());
                fire("Editar usuario");
                fire("OK");
                TextField username = (TextField) find("#formField0");
                fx(() -> username.setText("review_renamed"));
                fire("OK");
                find("#username");
                assertEquals(
                        "review_renamed",
                        auth.login("review_renamed", "RevisionPrueba123!").name());
                assertThrows(
                        SecurityException.class,
                        () -> new org.sigf.repositories.ReportRepository(db, admin).grid("users"));
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
