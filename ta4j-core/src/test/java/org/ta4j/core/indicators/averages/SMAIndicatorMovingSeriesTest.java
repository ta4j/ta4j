/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SMAIndicatorMovingSeriesTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SMAIndicatorMovingSeriesTest(NumFactory numFactory) {
        super((data, params) -> new SMAIndicator(data, (int) params[0]), numFactory);
    }

    private BarSeries data;

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 7).build();
        data.setMaximumBarCount(4);
    }

    @Test
    public void usingBarCount3MovingSeries() {
        firstAddition();
        secondAddition();
        thirdAddition();
        fourthAddition();
        randomAccessAfterFourAdditions();
    }

    private Instant getNextEndTime() {
        var lastBar = data.getLastBar();
        return lastBar == null ? null : lastBar.getEndTime().plus(lastBar.getTimePeriod());
    }

    private void firstAddition() {
        data.barBuilder().closePrice(5.).endTime(getNextEndTime()).add();
        Indicator<Num> indicator2 = getIndicator(new ClosePriceIndicator(data), 2);

        // unstable bars skipped, unpredictable results
        assertNumEquals((3d + 4d) / 2, indicator2.getValue(data.getBeginIndex() + 1));
        assertNumEquals((4d + 7d) / 2, indicator2.getValue(data.getBeginIndex() + 2));
        assertNumEquals((7d + 5d) / 2, indicator2.getValue(data.getBeginIndex() + 3));

        Indicator<Num> indicator3 = getIndicator(new ClosePriceIndicator(data), 3);

        // unstable bars skipped, unpredictable results
        assertNumEquals((3d + 4d + 7d) / 3, indicator3.getValue(data.getBeginIndex() + 2));
        assertNumEquals((4d + 7d + 5d) / 3, indicator3.getValue(data.getBeginIndex() + 3));
    }

    private void secondAddition() {
        data.barBuilder().closePrice(10.).endTime(getNextEndTime()).add();
        Indicator<Num> indicator2 = getIndicator(new ClosePriceIndicator(data), 2);

        // unstable bars skipped, unpredictable results
        assertNumEquals((4d + 7d) / 2, indicator2.getValue(data.getBeginIndex() + 1));
        assertNumEquals((7d + 5d) / 2, indicator2.getValue(data.getBeginIndex() + 2));
        assertNumEquals((5d + 10d) / 2, indicator2.getValue(data.getBeginIndex() + 3));

        Indicator<Num> indicator3 = getIndicator(new ClosePriceIndicator(data), 3);

        // unstable bars skipped, unpredictable results
        assertNumEquals((4d + 7d + 5d) / 3, indicator3.getValue(data.getBeginIndex() + 2));
        assertNumEquals((7d + 5d + 10d) / 3, indicator3.getValue(data.getBeginIndex() + 3));
    }

    private void thirdAddition() {
        data.barBuilder().closePrice(20.).endTime(getNextEndTime()).add();
        Indicator<Num> indicator2 = getIndicator(new ClosePriceIndicator(data), 2);

        // unstable bars skipped, unpredictable results
        assertNumEquals((7d + 5d) / 2, indicator2.getValue(data.getBeginIndex() + 1));
        assertNumEquals((5d + 10d) / 2, indicator2.getValue(data.getBeginIndex() + 2));
        assertNumEquals((10d + 20d) / 2, indicator2.getValue(data.getBeginIndex() + 3));

        Indicator<Num> indicator3 = getIndicator(new ClosePriceIndicator(data), 3);

        // unstable bars skipped, unpredictable results
        assertNumEquals((7d + 5d + 10d) / 3, indicator3.getValue(data.getBeginIndex() + 2));
        assertNumEquals((5d + 10d + 20d) / 3, indicator3.getValue(data.getBeginIndex() + 3));
    }

    private void fourthAddition() {
        data.barBuilder().closePrice(30.).endTime(getNextEndTime()).add();
        Indicator<Num> indicator2 = getIndicator(new ClosePriceIndicator(data), 2);

        // unstable bars skipped, unpredictable results
        assertNumEquals((5d + 10d) / 2, indicator2.getValue(data.getBeginIndex() + 1));
        assertNumEquals((10d + 20d) / 2, indicator2.getValue(data.getBeginIndex() + 2));
        assertNumEquals((20d + 30d) / 2, indicator2.getValue(data.getBeginIndex() + 3));

        Indicator<Num> indicator3 = getIndicator(new ClosePriceIndicator(data), 3);

        // unstable bars skipped, unpredictable results
        assertNumEquals((5d + 10d + 20d) / 3, indicator3.getValue(data.getBeginIndex() + 2));
        assertNumEquals((10d + 20d + 30d) / 3, indicator3.getValue(data.getBeginIndex() + 3));
    }

    private void randomAccessAfterFourAdditions() {
        Indicator<Num> indicator2 = getIndicator(new ClosePriceIndicator(data), 2);

        // unstable bars skipped, unpredictable results
        assertNumEquals((10d + 20d) / 2, indicator2.getValue(data.getBeginIndex() + 2));
        assertNumEquals((5d + 10d) / 2, indicator2.getValue(data.getBeginIndex() + 1));
        assertNumEquals((20d + 30d) / 2, indicator2.getValue(data.getBeginIndex() + 3));

        Indicator<Num> indicator3 = getIndicator(new ClosePriceIndicator(data), 3);

        // unstable bars skipped, unpredictable results
        assertNumEquals((10d + 20d + 30d) / 3, indicator3.getValue(data.getBeginIndex() + 3));
        assertNumEquals((5d + 10d + 20d) / 3, indicator3.getValue(data.getBeginIndex() + 2));
    }

    @Test
    public void whenBarCountIs1ResultShouldBeIndicatorValue() {
        data.barBuilder().closePrice(5.).endTime(getNextEndTime()).add();
        data.barBuilder().closePrice(5.).endTime(getNextEndTime()).add();

        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        for (int i = 0; i < data.getBarCount(); i++) {
            assertEquals(data.getBar(i).getClosePrice(), indicator.getValue(i));
        }
    }
}
