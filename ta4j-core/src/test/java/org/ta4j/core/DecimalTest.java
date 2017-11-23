package org.ta4j.core;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;


public class DecimalTest {
    @Test
    public void testValueOf() {
        assertEquals(Decimal.valueOf(0.33), Decimal.valueOf("0.33"));
    }

    @Test
    public void testMultiplicationSymmetricity(){
        Decimal decimalFromString = Decimal.valueOf("0.33");
        Decimal decimalFromDouble = Decimal.valueOf(0.33);

        assertEquals(decimalFromString.multipliedBy(decimalFromDouble), decimalFromDouble.multipliedBy(decimalFromString));
    }
}
