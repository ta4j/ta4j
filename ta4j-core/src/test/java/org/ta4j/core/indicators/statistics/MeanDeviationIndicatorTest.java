/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MeanDeviationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public MeanDeviationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 7, 6, 3, 4, 5, 11, 3, 0, 9).build();
    }

    @Test
    public void meanDeviationUsingBarCount5UsingClosePrice() {
        var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals(2.44444444444444, meanDeviation.getValue(2));
        assertNumEquals(2.5, meanDeviation.getValue(3));
        assertNumEquals(2.16, meanDeviation.getValue(7));
        assertNumEquals(2.32, meanDeviation.getValue(8));
        assertNumEquals(2.72, meanDeviation.getValue(9));
    }

    @Test
    public void firstValueShouldBeZero() {
        var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals(0, meanDeviation.getValue(0));
    }

    @Test
    public void meanDeviationShouldBeZeroWhenBarCountIs1() {
        var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(0, meanDeviation.getValue(2));
        assertNumEquals(0, meanDeviation.getValue(7));
    }

    @Test
    public void anchorsWindowAtBeginIndexAfterRemoval() {
        // Evict the first four closes (1..4) so beginIndex = 4; the retained closes
        // [5,6,7,8,9,10] live at absolute indices 4..9.
        BarSeries pruned = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .withMaxBarCount(6)
                .build();
        var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(pruned), 6);

        // Window [4..7] = {5,6,7,8}: mean 6.5, deviations 1.5 + 0.5 + 0.5 + 1.5
        assertNumEquals(1.0, meanDeviation.getValue(7));
        // Window [4..8] = {5,6,7,8,9}: mean 7, deviations 2 + 1 + 0 + 1 + 2
        assertNumEquals(1.2, meanDeviation.getValue(8));
        // Window [4..9] = {5,6,7,8,9,10}: mean 7.5, deviations 2.5 + 1.5 + 0.5 + 0.5 + 1.5 + 2.5
        assertNumEquals(1.5, meanDeviation.getValue(9));
    }
}
