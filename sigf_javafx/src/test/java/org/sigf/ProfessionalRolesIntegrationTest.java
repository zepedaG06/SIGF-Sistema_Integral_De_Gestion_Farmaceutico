package org.sigf;

import static org.junit.jupiter.api.Assertions.*;
import static org.sigf.CheckoutGuiTest.*;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mindrot.jbcrypt.BCrypt;
import org.sigf.config.Database;
import org.sigf.domain.*;
import org.sigf.repositories.ReportRepository;
import org.sigf.security.*;
import org.sigf.services.*;
import org.sigf.ui.Workspace;

@EnabledIfEnvironmentVariable(named = "SIGF_ROLES_TEST", matches = "true")
class ProfessionalRolesIntegrationTest {
    private static final String PASSWORD = "RolesPrueba123!";

    @Test
    void upgradesExistingRolesEnforcesServicesAndShowsProfessionalWorkspaces() throws Exception {
        String url = System.getenv("SIGF_DB_URL"),
                dbUser = System.getenv("SIGF_DB_USER"),
                dbPassword = System.getenv("SIGF_DB_PASSWORD");
        assertEquals("jdbc:postgresql://127.0.0.1:55439/sigf_javafx_db", url);
        // Parte de V13 con cuentas existentes para comprobar la actualización sin perderlas.
        Flyway.configure().dataSource(url, dbUser, dbPassword).target("13").load().migrate();
        try (var connection = DriverManager.getConnection(url, dbUser, dbPassword);
                var insert =
                        connection.prepareStatement(
                                "insert into app_user(username,password_hash,role) values(?,?,?)")) {
            for (String role : List.of("ADMIN", "CASHIER")) {
                insert.setString(1, "legacy_" + role.toLowerCase());
                insert.setString(2, BCrypt.hashpw(PASSWORD, BCrypt.gensalt(12)));
                insert.setString(3, role);
                insert.executeUpdate();
            }
        }
        try (Database db = new Database()) {
            Security security = new Security(db);
            var admin = security.login("legacy_admin", PASSWORD);
            var cashier = security.login("legacy_cashier", PASSWORD);
            AdministrationService administration = new AdministrationService(db);
            administration.user(admin, null, "farmaceutico", PASSWORD, "PHARMACIST", true);
            administration.user(admin, null, "contador", PASSWORD, "ACCOUNTANT", true);
            var pharmacy = security.login("farmaceutico", PASSWORD);
            var accountant = security.login("contador", PASSWORD);
            assertEquals("CASHIER", cashier.role());
            assertEquals(4, scalar(db, "select count(*) from app_user"));
            InventoryService inventory = new InventoryService(db);
            long product =
                    inventory.saveDetails(
                            admin,
                            null,
                            new InventoryService.ProductDetails(
                                    "ROLE-1",
                                    "800001",
                                    "Producto inicial",
                                    "Prueba",
                                    "",
                                    "Caja",
                                    "Lab",
                                    BigDecimal.ONE,
                                    BigDecimal.TEN,
                                    2,
                                    true,
                                    BigDecimal.ZERO));
            inventory.receive(
                    admin, product, "ROLE-LOT", LocalDate.now().plusDays(60), 10, BigDecimal.ONE);
            long batch = scalar(db, "select id from batch where product_id=" + product);
            var original = inventory.details(product);
            inventory.updatePharmaceuticalInfo(
                    pharmacy,
                    product,
                    new InventoryService.PharmaceuticalInfo(
                            "Producto revisado",
                            "Revisada",
                            "Tabletas",
                            "Laboratorio",
                            "Descripción revisada"));
            var updated = inventory.details(product);
            assertEquals("Producto revisado", updated.name());
            assertEquals(original.price(), updated.price());
            assertEquals(original.cost(), updated.cost());
            assertEquals(original.taxRate(), updated.taxRate());
            assertEquals(original.minimum(), updated.minimum());
            assertEquals(original.active(), updated.active());
            assertThrows(
                    SecurityException.class,
                    () -> inventory.saveDetails(pharmacy, product, original));
            assertThrows(
                    SecurityException.class,
                    () ->
                            inventory.updatePharmaceuticalInfo(
                                    accountant,
                                    product,
                                    new InventoryService.PharmaceuticalInfo("No", "", "", "", "")));
            ContactsService contacts = new ContactsService(db);
            long client = contacts.createCustomer(pharmacy, "Cliente farmacia", "88887777", "");
            assertTrue(contacts.createCustomer(cashier, "Cliente caja", "", "") > 0);
            assertThrows(
                    SecurityException.class,
                    () -> contacts.createCustomer(accountant, "No", "", ""));
            assertThrows(
                    SecurityException.class, () -> contacts.createSupplier(pharmacy, "No", ""));
            StockService stock = new StockService(db);
            assertThrows(
                    IllegalArgumentException.class, () -> stock.block(pharmacy, batch, true, ""));
            stock.block(pharmacy, batch, true, "Revisión farmacéutica");
            assertThrows(
                    SecurityException.class,
                    () -> stock.block(pharmacy, batch, false, "No autorizado"));
            stock.adjust(pharmacy, batch, 1, "Conteo físico");
            stock.adjust(pharmacy, batch, -1, "Corrección del conteo");
            SalesService sales = new SalesService(db);
            List<CartItem> cart = List.of(new CartItem(product, 1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sales.sell(pharmacy, cart, client, "CASH", UUID.randomUUID()));
            stock.block(admin, batch, false, "Revisión finalizada");
            var sale = sales.sell(pharmacy, cart, client, "CASH", UUID.randomUUID());
            assertEquals(BigDecimal.TEN.setScale(2), sale.total());
            assertThrows(
                    SecurityException.class,
                    () -> sales.sell(accountant, cart, null, "CASH", UUID.randomUUID()));
            assertThrows(
                    SecurityException.class,
                    () -> sales.voidSale(accountant, sale.id(), "No autorizado"));
            assertThrows(
                    SecurityException.class,
                    () ->
                            new SaleRecovery(db)
                                    .save(
                                            accountant,
                                            new SaleRecovery.Pending(
                                                    UUID.randomUUID(),
                                                    null,
                                                    "CASH",
                                                    BigDecimal.TEN,
                                                    cart)));
            assertThrows(
                    SecurityException.class,
                    () ->
                            new PurchaseService(db)
                                    .purchase(
                                            accountant,
                                            null,
                                            List.of(
                                                    new PurchaseItem(
                                                            product,
                                                            "NO",
                                                            LocalDate.now().plusDays(30),
                                                            1,
                                                            BigDecimal.ONE)),
                                            UUID.randomUUID()));
            assertThrows(
                    SecurityException.class,
                    () -> stock.adjust(accountant, batch, 1, "No autorizado"));
            assertThrows(
                    SecurityException.class,
                    () -> stock.block(accountant, batch, true, "No autorizado"));
            assertThrows(
                    SecurityException.class,
                    () -> administration.setting(accountant, "expiry_days", "1"));
            assertThrows(
                    SecurityException.class,
                    () -> administration.user(accountant, null, "no", PASSWORD, "ADMIN", true));
            assertThrows(SecurityException.class, () -> new ResetService(db).reset(accountant));
            var reports = new ReportRepository(db, accountant);
            LocalDate today = LocalDate.now();
            for (String name :
                    List.of(
                            "sales",
                            "purchases",
                            "payments",
                            "tax_summary",
                            "internal_docs",
                            "voided_docs",
                            "reconciliation",
                            "stock")) {
                assertNotNull(reports.report(name, today.minusDays(1), today));
            }
            assertEquals(1, reports.report("payments", today, today).rows().size());
            assertThrows(SecurityException.class, () -> reports.grid("users"));
            assertThrows(SecurityException.class, () -> reports.report("audit", today, today));
            assertNotNull(new ReportRepository(db, pharmacy).report("purchases", today, today));
            assertThrows(SecurityException.class, () -> new DesktopData(db, accountant).settings());
            assertEquals(1, scalar(db, "select count(*) from sale"));
            assertEquals(9, scalar(db, "select quantity from batch where id=" + batch));
            assertEquals(
                    1,
                    scalar(
                            db,
                            "select count(*) from audit_log where action='BATCH_BLOCK' and details like '%Revisión farmacéutica%'"));
            var forged =
                    new Security.User(pharmacy.id(), pharmacy.name(), "ADMIN", pharmacy.version());
            try (var connection = db.connection()) {
                assertThrows(
                        SecurityException.class, () -> Security.verify(connection, forged, true));
            }
            administration.user(admin, accountant.id(), accountant.name(), "", "ACCOUNTANT", true);
            assertThrows(SecurityException.class, () -> reports.report("sales", today, today));
            var currentAccountant = security.login("contador", PASSWORD);
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
                        new Workspace(window, db, pharmacy).show();
                        window.show();
                        ready.countDown();
                    });
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            try {
                tab("Inventario", "Productos");
                TableView<?> catalog = (TableView<?>) find(".table-view");
                until(() -> !catalog.getItems().isEmpty());
                fx(() -> catalog.getSelectionModel().selectFirst());
                fire("Editar información farmacéutica");
                TextField name = (TextField) find("#formField0");
                fx(
                        () -> {
                            assertNull(name.getScene().lookup("#productPrice"));
                            name.setText("Producto editado por farmacéutico");
                        });
                fire("Aceptar");
                databaseUntil(
                        db,
                        "select count(*) from product where name='Producto editado por farmacéutico'",
                        1);
                tab("Reportes", "Reportes");
                ComboBox<?> type = (ComboBox<?>) find("#reportType");
                fx(() -> assertTrue(type.getItems().contains("Compras")));
                snapshot("rol-farmaceutico");
                fx(() -> new Workspace(stage.get(), db, currentAccountant).show());
                fx(
                        () -> {
                            TabPane navigation =
                                    (TabPane)
                                            ((BorderPane) stage.get().getScene().getRoot())
                                                    .getCenter();
                            assertEquals(
                                    List.of("Inicio", "Consultas", "Reportes"),
                                    navigation.getTabs().stream().map(Tab::getText).toList());
                        });
                tab("Consultas", "Pagos");
                TableView<?> payments = (TableView<?>) find("#operationalTable");
                until(() -> payments.getItems().size() == 1);
                snapshot("rol-contador");
                tab("Reportes", "Reportes");
                ComboBox<?> accountingTypes = (ComboBox<?>) find("#reportType");
                fx(
                        () -> {
                            assertTrue(
                                    accountingTypes.getItems().contains("Conciliación de datos"));
                            assertFalse(
                                    accountingTypes
                                            .getItems()
                                            .contains("Actividad administrativa"));
                        });
                fx(() -> new Workspace(stage.get(), db, admin).show());
                tab("Administración", "Usuarios");
                fire("+ Nuevo usuario");
                TextField username = (TextField) find("#formField0");
                PasswordField password = (PasswordField) find("#formField1");
                ComboBox<String> role = (ComboBox<String>) find("#formField2");
                fx(
                        () -> {
                            assertEquals(4, role.getItems().size());
                            username.setText("farmaceutico_ui");
                            password.setText(PASSWORD);
                            role.setValue("Farmacéutico");
                        });
                fire("Aceptar");
                databaseUntil(
                        db,
                        "select count(*) from app_user where username='farmaceutico_ui' and role='PHARMACIST'",
                        1);
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
