package org.sigf;

import org.junit.jupiter.api.Test;
import org.sigf.services.PharmacyProfileService;
import static org.junit.jupiter.api.Assertions.*;

class PharmacyProfileValidationTest {
    private PharmacyProfileService.Profile profile(String name, String legal, String ruc, String address, String phone, String email) {
        return new PharmacyProfileService.Profile(name, legal, ruc, address, phone, email, null);
    }
    @Test void requiresEachCoreFieldAndAllowsOptionalEmail() {
        var valid = profile("Farmacia", "Titular", "J0000000000000", "Managua", "22222222", "");
        assertDoesNotThrow(() -> PharmacyProfileService.validateProfile(valid));
        assertThrows(IllegalArgumentException.class, () -> PharmacyProfileService.validateProfile(profile(" ", "Titular", "RUC", "Managua", "123", "")));
        assertThrows(IllegalArgumentException.class, () -> PharmacyProfileService.validateProfile(profile("Farmacia", "", "RUC", "Managua", "123", "")));
        assertThrows(IllegalArgumentException.class, () -> PharmacyProfileService.validateProfile(profile("Farmacia", "Titular", "", "Managua", "123", "")));
        assertThrows(IllegalArgumentException.class, () -> PharmacyProfileService.validateProfile(profile("Farmacia", "Titular", "RUC", "", "123", "")));
        assertThrows(IllegalArgumentException.class, () -> PharmacyProfileService.validateProfile(profile("Farmacia", "Titular", "RUC", "Managua", "", "")));
        assertThrows(IllegalArgumentException.class, () -> PharmacyProfileService.validateProfile(profile("Farmacia", "Titular", "RUC", "Managua", "123", "incorrecto")));
    }
}
