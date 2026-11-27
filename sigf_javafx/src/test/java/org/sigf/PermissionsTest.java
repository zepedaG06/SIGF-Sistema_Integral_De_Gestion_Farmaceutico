package org.sigf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.sigf.security.*;

class PermissionsTest {
    @Test
    void pharmacistHasOperationalPermissionsWithoutAdministrativePower() {
        assertTrue(Permissions.allows("PHARMACIST", Permission.SELL));
        assertTrue(Permissions.allows("PHARMACIST", Permission.CREATE_CUSTOMER));
        assertTrue(Permissions.allows("PHARMACIST", Permission.EDIT_PRODUCT_INFO));
        assertTrue(Permissions.allows("PHARMACIST", Permission.BLOCK_BATCH));
        assertFalse(Permissions.allows("PHARMACIST", Permission.ADMINISTER));
        assertTrue(Permissions.canReadReport("PHARMACIST", "purchases"));
    }

    @Test
    void expandedRolesKeepAdministrativeBoundaries() {
        for (Permission permission :
                new Permission[] {
                    Permission.CREATE_PRODUCT,
                    Permission.RECEIVE_PURCHASE,
                    Permission.ADJUST_STOCK,
                    Permission.EDIT_CUSTOMER
                }) {
            assertTrue(Permissions.allows("PHARMACIST", permission));
        }
        assertTrue(Permissions.allows("CASHIER", Permission.EDIT_CUSTOMER));
        for (Permission permission :
                new Permission[] {
                    Permission.CREATE_PRODUCT,
                    Permission.RECEIVE_PURCHASE,
                    Permission.ADJUST_STOCK,
                    Permission.BLOCK_BATCH,
                    Permission.ADMINISTER
                }) {
            assertFalse(Permissions.allows("CASHIER", permission));
        }
    }

    @Test
    void accountantCanReadFinancialReportsWithoutMutatingPermissions() {
        for (Permission permission : Permission.values()) {
            assertFalse(Permissions.allows("ACCOUNTANT", permission));
        }
        assertTrue(Permissions.canReadReport("ACCOUNTANT", "payments"));
        assertTrue(Permissions.canReadReport("ACCOUNTANT", "reconciliation"));
        assertFalse(Permissions.canReadReport("ACCOUNTANT", "users"));
        assertFalse(Permissions.canReadReport("ACCOUNTANT", "audit"));
    }

    @Test
    void cashierPermissionsArePreservedAndUnknownRolesAreDenied() {
        assertTrue(Permissions.allows("CASHIER", Permission.SELL));
        assertTrue(Permissions.allows("CASHIER", Permission.CREATE_CUSTOMER));
        assertFalse(Permissions.allows("UNKNOWN", Permission.ADMINISTER));
        assertFalse(Permissions.canReadReport("UNKNOWN", "sales"));
        assertEquals("Farmacéutico", Permissions.label("PHARMACIST"));
        assertEquals("Contador", Permissions.label("ACCOUNTANT"));
    }
}
