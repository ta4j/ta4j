/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CrossIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Boolean> {

    public CrossIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void unstableBarsIncludeComponentWarmupAndLookback() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 1, 1, 1, 1, 1, 1, 1)
                .build();

        MockIndicator up = new MockIndicator(series, 4,
                List.of(numOf(10), numOf(10), numOf(10), numOf(10), numOf(10), numOf(9), numOf(8), numOf(7)));
        MockIndicator low = new MockIndicator(series, 2,
                List.of(numOf(9), numOf(9), numOf(9), numOf(9), numOf(9), numOf(9), numOf(9), numOf(9)));

        CrossIndicator indicator = new CrossIndicator(up, low);

        assertEquals(5, indicator.getCountOfUnstableBars());
        assertFalse(indicator.getValue(4));
        assertFalse(indicator.getValue(5));
        assertTrue(indicator.getValue(6));
    }
}
