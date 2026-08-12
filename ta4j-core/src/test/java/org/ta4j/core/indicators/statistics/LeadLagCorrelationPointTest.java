/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.statistics.LeadLagCorrelationIndicator.Point;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LeadLagCorrelationPointTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public LeadLagCorrelationPointTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void pointRejectsNonFiniteCorrelation() {
        assertThrows(IllegalArgumentException.class,
                () -> new Point(0, DoubleNum.valueOf(Double.POSITIVE_INFINITY), 8));
        assertThrows(IllegalArgumentException.class,
                () -> new Point(0, DoubleNum.valueOf(Double.NEGATIVE_INFINITY), 8));
    }

    @Test
    public void pointRejectsFiniteCorrelationWithFewerThanTwoSamples() {
        // A finite Pearson correlation requires at least two aligned samples.
        assertThrows(IllegalArgumentException.class, () -> new Point(0, numFactory.one(), 0));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, numFactory.one(), 1));
        // NaN correlations keep working for unavailable windows at any count.
        new Point(0, NaN.NaN, 0);
        new Point(0, NaN.NaN, 1);
        // And a valid two-sample point is accepted.
        assertEquals(2, new Point(0, numFactory.one(), 2).sampleCount());
    }

    @Test
    public void isDefinedReflectsTheCorrelation() {
        Point undefined = new Point(0, NaN.NaN, 0);
        Point defined = new Point(0, numFactory.one(), 2);

        assertFalse(undefined.isDefined());
        assertTrue(defined.isDefined());
    }
}
