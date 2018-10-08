package org.ta4j.core.num;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class DoubleNumTest {

    @Test
    public void testEqualsDoubleNumWithPrecisionNum() {
        final PrecisionNum precisionNum = PrecisionNum.valueOf(3.0);

        final DoubleNum doubleNum = DoubleNum.valueOf(3.0);

        assertFalse(doubleNum.equals(precisionNum));
    }
}