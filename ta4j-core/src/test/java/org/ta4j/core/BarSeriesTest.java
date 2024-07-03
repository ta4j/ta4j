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
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

public class BarSeriesTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries defaultSeries;

    private BarSeries subSeries;

    private BarSeries emptySeries;

    private String defaultName;

    public BarSeriesTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        defaultName = "Series Name";

        defaultSeries = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withName(defaultName)
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();

        defaultSeries.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()))
                .closePrice(1d)
                .add();
        defaultSeries.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()))
                .closePrice(2d)
                .add();
        defaultSeries.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.of(2014, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault()))
                .closePrice(3d)
                .add();
        defaultSeries.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.of(2014, 6, 20, 0, 0, 0, 0, ZoneId.systemDefault()))
                .closePrice(4d)
                .add();
        defaultSeries.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault()))
                .closePrice(5d)
                .add();
        defaultSeries.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.of(2014, 6, 30, 0, 0, 0, 0, ZoneId.systemDefault()))
                .closePrice(6d)
                .add();

        subSeries = defaultSeries.getSubSeries(2, 5);
        emptySeries = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();

        Strategy strategy = new BaseStrategy(new FixedRule(0, 2, 3, 6), new FixedRule(1, 4, 7, 8));
        strategy.setUnstableBars(2); // Strategy would need a real test class

    }

    /**
     * Tests if the addBar(bar, boolean) function works correct.
     */
    @Test
    public void replaceBarTest() {
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.now(ZoneId.systemDefault()))
                .closePrice(1d)
                .build(), true);
        assertEquals(1, series.getBarCount());
        assertNumEquals(series.getLastBar().getClosePrice(), series.numFactory().one());

        series.addBar(series.barBuilder()
                .endTime(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(1))
                .closePrice(2d)
                .build(), false);
        series.addBar(series.barBuilder()
                .endTime(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(2))
                .closePrice(3d)
                .build(), false);
        assertEquals(3, series.getBarCount());

        assertNumEquals(series.getLastBar().getClosePrice(), series.numFactory().numOf(3));
        series.addBar(series.barBuilder()
                .endTime(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(3))
                .closePrice(4d)
                .build(), true);
        series.addBar(series.barBuilder()
                .endTime(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(4))
                .closePrice(5d)
                .build(), true);
        assertEquals(3, series.getBarCount());

        assertNumEquals(series.getLastBar().getClosePrice(), series.numFactory().numOf(5));
    }

    @Test
    public void getEndGetBeginGetBarCountIsEmptyTest() {

        // Default series
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(defaultSeries.getBarData().size() - 1, defaultSeries.getEndIndex());
        assertEquals(defaultSeries.getBarData().size(), defaultSeries.getBarCount());
        assertFalse(defaultSeries.isEmpty());
        // Constrained series
        assertEquals(0, subSeries.getBeginIndex());
        assertEquals(2, subSeries.getEndIndex());
        assertEquals(3, subSeries.getBarCount());
        assertFalse(subSeries.isEmpty());
        // Empty series
        assertEquals(-1, emptySeries.getBeginIndex());
        assertEquals(-1, emptySeries.getEndIndex());
        assertEquals(0, emptySeries.getBarCount());
        assertTrue(emptySeries.isEmpty());
    }

    @Test
    public void getBarDataTest() {
        // Constrained series
        assertNotEquals(defaultSeries.getBarData(), subSeries.getBarData());
        // Empty series
        assertEquals(0, emptySeries.getBarData().size());
    }

    @Test
    public void getSeriesPeriodDescriptionTest() {
        // Default series
        assertTrue(defaultSeries.getSeriesPeriodDescription()
                .endsWith(defaultSeries.getBarData()
                        .get(defaultSeries.getEndIndex())
                        .getEndTime()
                        .format(DateTimeFormatter.ISO_DATE_TIME)));
        assertTrue(defaultSeries.getSeriesPeriodDescription()
                .startsWith(defaultSeries.getBarData()
                        .get(defaultSeries.getBeginIndex())
                        .getEndTime()
                        .format(DateTimeFormatter.ISO_DATE_TIME)));
        // Constrained series
        assertTrue(subSeries.getSeriesPeriodDescription()
                .endsWith(defaultSeries.getBarData().get(4).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        assertTrue(subSeries.getSeriesPeriodDescription()
                .startsWith(defaultSeries.getBarData().get(2).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        // Empty series
        assertEquals("", emptySeries.getSeriesPeriodDescription());
    }

    @Test
    public void getNameTest() {
        assertEquals(defaultName, defaultSeries.getName());
        assertEquals(defaultName, subSeries.getName());
    }

    @Test
    public void getBarWithRemovedIndexOnMovingSeriesShouldReturnFirstRemainingBarTest() {
        Bar bar = defaultSeries.getBar(4);
        defaultSeries.setMaximumBarCount(2);

        assertSame(bar, defaultSeries.getBar(0));
        assertSame(bar, defaultSeries.getBar(1));
        assertSame(bar, defaultSeries.getBar(2));
        assertSame(bar, defaultSeries.getBar(3));
        assertSame(bar, defaultSeries.getBar(4));
        assertNotSame(bar, defaultSeries.getBar(5));
    }

    @Test
    public void modificationsOnOriginalListShouldNotAffectBarSeries() {
        defaultSeries.setMaximumBarCount(2);
        assertEquals(2, defaultSeries.getBarCount());
        assertNumEquals(5, defaultSeries.getBar(1).getClosePrice());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getBarWithNegativeIndexShouldThrowExceptionTest() {
        defaultSeries.getBar(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getBarWithIndexGreaterThanBarCountShouldThrowExceptionTest() {
        defaultSeries.getBar(10);
    }

    @Test
    public void getBarOnMovingSeriesTest() {
        Bar bar = defaultSeries.getBar(4);
        defaultSeries.setMaximumBarCount(2);
        assertEquals(bar, defaultSeries.getBar(4));
    }

    @Test
    public void subSeriesCreationTest() {
        BarSeries subSeries = defaultSeries.getSubSeries(2, 5);
        assertEquals(3, subSeries.getBarCount());
        assertEquals(defaultSeries.getName(), subSeries.getName());
        assertEquals(0, subSeries.getBeginIndex());
        assertEquals(defaultSeries.getBeginIndex(), subSeries.getBeginIndex());
        assertEquals(2, subSeries.getEndIndex());
        assertNotEquals(defaultSeries.getEndIndex(), subSeries.getEndIndex());
        assertEquals(3, subSeries.getBarCount());

        subSeries = defaultSeries.getSubSeries(0, 1000);
        assertEquals(0, subSeries.getBeginIndex());
        assertEquals(defaultSeries.getBarCount(), subSeries.getBarCount());
        assertEquals(defaultSeries.getEndIndex(), subSeries.getEndIndex());
    }

    @Test(expected = IllegalArgumentException.class)
    public void subSeriesCreationWithNegativeIndexTest() {
        defaultSeries.getSubSeries(-1000, 1000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void subSeriesWithWrongArgumentsTest() {
        defaultSeries.getSubSeries(10, 9);
    }

    @Test
    public void maximumBarCountOnConstrainedSeriesShouldNotThrowExceptionTest() {
        try {
            subSeries.setMaximumBarCount(10);
        } catch (Exception e) {
            Assert.fail("setMaximumBarCount onConstrained series should not throw Exception");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeMaximumBarCountShouldThrowExceptionTest() {
        defaultSeries.setMaximumBarCount(-1);
    }

    @Test
    public void setMaximumBarCountTest() {
        // Before
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(defaultSeries.getBarData().size() - 1, defaultSeries.getEndIndex());
        assertEquals(defaultSeries.getBarData().size(), defaultSeries.getBarCount());

        defaultSeries.setMaximumBarCount(3);

        // After
        assertEquals(3, defaultSeries.getBeginIndex());
        assertEquals(5, defaultSeries.getEndIndex());
        assertEquals(3, defaultSeries.getBarCount());
    }

    @Test(expected = NullPointerException.class)
    public void addNullBarShouldThrowExceptionTest() {
        defaultSeries.addBar(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addBarWithEndTimePriorToSeriesEndTimeShouldThrowExceptionTest() {
        defaultSeries.addBar(defaultSeries.barBuilder()
                .endTime(ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()))
                .closePrice(99d)
                .build());
    }

    @Test
    public void addBarTest() {
        defaultSeries = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();
        Bar bar1 = defaultSeries.barBuilder()
                .endTime(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()))
                .closePrice(1d)
                .build();

        Bar bar2 = defaultSeries.barBuilder()
                .endTime(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()))
                .closePrice(2d)
                .build();

        assertEquals(0, defaultSeries.getBarCount());
        assertEquals(-1, defaultSeries.getBeginIndex());
        assertEquals(-1, defaultSeries.getEndIndex());

        defaultSeries.addBar(bar1);
        assertEquals(1, defaultSeries.getBarCount());
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(0, defaultSeries.getEndIndex());

        defaultSeries.addBar(bar2);
        assertEquals(2, defaultSeries.getBarCount());
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(1, defaultSeries.getEndIndex());
    }

    @Test
    public void addPriceTest() {
        var cp = new ClosePriceIndicator(defaultSeries);
        var mxPrice = new HighPriceIndicator(defaultSeries);
        var mnPrice = new LowPriceIndicator(defaultSeries);
        var prevValue = new PreviousValueIndicator(cp, 1);

        Num adding1 = numOf(100);
        Num prevClose = defaultSeries.getBar(defaultSeries.getEndIndex() - 1).getClosePrice();
        Num currentMax = mxPrice.getValue(defaultSeries.getEndIndex());
        Num currentMin = mnPrice.getValue(defaultSeries.getEndIndex());
        Num currentClose = cp.getValue(defaultSeries.getEndIndex());

        assertNumEquals(currentClose, defaultSeries.getLastBar().getClosePrice());
        defaultSeries.addPrice(adding1);
        assertNumEquals(adding1, cp.getValue(defaultSeries.getEndIndex())); // adding1 is new close
        assertNull(currentMax);
        assertNumEquals(adding1, mxPrice.getValue(defaultSeries.getEndIndex())); // adding1 also new max
        assertNull(currentMin);
        assertNumEquals(adding1, mnPrice.getValue(defaultSeries.getEndIndex())); // adding1 also new min
        assertNumEquals(prevClose, prevValue.getValue(defaultSeries.getEndIndex())); // previous close stays

        Num adding2 = numOf(0);
        defaultSeries.addPrice(adding2);
        assertNumEquals(adding2, cp.getValue(defaultSeries.getEndIndex())); // adding2 is new close
        assertNumEquals(adding1, mxPrice.getValue(defaultSeries.getEndIndex())); // max stays 100
        assertNumEquals(adding2, mnPrice.getValue(defaultSeries.getEndIndex())); // min is new adding2
        assertNumEquals(prevClose, prevValue.getValue(defaultSeries.getEndIndex())); // previous close stays
    }

    /**
     * Tests if the {@link BaseBarSeries#addTrade(Number, Number)} method works
     * correct.
     */
    @Test
    public void addTradeTest() {
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();
        series.barBuilder().closePrice(1d).volume(0).amount(0).add();
        series.addTrade(200, 11.5);
        assertNumEquals(series.numFactory().numOf(200), series.getLastBar().getVolume());
        assertNumEquals(series.numFactory().numOf(11.5), series.getLastBar().getClosePrice());
        series.addTrade(BigDecimal.valueOf(200), BigDecimal.valueOf(100));
        assertNumEquals(series.numFactory().numOf(400), series.getLastBar().getVolume());
        assertNumEquals(series.numFactory().numOf(100), series.getLastBar().getClosePrice());
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongBarTypeDoubleTest() {
        var series = new BaseBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        series.addBar(new BaseBarBuilder().timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.now())
                .closePrice(DecimalNumFactory.getInstance().one())
                .build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongBarTypeBigDecimalTest() {
        var series = new BaseBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance()).build();
        series.addBar(new BaseBarBuilder().timePeriod(Duration.ofDays(1))
                .endTime(ZonedDateTime.now())
                .closePrice(DoubleNumFactory.getInstance().one())
                .build());
    }

    @Test
    public void subSeriesOfMaxBarCountSeriesTest() {
        final BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withName("Series with maxBar count")
                .withMaxBarCount(20)
                .build();
        final int timespan = 5;

        IntStream.range(0, 100).forEach(i -> {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(ZonedDateTime.now(ZoneId.systemDefault()).plusMinutes(i))
                    .openPrice(5)
                    .highPrice(7)
                    .lowPrice(1)
                    .closePrice(5)
                    .volume(i)
                    .add();
            int startIndex = Math.max(series.getBeginIndex(), series.getEndIndex() - timespan + 1);
            int endIndex = i + 1;
            final BarSeries subSeries = series.getSubSeries(startIndex, endIndex);
            assertEquals(subSeries.getBarCount(), endIndex - startIndex);

            final Bar subSeriesLastBar = subSeries.getLastBar();
            final Bar seriesLastBar = series.getLastBar();
            assertEquals(subSeriesLastBar.getVolume(), seriesLastBar.getVolume());
        });
    }
}
