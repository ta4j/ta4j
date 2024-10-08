/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
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
