package org.sigf.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseItem(
        long productId, String lot, LocalDate expiry, int quantity, BigDecimal cost) {
    public PurchaseItem {
        lot = lot == null ? null : lot.trim();
    }
}
