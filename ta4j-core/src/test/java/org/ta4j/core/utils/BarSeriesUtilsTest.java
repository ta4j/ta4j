/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

public class BarSeriesUtilsTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;
    private ZonedDateTime time;

    public BarSeriesUtilsTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    /**
     * Tests if the previous bar is replaced by newBar
     */
    @Test
    public void replaceBarIfChangedTest() {

        final List<Bar> bars = new ArrayList<>();
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault());

        final Bar bar0 = new MockBar(time, 1d, 2d, 3d, 4d, 5d, 0d, 7, numFunction);
        final Bar bar1 = new MockBar(time.plusDays(1), 1d, 1d, 1d, 1d, 1d, 1d, 1, numFunction);
        final Bar bar2 = new MockBar(time.plusDays(2), 2d, 2d, 2d, 2d, 2d, 2d, 2, numFunction);
        final Bar bar3 = new MockBar(time.plusDays(3), 3d, 3d, 3d, 3d, 3d, 3d, 3, numFunction);
        final Bar bar4 = new MockBar(time.plusDays(4), 3d, 4d, 4d, 5d, 6d, 4d, 4, numFunction);
        final Bar bar5 = new MockBar(time.plusDays(5), 5d, 5d, 5d, 5d, 5d, 5d, 5, numFunction);
        final Bar bar6 = new MockBar(time.plusDays(6), 6d, 6d, 6d, 6d, 6d, 6d, 6, numFunction);

        bars.add(bar0);
        bars.add(bar1);
        bars.add(bar2);
        bars.add(bar3);
        bars.add(bar4);
        bars.add(bar5);
        bars.add(bar6);

        series = new BaseBarSeriesBuilder().withNumTypeOf(numFunction).withName("Series Name").withBars(bars).build();

        final Bar newBar3 = new MockBar(bar3.getEndTime(), 1d, 1d, 1d, 1d, 1d, 1d, 33, numFunction);
        final Bar newBar5 = new MockBar(bar5.getEndTime(), 1d, 1d, 1d, 1d, 1d, 1d, 55, numFunction);

        // newBar3 must be replaced with bar3
        Bar replacedBar3 = BarSeriesUtils.replaceBarIfChanged(series, newBar3);
        // newBar5 must be replaced with bar5
        Bar replacedBar5 = BarSeriesUtils.replaceBarIfChanged(series, newBar5);

        // the replaced bar must be the same as the previous bar
        assertEquals(bar3, replacedBar3);
        assertEquals(bar5, replacedBar5);
        assertNotEquals(bar2, replacedBar3);
        assertNotEquals(bar6, replacedBar5);

        // the replaced bar must removed from the series
        assertNotEquals(series.getBar(3), replacedBar3);
        assertNotEquals(series.getBar(5), replacedBar5);

        // the new bar must be stored in the series
        assertEquals(series.getBar(3), newBar3);
        assertEquals(series.getBar(5), newBar5);

        // no bar was added
        assertEquals(7, series.getBarData().size());
        assertEquals(7, series.getBarCount());
    }

    @Test
    public void findMissingBarsTest() {

        final List<Bar> bars = new ArrayList<>();
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault());

        final Bar bar0 = new MockBar(time, 1d, 2d, 3d, 4d, 5d, 0d, 7, numFunction);
        final Bar bar1 = new MockBar(time.plusDays(1), 1d, 1d, 1d, 1d, 1d, 1d, 1, numFunction);
        final Bar bar4 = new MockBar(time.plusDays(4), 3d, 4d, 4d, 5d, 6d, 4d, 4, numFunction);
        final Bar bar5 = new MockBar(time.plusDays(5), 5d, 5d, 5d, 5d, 5d, 5d, 5, numFunction);
        final Bar bar7 = new MockBar(time.plusDays(7), 0, 0, 0, 0, 0, 0, 0, numFunction);
        Bar bar8 = BaseBar.builder(DoubleNum::valueOf, Double.class)
                .timePeriod(Duration.ofDays(1))
                .endTime(time.plusDays(8))
                .openPrice(NaN.NaN)
                .highPrice(NaN.NaN)
                .lowPrice(NaN.NaN)
                .closePrice(NaN.NaN)
                .volume(NaN.NaN)
                .build();

        bars.add(bar0);
        bars.add(bar1);
        bars.add(bar4);
        bars.add(bar5);
        bars.add(bar7);
        bars.add(bar8);

        series = new BaseBarSeriesBuilder().withNumTypeOf(numFunction).withName("Series Name").withBars(bars).build();

        // return the beginTime of each missing bar
        List<ZonedDateTime> missingBars = BarSeriesUtils.findMissingBars(series, false);

        // there must be 3 missing bars (bar2, bar3, bar6)
        assertEquals(missingBars.get(0), time.plusDays(2));
        assertEquals(missingBars.get(1), time.plusDays(3));
        assertEquals(missingBars.get(2), time.plusDays(6));
        // there must be 1 bar with invalid data (e.g. price, volume)
        assertEquals(missingBars.get(3), bar8.getEndTime());
    }

    @Test
    public void convertBarSeriesTest() {

        final Function<Number, Num> decimalNumFunction = DecimalNum::valueOf;
        final Function<Number, Num> doubleNumFunction = DoubleNum::valueOf;
        final Function<Number, Num> nanNumFunction = NaN::valueOf;

        final List<Bar> bars = new ArrayList<>();
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault());
        bars.add(new MockBar(time, 1d, 2d, 3d, 4d, 5d, 0d, 7, decimalNumFunction));
        bars.add(new MockBar(time.plusDays(1), 1d, 1d, 1d, 1d, 1d, 1d, 1, decimalNumFunction));
        bars.add(new MockBar(time.plusDays(2), 2d, 2d, 2d, 2d, 2d, 2d, 2, decimalNumFunction));

        final BarSeries decimalBarSeries = new BaseBarSeriesBuilder().withBars(bars)
                .withMaxBarCount(100)
                .withNumTypeOf(DecimalNum.class)
                .withName("useDecimalNum")
                .build();

        // convert barSeries with DecimalNum to barSeries with DoubleNum
        final BarSeries decimalToDoubleSeries = BarSeriesUtils.convertBarSeries(decimalBarSeries, doubleNumFunction);

        // convert barSeries with DoubleNum to barSeries with DecimalNum
        final BarSeries doubleToDecimalSeries = BarSeriesUtils.convertBarSeries(decimalToDoubleSeries,
                decimalNumFunction);

        // convert barSeries with DoubleNum to barSeries with NaNNum
        final BarSeries doubleToNaNSeries = BarSeriesUtils.convertBarSeries(decimalToDoubleSeries, nanNumFunction);

        assertEquals(decimalBarSeries.getFirstBar().getClosePrice().getClass(), DecimalNum.class);
        assertEquals(decimalToDoubleSeries.getFirstBar().getClosePrice().getClass(), DoubleNum.class);
        assertEquals(doubleToDecimalSeries.getFirstBar().getClosePrice().getClass(), DecimalNum.class);
        assertEquals(doubleToNaNSeries.getFirstBar().getClosePrice().getClass(), NaN.class);
    }

    @Test
    public void findOverlappingBarsTest() {

        final List<Bar> bars = new ArrayList<>();
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault());

        final Bar bar0 = new MockBar(time, 1d, 2d, 3d, 4d, 5d, 0d, 7, numFunction);
        final Bar bar1 = new MockBar(time, 1d, 1d, 1d, 1d, 1d, 1d, 1, numFunction);
        Bar bar8 = BaseBar.builder(DoubleNum::valueOf, Double.class)
                .timePeriod(Duration.ofDays(1))
                .endTime(time.plusDays(8))
                .openPrice(NaN.NaN)
                .highPrice(NaN.NaN)
                .lowPrice(NaN.NaN)
                .closePrice(NaN.NaN)
                .volume(NaN.NaN)
                .build();

        bars.add(bar0);
        bars.add(bar1);
        bars.add(bar8);

        series = new BaseBarSeriesBuilder().withNumTypeOf(numFunction).withName("Series Name").withBars(bars).build();
        List<Bar> overlappingBars = BarSeriesUtils.findOverlappingBars(series);

        // there must be 1 overlapping bars (bar1)
        assertEquals(overlappingBars.get(0).getBeginTime(), bar1.getBeginTime());
    }

    @Test
    public void addBars() {
        BarSeries barSeries = new BaseBarSeries("1day", numFunction);

        List<Bar> bars = new ArrayList<>();
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault());
        final Bar bar0 = new MockBar(time, 1d, 2d, 3d, 4d, 5d, 0d, 7, numFunction);
        final Bar bar1 = new MockBar(time.plusDays(1), 1d, 1d, 1d, 1d, 1d, 1d, 1, numFunction);
        final Bar bar2 = new MockBar(time.plusDays(2), 1d, 1d, 1d, 1d, 1d, 1d, 1, numFunction);
        bars.add(bar2);
        bars.add(bar0);
        bars.add(bar1);

        // add 3 bars to empty barSeries
        BarSeriesUtils.addBars(barSeries, bars);

        assertEquals(bar0.getEndTime(), barSeries.getFirstBar().getEndTime());
        assertEquals(bar2.getEndTime(), barSeries.getLastBar().getEndTime());

        final Bar bar3 = new MockBar(time.plusDays(3), 1d, 1d, 1d, 1d, 1d, 1d, 1, numFunction);
        bars.add(bar3);

        // add 1 bar to non empty barSeries
        BarSeriesUtils.addBars(barSeries, bars);
        assertEquals(bar3.getEndTime(), barSeries.getLastBar().getEndTime());
    }

    @Test
    public void sortBars() {
        time = ZonedDateTime.of(2019, 6, 1, 1, 1, 0, 0, ZoneId.systemDefault());

        final Bar bar0 = new MockBar(time, 1d, 2d, 3d, 4d, 5d, 0d, 7, numFunction);
        final Bar bar1 = new MockBar(time.plusDays(1), 1d, 1d, 1d, 1d, 1d, 1d, 1, numFunction);
        final Bar bar2 = new MockBar(time.plusDays(2), 1d, 1d, 1d, 1d, 1d, 1d, 1, numFunction);
        final Bar bar3 = new MockBar(time.plusDays(3), 1d, 1d, 1d, 1d, 1d, 1d, 1, numFunction);

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
