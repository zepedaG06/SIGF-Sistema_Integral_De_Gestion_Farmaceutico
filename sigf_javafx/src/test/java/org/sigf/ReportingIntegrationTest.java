package org.sigf;

import static org.junit.jupiter.api.Assertions.*;
import static org.sigf.CheckoutGuiTest.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.config.Database;
import org.sigf.domain.*;
import org.sigf.repositories.*;
import org.sigf.security.Security;
import org.sigf.services.*;
import org.sigf.ui.*;

@EnabledIfEnvironmentVariable(named = "SIGF_REPORTING_TEST", matches = "true")
class ReportingIntegrationTest {
    private static <T> T call(java.util.concurrent.Callable<T> action) throws Exception {
        var task = new java.util.concurrent.FutureTask<T>(action);
        Platform.runLater(task);
        return task.get(25, TimeUnit.SECONDS);
    }

    @Test
    void reportsTransfersHistoricalDetailsAndRoleScopes() throws Exception {
        assertEquals(
                "jdbc:postgresql://127.0.0.1:55439/sigf_javafx_db", System.getenv("SIGF_DB_URL"));
        try (Database db = new Database()) {
            var auth = new Security(db);
            auth.bootstrap("report_admin", "ReportesPrueba123!");
            var admin = auth.login("report_admin", "ReportesPrueba123!");
            var administration = new AdministrationService(db);
            for (String role : List.of("CASHIER", "PHARMACIST", "ACCOUNTANT"))
                administration.user(admin, null, role, "ReportesPrueba123!", role, true);
            var cashier = auth.login("CASHIER", "ReportesPrueba123!");
            var pharmacist = auth.login("PHARMACIST", "ReportesPrueba123!");
            var accountant = auth.login("ACCOUNTANT", "ReportesPrueba123!");
            var contacts = new ContactsService(db);
            long customer = contacts.createCustomer(admin, "Cliente original", "88888888", "");
            long supplier = contacts.createSupplier(admin, "Proveedor original", "22222222");
            var inventory = new InventoryService(db);
            long product =
                    inventory.saveDetails(
                            admin,
                            null,
                            new InventoryService.ProductDetails(
                                    "REP-1",
                                    "REP-BAR",
                                    "Medicina original",
                                    "Categoría prueba",
                                    "",
                                    "Caja",
                                    "Laboratorio prueba",
                                    BigDecimal.ONE,
                                    BigDecimal.TEN,
                                    2,
                                    true,
                                    BigDecimal.ZERO));
            long purchase =
                    new PurchaseService(db)
                            .purchase(
                                    admin,
                                    supplier,
                                    List.of(
                                            new PurchaseItem(
                                                    product,
                                                    "L-REP",
                                                    LocalDate.now().plusDays(90),
                                                    20,
                                                    BigDecimal.ONE)),
                                    UUID.randomUUID());
            new PharmacyProfileService(db)
                    .save(
                            admin,
                            new PharmacyProfileService.Profile(
                                    "Farmacia Reportes",
                                    "Titular Reportes",
                                    "J1234567890123",
                                    "Managua",
                                    "88888888",
                                    "",
                                    null));
            var sales = new SalesService(db);
            var recovery = new SaleRecovery(db);
            var quote = sales.quoteDetailed(List.of(new CartItem(product, 2)));
            UUID request = UUID.randomUUID();
            var transfer = new TransferDetails("BAC", "REF-001", "Pago de medicina");
            var pending =
                    new SaleRecovery.Pending(
                            request,
                            customer,
                            "TRANSFER",
                            quote.totals().total(),
                            List.of(new CartItem(product, 2)),
                            quote.lines(),
                            null,
                            transfer);
            recovery.save(cashier, pending);
            assertEquals(transfer, recovery.list(cashier).getFirst().transfer());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            recovery.save(
                                    cashier,
                                    new SaleRecovery.Pending(
                                            request,
                                            customer,
                                            "TRANSFER",
                                            quote.totals().total(),
                                            pending.items(),
                                            quote.lines(),
                                            null,
                                            new TransferDetails("LAFISE", "OTRA", ""))));
            var banks = new BankService(db);
            banks.save(admin, "BAC", false);
            long sale = recovery.resolve(cashier, request).id();
            assertEquals(sale, recovery.resolve(cashier, request).id());
            recovery.acknowledge(cashier, request, false);
            assertTrue(banks.duplicate(cashier, "BAC", "REF-001"));
            assertFalse(banks.list(cashier, false).contains("BAC"));
            banks.save(admin, "BAC", true);
            assertThrows(SecurityException.class, () -> banks.save(accountant, "OTRO", true));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            recovery.save(
                                    cashier,
                                    new SaleRecovery.Pending(
                                            UUID.randomUUID(),
                                            null,
                                            "TRANSFER",
                                            quote.totals().total(),
                                            pending.items(),
                                            quote.lines(),
                                            null)));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sales.sell(
                                    cashier, pending.items(), null, "TRANSFER", UUID.randomUUID()));
            long otherSale =
                    sales.sell(
                                    admin,
                                    List.of(new CartItem(product, 1)),
                                    null,
                                    "CASH",
                                    UUID.randomUUID())
                            .id();
            LocalDate today = LocalDate.now();
            var empty = ReportRepository.Filters.empty();
            var reports = new ReportRepository(db, admin);
            assertEquals(
                    2,
                    reports.filtered("sales", today, today, "", empty, 0, 0, false).rows().size());
            assertEquals(
                    1,
                    new ReportRepository(db, cashier)
                            .filtered("sales", today, today, "", empty, 0, 0, false)
                            .rows()
                            .size());
            assertEquals(1, new ReportRepository(db, cashier).grid("sales").rows().size());
            assertThrows(
                    SecurityException.class,
                    () -> new ReportRepository(db, cashier).report("purchases", today, today));
            assertThrows(
                    SecurityException.class,
                    () -> new OperationDetailRepository(db).load(cashier, "sales", otherSale));
            assertThrows(
                    SecurityException.class, () -> new Operations(db, cashier).receipt(otherSale));
            var selection =
                    new ReportRepository.Filters(
                            Map.of(
                                    "cliente",
                                    Long.toString(customer),
                                    "pago",
                                    "TRANSFER",
                                    "banco",
                                    "BAC",
                                    "producto",
                                    Long.toString(product)));
            assertEquals(
                    1,
                    reports.filtered("sales", today, today, "", selection, 0, 0, false)
                            .rows()
                            .size());
            assertEquals(
                    0,
                    new BigDecimal(
                                    reports.filtered(
                                                    "sales", today, today, "", selection, 0, 0,
                                                    true)
                                            .rows()
                                            .getFirst()
                                            .get(1))
                            .compareTo(new BigDecimal("20")));
            assertEquals(
                    1,
                    reports.filtered(
                                    "purchases",
                                    today,
                                    today,
                                    "",
                                    new ReportRepository.Filters(
                                            Map.of("proveedor", Long.toString(supplier))),
                                    0,
                                    0,
                                    false)
                            .rows()
                            .size());
            assertEquals(
                    1,
                    reports.filtered(
                                    "stock",
                                    today,
                                    today,
                                    "",
                                    new ReportRepository.Filters(
                                            Map.of(
                                                    "categoría",
                                                    "Categoría prueba",
                                                    "laboratorio",
                                                    "Laboratorio prueba")),
                                    0,
                                    0,
                                    false)
                            .rows()
                            .size());
            assertFalse(
                    new ReportRepository(db, pharmacist)
                                    .filtered("general", today, today, "", empty, 0, 0, false)
                                    .rows()
                                    .stream()
                                    .anyMatch(r -> r.contains("C$")));
            assertEquals(
                    7,
                    new ReportRepository(db, accountant)
                            .filtered("general", today, today, "", empty, 0, 0, false)
                            .rows()
                            .size());
            assertEquals(
                    0,
                    reports.filtered("sales", today, today, "' OR 1=1 --", empty, 0, 0, false)
                            .rows()
                            .size());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> reports.filtered("general", today, today, "", selection, 0, 0, false));
            contacts.customer(admin, customer, "Cliente cambiado", "", "");
            contacts.contact(admin, "supplier", supplier, "Proveedor cambiado", "", true);
            var detail = new OperationDetailRepository(db).load(cashier, "sales", sale);
            assertTrue(detail.header().contains("Cliente original"));
            assertTrue(detail.header().contains("REF-001"));
            assertTrue(detail.header().contains("Pago de medicina"));
            assertEquals("L-REP", detail.lines().rows().getFirst().get(2));
            assertTrue(
                    new OperationDetailRepository(db)
                            .load(accountant, "purchases", purchase)
                            .header()
                            .contains("Proveedor original"));
            assertTrue(sales.receipt(sale).contains("REF-001"));
            for (String format : List.of("pdf", "xlsx", "docx")) {
                Path file = Path.of("target", "report-verification", "integracion." + format);
                Files.createDirectories(file.getParent());
                new ReportExportService(db)
                        .export(
                                accountant,
                                "payments",
                                "Pagos",
                                today,
                                today,
                                "",
                                empty,
                                "Todos",
                                format,
                                file);
                assertTrue(Files.size(file) > 1000);
            }
            assertThrows(
                    SecurityException.class,
                    () ->
                            new ReportExportService(db)
                                    .export(
                                            pharmacist,
                                            "payments",
                                            "Pagos",
                                            today,
                                            today,
                                            "",
                                            empty,
                                            "Todos",
                                            "pdf",
                                            Path.of(
                                                    "target",
                                                    "report-verification",
                                                    "denegado.pdf")));
            CountDownLatch boot = new CountDownLatch(1);
            Platform.startup(boot::countDown);
            assertTrue(boot.await(20, TimeUnit.SECONDS));
            try {
                final Stage[] stage = {null};
                call(
                        () -> {
                            stage[0] = new Stage();
                            stage[0].setScene(
                                    new Scene(new ReportsPane(db, accountant), 1250, 850));
                            stage[0].getScene()
                                    .getStylesheets()
                                    .add(
                                            org.sigf.app.Main.class
                                                    .getResource("/css/style.css")
                                                    .toExternalForm());
                            CheckoutGuiTest.stage.set(stage[0]);
                            stage[0].show();
                            return null;
                        });
                assertNotNull(call(() -> stage[0].getScene().lookup("#reportType")));
                until(
                        () ->
                                stage[0].getScene().getRoot().lookupAll(".table-view").stream()
                                        .anyMatch(
                                                n ->
                                                        n instanceof TableView<?> t
                                                                && t.getItems().size() == 7));
                snapshot("reportes-nuevos");
                call(
                        () -> {
                            assertTrue(
                                    ((ComboBox<?>) stage[0].getScene().lookup("#reportType"))
                                            .getItems()
                                            .contains("General"));
                            stage[0].setScene(new Scene(new SalesPane(db, cashier), 1250, 850));
                            stage[0].getScene()
                                    .getStylesheets()
                                    .add(
                                            org.sigf.app.Main.class
                                                    .getResource("/css/style.css")
                                                    .toExternalForm());
                            return null;
                        });
                assertNotNull(call(() -> stage[0].getScene().lookup("#transferBank")));
                call(
                        () -> {
                            ((ComboBox<String>) stage[0].getScene().lookup("#salePayment"))
                                    .setValue("Transferencia");
                            assertTrue(stage[0].getScene().lookup("#transferBank").isVisible());
                            assertEquals("Cerrar", SpanishButtons.CLOSE.getText());
                            return null;
                        });
                until(
                        () ->
                                !((ComboBox<?>) stage[0].getScene().lookup("#transferBank"))
                                        .getItems()
                                        .isEmpty());
                call(
                        () -> {
                            ((ComboBox<String>) stage[0].getScene().lookup("#transferBank"))
                                    .setValue("LAFISE");
                            ((TextField) stage[0].getScene().lookup("#transferReference"))
                                    .setText("UI-TRANSFER-002");
                            ((TextArea) stage[0].getScene().lookup("#transferDescription"))
                                    .setText("Transferencia capturada en interfaz");
                            var scan = (TextField) stage[0].getScene().lookup("#saleSearch");
                            scan.setText("REP-BAR");
                            scan.fireEvent(new javafx.event.ActionEvent());
                            return null;
                        });
                until(
                        () ->
                                ((TableView<?>) stage[0].getScene().lookup("#saleCart"))
                                                .getItems()
                                                .size()
                                        == 1);
                snapshot("transferencia-nueva");
                call(
                        () -> {
                            ((Button) stage[0].getScene().lookup("#cartCheckout")).fire();
                            return null;
                        });
                until(
                        () ->
                                javafx.stage.Window.getWindows().stream()
                                        .anyMatch(
                                                w ->
                                                        w.isShowing()
                                                                && w.getScene() != null
                                                                && w.getScene().getRoot()
                                                                        instanceof DialogPane));
                call(
                        () -> {
                            var dialog =
                                    (DialogPane)
                                            javafx.stage.Window.getWindows().stream()
                                                    .filter(
                                                            w ->
                                                                    w.isShowing()
                                                                            && w.getScene() != null
                                                                            && w.getScene()
                                                                                            .getRoot()
                                                                                    instanceof
                                                                                    DialogPane)
                                                    .findFirst()
                                                    .orElseThrow()
                                                    .getScene()
                                                    .getRoot();
                            ((Button) dialog.lookupButton(SpanishButtons.OK)).fire();
                            return null;
                        });
                databaseUntil(
                        db,
                        "select count(*) from payment where transfer_reference='UI-TRANSFER-002' and bank_name='LAFISE' and transfer_description='Transferencia capturada en interfaz'",
                        1);
                call(
                        () -> {
                            stage[0].close();
                            return null;
                        });
            } finally {
                Platform.exit();
            }
            new ResetService(db).reset(admin);
            try (var c = db.connection();
                    var q = c.createStatement();
                    var r =
                            q.executeQuery(
                                    "select (select count(*) from bank),(select count(*) from purchase_snapshot),(select count(*) from flyway_schema_history)")) {
                r.next();
                assertEquals(6, r.getInt(1));
                assertEquals(0, r.getInt(2));
                assertEquals(15, r.getInt(3));
            }
        }
    }
}
