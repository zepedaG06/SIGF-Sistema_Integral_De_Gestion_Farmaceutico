package org.sigf.domain;

import java.math.BigDecimal;

public record Receipt(long id, BigDecimal subtotal, BigDecimal tax, BigDecimal total) {}
