/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EventMutualInformationResultTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public EventMutualInformationResultTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void emptyRangeResultMustBeUndefined() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[20]).build();
        NumFactory factory = series.numFactory();
        // An empty sample range with defined metrics or formed bins is an
        // inconsistent state and must be rejected.
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(factory.zero(),
                factory.zero(), factory.zero(), 0, 0, NaN.NaN, 8, 0, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN,
                0, 0, NaN.NaN, 8, 2, BinningStrategy.EQUAL_WIDTH, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN,
                0, 0, factory.one(), 8, 0, BinningStrategy.EQUAL_WIDTH, 0, 3));
        // The undefined empty result is the canonical valid form.
        new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN, 0, 0, NaN.NaN, 8, 0, BinningStrategy.EQUAL_WIDTH, 0,
                3);
    }
}
