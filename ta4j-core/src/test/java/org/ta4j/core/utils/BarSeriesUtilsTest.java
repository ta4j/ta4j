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
package org.ta4j.core.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BarSeriesUtilsTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;
    private Instant time;

    public BarSeriesUtilsTest(final NumFactory numFactory) {
        super(numFactory);
    }

    /**
     * Tests if the previous bar is replaced by newBar
     */
    @Test
    public void replaceBarIfChangedTest() {

        final List<Bar> bars = new ArrayList<>();
        this.time = Instant.parse("2019-06-01T01:01:00Z");

        final Bar bar0 = new MockBarBuilder(numFactory).endTime(time)
                .openPrice(1d)
                .closePrice(2d)
                .highPrice(3d)
                .lowPrice(4d)
                .amount(5d)
                .volume(0d)
                .trades(7)
                .build();

        final Bar bar1 = new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(1)))
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .amount(1d)
                .volume(1d)
                .trades(1)
                .build();

        final Bar bar2 = new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(2)))
                .openPrice(2d)
                .closePrice(2d)
                .highPrice(2d)
                .lowPrice(2d)
                .amount(2d)
                .volume(2d)
                .trades(2)
                .build();

        final Bar bar3 = new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(3)))
                .openPrice(3d)
                .closePrice(3d)
                .highPrice(3d)
                .lowPrice(3d)
                .amount(3d)
                .volume(3d)
                .trades(3)
                .build();

        final Bar bar4 = new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(4)))
                .openPrice(3d)
                .closePrice(4d)
                .highPrice(5d)
                .lowPrice(6d)
                .amount(4d)
                .volume(4d)
                .trades(4)
                .build();

        final Bar bar5 = new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(5)))
                .openPrice(4d)
                .closePrice(5d)
                .highPrice(5d)
                .lowPrice(5d)
                .amount(5d)
                .volume(5d)
                .trades(5)
                .build();

        final Bar bar6 = new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(6)))
                .openPrice(6d)
                .closePrice(6d)
                .highPrice(6d)
                .lowPrice(6d)
                .amount(6d)
                .volume(6d)
                .trades(6)
                .build();

        bars.add(bar0);
        bars.add(bar1);
        bars.add(bar2);
        bars.add(bar3);
        bars.add(bar4);
        bars.add(bar5);
        bars.add(bar6);

        this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
                .withName("Series Name")
                .withBars(bars)
                .build();

        final var newBar3 = this.series.barBuilder()
                .endTime(bar3.getEndTime())
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .volume(1d)
                .trades(33)
                .build();

        final Bar newBar5 = this.series.barBuilder()
                .endTime(bar5.getEndTime())
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .amount(1d)
                .volume(1d)
                .trades(55)
                .build();

        // newBar3 must be replaced with bar3
        final Bar replacedBar3 = BarSeriesUtils.replaceBarIfChanged(this.series, newBar3);
        // newBar5 must be replaced with bar5
        final Bar replacedBar5 = BarSeriesUtils.replaceBarIfChanged(this.series, newBar5);

        // the replaced bar must be the same as the previous bar
        assertEquals(bar3, replacedBar3);
        assertEquals(bar5, replacedBar5);
        assertNotEquals(bar2, replacedBar3);
        assertNotEquals(bar6, replacedBar5);

        // the replaced bar must removed from the series
        assertNotEquals(this.series.getBar(3), replacedBar3);
        assertNotEquals(this.series.getBar(5), replacedBar5);

        // the new bar must be stored in the series
        assertEquals(this.series.getBar(3), newBar3);
        assertEquals(this.series.getBar(5), newBar5);

        // no bar was added
        assertEquals(7, this.series.getBarData().size());
        assertEquals(7, this.series.getBarCount());
    }

    @Test
    public void findMissingBarsTest() {

        final List<Bar> bars = new ArrayList<>();
        this.time = Instant.parse("2019-06-01T01:01:00Z");

        final Bar bar0 = new MockBarBuilder(this.numFactory).endTime(this.time)
                .openPrice(1d)
                .closePrice(2d)
                .highPrice(3d)
                .lowPrice(4d)
                .amount(5d)
                .volume(0d)
                .trades(7)
                .build();

        final Bar bar1 = new MockBarBuilder(this.numFactory).endTime(this.time.plus(Duration.ofDays(1)))
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .amount(1d)
                .volume(1d)
                .trades(1)
                .build();

        final Bar bar4 = new MockBarBuilder(this.numFactory).endTime(this.time.plus(Duration.ofDays(4)))
                .openPrice(3d)
                .closePrice(4d)
                .highPrice(4d)
                .lowPrice(5d)
                .amount(6d)
                .volume(4d)
                .trades(4)
                .build();

        final Bar bar5 = new MockBarBuilder(this.numFactory).endTime(this.time.plus(Duration.ofDays(5)))
                .openPrice(5d)
                .closePrice(5d)
                .highPrice(5d)
                .lowPrice(5d)
                .amount(5d)
                .volume(5d)
                .trades(5)
                .build();

        final Bar bar7 = new MockBarBuilder(this.numFactory).endTime(this.time.plus(Duration.ofDays(7)))
                .openPrice(0d)
                .closePrice(0d)
                .highPrice(0d)
                .lowPrice(0d)
                .amount(0d)
                .volume(0d)
                .trades(0)
                .build();

        final Bar bar8 = new MockBarBuilder(this.numFactory).timePeriod(Duration.ofDays(1))
                .endTime(this.time.plus(Duration.ofDays(8)))
                .openPrice(NaN.NaN)
                .highPrice(NaN.NaN)
                .lowPrice(NaN.NaN)
                .closePrice(NaN.NaN)
                .volume(NaN.NaN)
                .build();

        this.series = new BaseBarSeriesBuilder().withNumFactory(this.numFactory).withName("Series Name").build();
        bars.add(bar0);
        bars.add(bar1);
        bars.add(bar4);
        bars.add(bar5);
        bars.add(bar7);
        bars.add(bar8);

        bars.forEach(series::addBar);

        // return the beginTime of each missing bar
        final List<Instant> missingBars = BarSeriesUtils.findMissingBars(this.series, false);

        // there must be 3 missing bars (bar2, bar3, bar6)
        assertEquals(missingBars.get(0), this.time.plus(Duration.ofDays(2)));
        assertEquals(missingBars.get(1), this.time.plus(Duration.ofDays(3)));
        assertEquals(missingBars.get(2), this.time.plus(Duration.ofDays(6)));
        // there must be 1 bar with invalid data (e.g. price, volume)
        assertEquals(missingBars.get(3), bar8.getEndTime());
    }

    @Test
    public void convertBarSeriesTest() {
        final BarSeries decimalBarSeries = new MockBarSeriesBuilder().withMaxBarCount(100)
                .withNumFactory(DecimalNumFactory.getInstance())
                .withName("useDecimalNum")
                .build();

        decimalBarSeries.barBuilder()
                .openPrice(1d)
                .closePrice(2d)
                .highPrice(4d)
                .lowPrice(5d)
                .volume(0d)
                .amount(0)
                .trades(7)
                .add();
        decimalBarSeries.barBuilder()
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .volume(1d)
                .amount(0)
                .trades(1)
                .add();
        decimalBarSeries.barBuilder()
                .openPrice(2d)
                .closePrice(2d)
                .highPrice(2d)
                .lowPrice(2d)
                .volume(2d)
                .amount(0)
                .trades(2)
                .add();

        // convert barSeries with DecimalNum to barSeries with DoubleNum
        final BarSeries decimalToDoubleSeries = BarSeriesUtils.convertBarSeries(decimalBarSeries,
                DoubleNumFactory.getInstance());

        // convert barSeries with DoubleNum to barSeries with DecimalNum
        final BarSeries doubleToDecimalSeries = BarSeriesUtils.convertBarSeries(decimalToDoubleSeries,
                DecimalNumFactory.getInstance());

        assertEquals(DecimalNum.class, decimalBarSeries.getFirstBar().getClosePrice().getClass());
        assertEquals(DoubleNum.class, decimalToDoubleSeries.getFirstBar().getClosePrice().getClass());
        assertEquals(DecimalNum.class, doubleToDecimalSeries.getFirstBar().getClosePrice().getClass());
    }

    @Test
    public void findOverlappingBarsTest() {

        final List<Bar> bars = new ArrayList<>();
        this.time = Instant.parse("2019-06-01T01:01:00Z");

        final Bar bar0 = new MockBarBuilder(numFactory).endTime(time)
                .openPrice(1d)
                .closePrice(2d)
                .highPrice(3d)
                .lowPrice(4d)
                .amount(5d)
                .volume(0d)
                .trades(7)
                .build();

        final Bar bar1 = new MockBarBuilder(numFactory).endTime(time)
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .amount(1d)
                .volume(1d)
                .trades(1)
                .build();

        final Bar bar8 = new MockBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(time.plus(Duration.ofDays(3)))
                .openPrice(NaN.NaN)
                .closePrice(NaN.NaN)
                .highPrice(NaN.NaN)
                .lowPrice(NaN.NaN)
                .amount(NaN.NaN)
                .volume(NaN.NaN)
                .build();

        bars.add(bar0);
        bars.add(bar1);
        bars.add(bar8);

        this.series = new BaseBarSeriesBuilder().withNumFactory(this.numFactory)
                .withName("Series Name")
                .withBars(bars)
                .build();
        final List<Bar> overlappingBars = BarSeriesUtils.findOverlappingBars(this.series);

        // there must be 1 overlapping bars (bar1)
        assertEquals(overlappingBars.get(0).getBeginTime(), bar1.getBeginTime());
    }

    @Test
    public void addBars() {
        final var barSeries = new MockBarSeriesBuilder().withName("1day").build();

        final List<Bar> bars = new ArrayList<>();
        this.time = Instant.parse("2019-06-01T01:01:00Z");

        final Bar bar0 = barSeries.barBuilder()
                .endTime(time)
                .openPrice(1d)
                .closePrice(2d)
                .highPrice(3d)
                .lowPrice(4d)
                .amount(5d)
                .volume(0d)
                .trades(7)
                .build();

        final Bar bar1 = barSeries.barBuilder()
                .endTime(time.plus(Duration.ofDays(1)))
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .amount(1d)
                .volume(1d)
                .trades(1)
                .build();

        final Bar bar2 = barSeries.barBuilder()
                .endTime(time.plus(Duration.ofDays(2)))
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .amount(1d)
                .volume(1d)
                .trades(1)
                .build();

        bars.add(bar2);
        bars.add(bar0);
        bars.add(bar1);

        // add 3 bars to empty barSeries
        BarSeriesUtils.addBars(barSeries, bars);

        assertEquals(bar0.getEndTime(), barSeries.getFirstBar().getEndTime());
        assertEquals(bar2.getEndTime(), barSeries.getLastBar().getEndTime());

        final Bar bar3 = barSeries.barBuilder()
                .endTime(time.plus(Duration.ofDays(3)))
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .amount(1d)
                .volume(1d)
                .trades(1)
                .build();

        bars.add(bar3);

        // add 1 bar to non empty barSeries
        BarSeriesUtils.addBars(barSeries, bars);
        assertEquals(bar3.getEndTime(), barSeries.getLastBar().getEndTime());
    }

    @Test
    public void sortBars() {
        this.time = Instant.parse("2019-06-01T01:01:00Z");

        final Bar bar0 = new MockBarBuilder(numFactory).endTime(time)
                .openPrice(1d)
                .closePrice(2d)
                .highPrice(3d)
                .lowPrice(4d)
                .amount(5d)
                .volume(0d)
                .trades(7)
                .build();

        final Bar bar1 = new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(1)))
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .amount(1d)
                .volume(1d)
                .trades(1)
                .build();

        final Bar bar2 = new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(2)))
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .amount(1d)
                .volume(1d)
                .trades(1)
                .build();

        final Bar bar3 = new MockBarBuilder(numFactory).endTime(time.plus(Duration.ofDays(3)))
                .openPrice(1d)
                .closePrice(1d)
                .highPrice(1d)
                .lowPrice(1d)
                .amount(1d)
                .volume(1d)
                .trades(1)
                .build();

        final List<Bar> sortedBars = new ArrayList<>();
        sortedBars.add(bar0);
        sortedBars.add(bar1);
        sortedBars.add(bar2);
        sortedBars.add(bar3);

        BarSeriesUtils.sortBars(sortedBars);
        assertEquals(bar0.getEndTime(), sortedBars.get(0).getEndTime());
        assertEquals(bar1.getEndTime(), sortedBars.get(1).getEndTime());
        assertEquals(bar2.getEndTime(), sortedBars.get(2).getEndTime());
        assertEquals(bar3.getEndTime(), sortedBars.get(3).getEndTime());

        final List<Bar> unsortedBars = new ArrayList<>();
        unsortedBars.add(bar3);
        unsortedBars.add(bar2);
        unsortedBars.add(bar1);
        unsortedBars.add(bar0);

        BarSeriesUtils.sortBars(unsortedBars);
        assertEquals(bar0.getEndTime(), unsortedBars.get(0).getEndTime());
        assertEquals(bar1.getEndTime(), unsortedBars.get(1).getEndTime());
        assertEquals(bar2.getEndTime(), unsortedBars.get(2).getEndTime());
        assertEquals(bar3.getEndTime(), unsortedBars.get(3).getEndTime());

        final List<Bar> unsortedBars2 = new ArrayList<>();
        unsortedBars2.add(bar2);
        unsortedBars2.add(bar1);
        unsortedBars2.add(bar3);
        unsortedBars2.add(bar0);

        BarSeriesUtils.sortBars(unsortedBars2);
        assertEquals(bar0.getEndTime(), unsortedBars2.get(0).getEndTime());
        assertEquals(bar1.getEndTime(), unsortedBars2.get(1).getEndTime());
        assertEquals(bar2.getEndTime(), unsortedBars2.get(2).getEndTime());
        assertEquals(bar3.getEndTime(), unsortedBars2.get(3).getEndTime());

        Collections.shuffle(unsortedBars2);
        BarSeriesUtils.sortBars(unsortedBars2);
        assertEquals(bar0.getEndTime(), unsortedBars2.get(0).getEndTime());
        assertEquals(bar1.getEndTime(), unsortedBars2.get(1).getEndTime());
        assertEquals(bar2.getEndTime(), unsortedBars2.get(2).getEndTime());
        assertEquals(bar3.getEndTime(), unsortedBars2.get(3).getEndTime());
    }
}
