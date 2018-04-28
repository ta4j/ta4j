package org.ta4j.core.num;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class BigDecimalNumTest {
    @Test
    public void sqrtGuess() {
        assertEquals(BigDecimal.valueOf(2), ((BigDecimalNum)BigDecimalNum.valueOf(9.9999)).sqrtGuess());
        assertEquals(BigDecimal.valueOf(6), ((BigDecimalNum)BigDecimalNum.valueOf(10.00001)).sqrtGuess());
        assertEquals(BigDecimal.valueOf(60), ((BigDecimalNum)BigDecimalNum.valueOf(100.0001)).sqrtGuess());
        assertEquals(BigDecimal.valueOf(60), ((BigDecimalNum)BigDecimalNum.valueOf(1000.0001)).sqrtGuess());
        assertEquals(BigDecimal.valueOf(600), ((BigDecimalNum)BigDecimalNum.valueOf(10000.0001)).sqrtGuess());
        assertEquals(BigDecimal.valueOf(600), ((BigDecimalNum)BigDecimalNum.valueOf(100000.0001)).sqrtGuess());
        assertEquals(BigDecimal.valueOf(6000), ((BigDecimalNum)BigDecimalNum.valueOf(1000000.0001)).sqrtGuess());

        assertEquals(BigDecimal.valueOf(600), ((BigDecimalNum)BigDecimalNum.valueOf(123586)).sqrtGuess());
        assertEquals(BigDecimal.valueOf(600), ((BigDecimalNum)BigDecimalNum.valueOf(123586.236423232348)).sqrtGuess());
    }
}
