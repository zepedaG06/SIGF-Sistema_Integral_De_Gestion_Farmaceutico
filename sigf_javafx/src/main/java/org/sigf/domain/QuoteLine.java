package org.sigf.domain;

import java.math.BigDecimal;

public record QuoteLine(long productId, int quantity, BigDecimal price, BigDecimal taxRate) {}
