package org.ta4j.core;

import org.junit.Test;

import static org.junit.Assert.*;


public class DecimalTest {
    @Test
    public void valueOf() {
        assertEquals(Decimal.valueOf(0.33), Decimal.valueOf("0.33"));
    }
}
