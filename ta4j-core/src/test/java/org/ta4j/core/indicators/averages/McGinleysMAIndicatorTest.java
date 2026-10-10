/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.ta4j.core.TestUtils.*;
import static org.junit.Assert.*;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.CsvTestUtils;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.StochasticIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class McGinleysMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public McGinleysMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void mcginleysIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(McGinleysMAIndicatorTest.class, "McGinley.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        MCGinleyMAIndicator mcg = new MCGinleyMAIndicator(new ClosePriceIndicator(barSeries), 14);

        for (int i = 1; i < barSeries.getBarCount(); i++) {

            Num expected = mock.getValue(i);
            Num value = mcg.getValue(i);

            assertNumEquals(expected.doubleValue(), value);
        }
    }

    @Test
    public void headAdvanceReanchorsRecursionAtFirstRetainedBar() {
        // McGinley recurses into index - 1, so when the recursion is severed by
        // a head advance it must re-anchor at the first retained bar instead of
        // walking back to the removed index 0. The stochastic source drops its
        // whole cache on the advance (zero range at the retained head), so the
        // new seed is its zero value at index 3. The division by a zero
        // previous average is undefined, so the next value is NaN rather than a
        // stale cached NaN from the pre-advance chain.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : new double[] { 10, 20, 30, 40, 40, 40, 40, 40 }) {
            series.barBuilder().openPrice(close).closePrice(close).highPrice(close).lowPrice(close).add();
        }
        StochasticIndicator stochastic = new StochasticIndicator(new ClosePriceIndicator(series), 3);
        MCGinleyMAIndicator mcg = new MCGinleyMAIndicator(stochastic, 2);
        mcg.getValue(7); // NaN chain before the advance

        series.setMaximumBarCount(5);
        assertEquals(3, series.getBeginIndex());

        // The re-anchored recursion seeds at the first retained source value
        // (the flat retained window leaves a zero stochastic range) and then
        // recurses forward into an undefined 0 / 0 ratio. The pre-fix chain
        // kept serving the stale NaN cached for index 3 before the advance,
        // and walking past index 0 after a full eviction raised an error.
        assertNumEquals(0, mcg.getValue(3));
        assertTrue(Num.isNaNOrNull(mcg.getValue(4)));
    }
}
