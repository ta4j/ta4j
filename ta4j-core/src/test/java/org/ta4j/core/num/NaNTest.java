/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;

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

}
