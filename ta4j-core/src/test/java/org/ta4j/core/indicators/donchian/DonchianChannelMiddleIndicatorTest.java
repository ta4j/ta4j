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
package org.ta4j.core.indicators.donchian;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;

public class DonchianChannelMiddleIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;

    public DonchianChannelMiddleIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        ZonedDateTime startDateTime = ZonedDateTime.now();
        List<Bar> bars = new ArrayList<>();

        bars.add(new BaseBar(Duration.ofHours(1), startDateTime, 100d, 105d, 95d, 100d, 0d, 0, 0, this::numOf));
        bars.add(new BaseBar(Duration.ofHours(1), startDateTime.plusHours(1), 105, 110, 100, 105, 0d, 0, 0,
                this::numOf));
        bars.add(new BaseBar(Duration.ofHours(1), startDateTime.plusHours(2), 110, 115, 105, 110, 0d, 0, 0,
                this::numOf));
        bars.add(new BaseBar(Duration.ofHours(1), startDateTime.plusHours(3), 115, 120, 110, 115, 0d, 0, 0,
                this::numOf));
        bars.add(new BaseBar(Duration.ofHours(1), startDateTime.plusHours(4), 120, 125, 115, 120, 0d, 0, 0,
                this::numOf));
        bars.add(new BaseBar(Duration.ofHours(1), startDateTime.plusHours(5), 115, 120, 110, 115, 0d, 0, 0,
                this::numOf));
        bars.add(new BaseBar(Duration.ofHours(1), startDateTime.plusHours(6), 110, 115, 105, 110, 0d, 0, 0,
                this::numOf));
        bars.add(new BaseBar(Duration.ofHours(1), startDateTime.plusHours(7), 105, 110, 100, 105, 0d, 0, 0,
                this::numOf));
        bars.add(
                new BaseBar(Duration.ofHours(1), startDateTime.plusHours(8), 100, 105, 95, 100, 0d, 0, 0, this::numOf));

        this.series = new BaseBarSeries("DonchianChannelMiddleIndicatorTestSeries", bars);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testGetValue() {
        DonchianChannelMiddleIndicator subject = new DonchianChannelMiddleIndicator(series, 3);

        assertEquals(numOf(100), subject.getValue(0));
        assertEquals(numOf(102.5), subject.getValue(1));
        assertEquals(numOf(105), subject.getValue(2));
        assertEquals(numOf(110), subject.getValue(3));
        assertEquals(numOf(115), subject.getValue(4));
        assertEquals(numOf(117.5), subject.getValue(5));
        assertEquals(numOf(115), subject.getValue(6));
        assertEquals(numOf(110), subject.getValue(7));
        assertEquals(numOf(105), subject.getValue(8));

    }
}
