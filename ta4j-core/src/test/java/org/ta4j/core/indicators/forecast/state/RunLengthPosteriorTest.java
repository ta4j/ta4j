/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RunLengthPosteriorTest extends AbstractIndicatorTest<RunLengthPosterior, Num> {

    public RunLengthPosteriorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void constructorNormalizesAllValuesThroughMeanFactory() {
        NumFactory otherFactory = DecimalNumFactory.getInstance(40);

        RunLengthPosterior posterior = new RunLengthPosterior(7, otherFactory.numOf(0.25), numOf(0.1),
                otherFactory.numOf(0.04));

        assertEquals(7, posterior.runLength());
        assertEquals(posterior.mean().getClass(), posterior.probability().getClass());
        assertEquals(posterior.mean().getClass(), posterior.variance().getClass());
        assertNumEquals(0.25, posterior.probability());
        assertNumEquals(0.1, posterior.mean());
        assertNumEquals(0.04, posterior.variance());
    }

    @Test
    public void constructorRejectsInvalidPosteriorValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunLengthPosterior(-1, numOf(0.5), numFactory.zero(), numFactory.one()));
        assertThrows(IllegalArgumentException.class,
                () -> new RunLengthPosterior(0, numFactory.zero(), numFactory.zero(), numFactory.one()));
        assertThrows(IllegalArgumentException.class,
                () -> new RunLengthPosterior(0, numOf(1.1), numFactory.zero(), numFactory.one()));
        assertThrows(IllegalArgumentException.class,
                () -> new RunLengthPosterior(0, NaN.NaN, numFactory.zero(), numFactory.one()));
        assertThrows(IllegalArgumentException.class,
                () -> new RunLengthPosterior(0, numOf(0.5), NaN.NaN, numFactory.one()));
        assertThrows(IllegalArgumentException.class,
                () -> new RunLengthPosterior(0, numOf(0.5), numFactory.zero(), numOf(-1)));
    }
}
