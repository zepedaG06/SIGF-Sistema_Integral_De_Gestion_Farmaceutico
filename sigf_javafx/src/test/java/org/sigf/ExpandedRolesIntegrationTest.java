package org.sigf;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.config.Database;
import org.sigf.domain.PurchaseItem;
import org.sigf.security.Security;
import org.sigf.services.*;

@EnabledIfEnvironmentVariable(named = "SIGF_EXPANDED_ROLES_TEST", matches = "true")
class ExpandedRolesIntegrationTest {
    @Test
    void validatesExpandedOperationsAndFinancialBoundaries() throws Exception {
        assertEquals(
                "jdbc:postgresql://127.0.0.1:55439/sigf_javafx_db", System.getenv("SIGF_DB_URL"));
        try (Database db = new Database()) {
            Security auth = new Security(db);
            auth.bootstrap("roles_admin", "PruebaRoles123!");
            var admin = auth.login("roles_admin", "PruebaRoles123!");
            AdministrationService users = new AdministrationService(db);
            users.user(admin, null, "roles_pharma", "PruebaRoles123!", "PHARMACIST", true);
            users.user(admin, null, "roles_cash", "PruebaRoles123!", "CASHIER", true);
            var pharma = auth.login("roles_pharma", "PruebaRoles123!");
            var cash = auth.login("roles_cash", "PruebaRoles123!");
            ContactsService contacts = new ContactsService(db);
            long client = contacts.createCustomer(cash, "Cliente", "123", "");
            contacts.customer(cash, client, "Cliente actualizado", "456", "RUC");
            contacts.customer(pharma, client, "Cliente revisado", "456", "RUC");
            InventoryService inventory = new InventoryService(db);
            var draft =
                    new InventoryService.ProductDetails(
                            "EXPAND",
                            "",
                            "Producto",
                            "",
                            "",
                            "Tabletas",
                            "",
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            1,
                            false,
                            null);
            long product = inventory.saveDetails(pharma, null, draft);
            var stored = inventory.details(product);
            assertFalse(stored.active());
            assertNull(stored.taxRate());
            assertEquals(0, stored.price().signum());
            var priced =
                    new InventoryService.ProductDetails(
                            "EXPAND",
                            "",
                            "Producto",
                            "",
                            "",
                            "Tabletas",
                            "",
                            BigDecimal.ONE,
                            BigDecimal.TEN,
                            1,
                            true,
                            BigDecimal.ZERO);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> inventory.saveDetails(pharma, null, priced));
            assertThrows(
                    SecurityException.class, () -> inventory.saveDetails(pharma, product, priced));
            assertThrows(SecurityException.class, () -> inventory.saveDetails(cash, null, draft));
            inventory.saveDetails(admin, product, priced);
            var items =
                    List.of(
                            new PurchaseItem(
                                    product,
                                    "ROLE-LOT",
                                    LocalDate.now().plusDays(90),
                                    5,
                                    BigDecimal.ONE));
            var drafts = new PurchaseDraftService(db);
            drafts.save(pharma, null, null, items);
            assertTrue(drafts.load(pharma).isPresent());
            drafts.clear(pharma);
            var recovery = new PurchaseRecovery(db);
            var request = new PurchaseRecovery.Pending(UUID.randomUUID(), null, items);
            recovery.save(pharma, request);
            assertEquals(1, recovery.list(pharma).size());
            assertEquals(1, new DesktopData(db, pharma).pending(pharma, true).rows().size());
            long purchase = recovery.resolve(pharma, request.id());
            assertEquals(purchase, recovery.resolve(pharma, request.id()));
            recovery.acknowledge(pharma, request.id(), false);
            long batch;
            try (var c = db.connection();
                    var s = c.createStatement();
                    var r = s.executeQuery("select id from batch where product_id=" + product)) {
                r.next();
                batch = r.getLong(1);
            }
            StockService stock = new StockService(db);
            stock.adjust(pharma, batch, 2, "Conteo");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> stock.adjust(pharma, batch, -100, "Insuficiente"));
            assertThrows(IllegalArgumentException.class, () -> stock.adjust(pharma, batch, 1, ""));
            assertThrows(
                    SecurityException.class, () -> stock.adjust(cash, batch, 1, "No autorizado"));
            assertThrows(
                    SecurityException.class,
                    () ->
                            new PurchaseService(db)
                                    .purchase(cash, (Long) null, items, UUID.randomUUID()));
            assertThrows(
                    SecurityException.class,
                    () -> stock.block(pharma, batch, false, "No autorizado"));
            try (var c = db.connection();
                    var s = c.createStatement();
                    var r = s.executeQuery("select quantity from batch where id=" + batch)) {
                r.next();
                assertEquals(7, r.getInt(1));
            }
        }
    }
}
