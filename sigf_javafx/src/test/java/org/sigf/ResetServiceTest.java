package org.sigf;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.InventoryService;
import org.sigf.services.ResetService;

@EnabledIfEnvironmentVariable(named = "SIGF_RESET_TEST", matches = "true")
class ResetServiceTest {
    @Test
    void clearsOnlySigfDataAndPreservesSchemaAndDefaults() throws Exception {
        assertTrue(System.getenv("SIGF_DB_URL").contains(":55439/"), "Solo base de prueba aislada");
        try (Database db = new Database()) {
            Security security = new Security(db);
            security.bootstrap("reset_test", "contraseñaPrueba123");
            var admin = security.login("reset_test", "contraseñaPrueba123");
            InventoryService inventory = new InventoryService(db);
            inventory.saveProduct(admin, null, "R-1", "Prueba", BigDecimal.TEN, 1);
            long id = inventory.products("R-1").getFirst().id();
            inventory.receive(admin, id, "L-1", LocalDate.now().plusDays(90), 3, BigDecimal.ONE);
            assertThrows(
                    SecurityException.class,
                    () ->
                            new ResetService(db)
                                    .reset(new Security.User(admin.id(), admin.name(), "CASHIER")));
            assertEquals(3, inventory.products("R-1").getFirst().stock());
            int migrationCount;
            try (var connection = db.connection();
                    var query = connection.createStatement();
                    var result = query.executeQuery("select count(*) from flyway_schema_history")) {
                result.next();
                migrationCount = result.getInt(1);
            }
            new ResetService(db).reset(admin);
            assertFalse(security.hasUsers());
            assertTrue(inventory.products("").isEmpty());
            try (var c = db.connection();
                    var s = c.createStatement();
                    var r =
                            s.executeQuery(
                                    "select (select count(*) from stock_movement),(select count(*) from flyway_schema_history),(select value from setting where key='currency'),(select code from internal_series where id=1)")) {
                assertTrue(r.next());
                assertEquals(0, r.getInt(1));
                assertEquals(migrationCount, r.getInt(2));
                assertEquals("NIO", r.getString(3));
                assertEquals("DEMO", r.getString(4));
            }
            security.bootstrap("nuevo_admin", "otraContraseña123");
            assertTrue(security.hasUsers());
            try (var c = db.connection()) {
                assertThrows(SecurityException.class, () -> Security.verify(c, admin, true));
            }
        }
    }
}
