package org.sigf;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sigf.security.Security;
import org.sigf.services.InventoryService;

class InventoryServiceTest {
    private final InventoryService service = new InventoryService(null);
    private final Security.User admin = new Security.User(1, "a", "ADMIN"),
            cashier = new Security.User(2, "c", "CASHIER");

    @Test
    void rejectsInvalidProductBeforePersistence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveProduct(admin, null, "", "Nombre", BigDecimal.ONE, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveProduct(admin, null, "A", "Nombre", new BigDecimal("-1"), 0));
    }

    @Test
    void protectsAdministrativeOperations() {
        assertThrows(
                SecurityException.class,
                () -> service.saveProduct(cashier, null, "A", "Nombre", BigDecimal.ONE, 0));
        assertThrows(
                SecurityException.class,
                () ->
                        service.receive(
                                cashier, 1, "L", LocalDate.now().plusDays(1), 1, BigDecimal.ONE));
    }

    @Test
    void rejectsInvalidMovementsBeforePersistence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.receive(admin, 1, "L", LocalDate.now(), 1, BigDecimal.ONE));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.sell(cashier, 1, 0, UUID.randomUUID()));
    }
}
