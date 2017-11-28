package org.ta4j.core;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;
import static org.ta4j.core.TATestsUtils.assertDecimalEquals;


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

    @Test
    public void testBigDecimalInDecimal(){
        BigDecimal ten = BigDecimal.valueOf(10);
        Decimal decimalTen = Decimal.valueOf(ten);
        Decimal zero = decimalTen.minus(Decimal.valueOf(BigDecimal.valueOf(10)));

        BigDecimal tenPlaces = BigDecimal.valueOf(10.1234567891);
        Decimal tenPlacesDecimal = Decimal.valueOf(tenPlaces);


        assertEquals(decimalTen.toString(), ten.toString());
        assertEquals(ten, decimalTen.getDelegate());
        assertDecimalEquals(zero, 0);

        assertEquals(tenPlaces, tenPlacesDecimal.getDelegate());
        assertEquals(tenPlaces.multiply(ten), decimalTen.multipliedBy(tenPlacesDecimal).getDelegate()); // BigDecimal = BigDecimal
        assertEquals(Decimal.valueOf(tenPlaces.multiply(ten)), decimalTen.multipliedBy(tenPlacesDecimal)); // Decimal = Decimal
    }
}
