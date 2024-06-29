/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import java.time.ZonedDateTime;
import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class SMAIndicatorMovingSeriesTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SMAIndicatorMovingSeriesTest(Function<Number, Num> numFunction) {
        super((data, params) -> new SMAIndicator(data, (int) params[0]), numFunction);
    }

    private BarSeries data;

    @Before
    public void setUp() {
        data = new MockBarSeries(numFunction, 1, 2, 3, 4, 7);
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

    private void firstAddition() {
        data.addBar(new MockBar(ZonedDateTime.now(), 5., numFunction));
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
        data.addBar(new MockBar(data.isEmpty() ? ZonedDateTime.now() : data.getLastBar().getEndTime().plusHours(1), 10.,
                numFunction));
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
        data.addBar(new MockBar(data.isEmpty() ? ZonedDateTime.now() : data.getLastBar().getEndTime().plusHours(1), 20.,
                numFunction));
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
        data.addBar(new MockBar(data.isEmpty() ? ZonedDateTime.now() : data.getLastBar().getEndTime().plusHours(1), 30.,
                numFunction));
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
        data.addBar(new MockBar(data.isEmpty() ? ZonedDateTime.now() : data.getLastBar().getEndTime().plusHours(1), 5.,
                numFunction));
        data.addBar(new MockBar(data.isEmpty() ? ZonedDateTime.now() : data.getLastBar().getEndTime().plusHours(1), 5.,
                numFunction));

        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        for (int i = 0; i < data.getBarCount(); i++) {
            assertEquals(data.getBar(i).getClosePrice(), indicator.getValue(i));
        }
    }
}
