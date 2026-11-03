/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;

public class NaNTest {

    @Test
    public void testExpReturnsNaN() {
        final Num nan = NaN.NaN;
        final Num result = nan.exp();
        assertTrue(result.isNaN());
        assertNumEquals(NaN.NaN, result);
    }

    @Test
    public void testExpIsSameInstance() {
        final Num nan = NaN.NaN;
        final Num result = nan.exp();
        // NaN operations should return the same NaN instance
        assertTrue(result == NaN.NaN);
    }

    @Test
    public void compareToAndEqualsTreatOnlyNaNAsEqual() {
        final Num nan = NaN.NaN;
        final Num one = DoubleNum.valueOf(1);

        assertEquals(0, nan.compareTo(NaN.NaN));
        assertTrue(nan.compareTo(one) > 0);
        assertTrue(nan.equals(NaN.NaN));
        assertFalse(nan.equals(one));
    }

}
