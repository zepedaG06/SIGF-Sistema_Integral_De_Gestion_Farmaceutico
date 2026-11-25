package org.sigf.domain;

import java.util.List;

public record SaleQuote(Receipt totals, List<QuoteLine> lines) {}
