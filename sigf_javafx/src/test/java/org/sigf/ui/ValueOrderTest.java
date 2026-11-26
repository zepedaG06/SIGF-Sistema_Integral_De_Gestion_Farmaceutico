package org.sigf.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValueOrderTest {
    @Test
    void ordersNumbersAndMoneyByValue() {
        assertTrue(ValueOrder.compare("2", "10") < 0);
        assertTrue(ValueOrder.compare("C$ 900.00", "C$ 1,000.00") < 0);
        assertTrue(ValueOrder.compare("-10", "-2") < 0);
    }

    @Test
    void datesUseTheirOriginalIsoValues() {
        assertTrue(ValueOrder.compare("2026-01-31", "2026-02-01") < 0);
        assertTrue(ValueOrder.compare("2025-12-31 23:00", "2026-01-01 00:00") < 0);
    }

    @Test
    void mixedValuesHaveAConsistentOrder() {
        var values = new ArrayList<>(List.of("2", "10", "1abc", "C$ 100.00", "Sin clasificar"));
        values.sort(ValueOrder::compare);
        assertEquals(List.of("2", "10", "C$ 100.00", "1abc", "Sin clasificar"), values);
    }
}
