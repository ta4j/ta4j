/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

@RunWith(Parameterized.class)
public class BaseBarSeriesBuilderTest extends AbstractIndicatorTest<BarSeries, Num> {

    public BaseBarSeriesBuilderTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testBuildBigDecimal() {

        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();
        final var bar = series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime)
                .openPrice(BigDecimal.valueOf(101.0))
                .highPrice(BigDecimal.valueOf(103))
                .lowPrice(BigDecimal.valueOf(100))
                .closePrice(BigDecimal.valueOf(102))
                .trades(4)
                .volume(BigDecimal.valueOf(40))
                .amount(BigDecimal.valueOf(4020))
                .build();

        assertEquals(duration, bar.getTimePeriod());
        assertEquals(beginTime, bar.getBeginTime());
        assertEquals(endTime, bar.getEndTime());
        assertNumEquals(numOf(101.0), bar.getOpenPrice());
        assertNumEquals(numOf(103), bar.getHighPrice());
        assertNumEquals(numOf(100), bar.getLowPrice());
        assertNumEquals(numOf(102), bar.getClosePrice());
        assertEquals(4, bar.getTrades());
        assertNumEquals(numOf(40), bar.getVolume());
        assertNumEquals(numOf(4020), bar.getAmount());
    }

    @Test
    public void testBuildWithBars() {

        // When we create a series with predefined bars, we need to make sure that the
        // NumFactory of the series and the NumFactory of the bars are the same.

        final NumFactory doubleNumFactory = DoubleNumFactory.getInstance();

        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        // we build bars with DoubleNumFactory
        final var bar1 = new TimeBarBuilder(doubleNumFactory).timePeriod(duration)
                .endTime(endTime)
                .openPrice(BigDecimal.valueOf(101.0))
                .highPrice(BigDecimal.valueOf(103))
                .lowPrice(BigDecimal.valueOf(100))
                .closePrice(BigDecimal.valueOf(102))
                .trades(4)
                .volume(BigDecimal.valueOf(40))
                .amount(BigDecimal.valueOf(4020))
                .build();

        final var bars = new ArrayList<Bar>();
        bars.add(bar1);

        // User does not assign a numFactory, therefore use the numFactory of the bars
        // instead of the default
        final var series = new BaseBarSeriesBuilder().withBars(bars).build();

        // We add another bar with NumFactory assigned to series
        final var bar2 = series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plus(duration))
                .openPrice(BigDecimal.valueOf(101.0))
                .highPrice(BigDecimal.valueOf(103))
                .lowPrice(BigDecimal.valueOf(100))
                .closePrice(BigDecimal.valueOf(102))
                .trades(4)
                .volume(BigDecimal.valueOf(40))
                .amount(BigDecimal.valueOf(4020))
                .build();

        series.addBar(bar2);

        assertEquals(2, series.getBarCount());

        assertEquals(doubleNumFactory, series.numFactory());
        assertEquals(doubleNumFactory, bar1.getClosePrice().getNumFactory());
        assertEquals(doubleNumFactory, bar2.getClosePrice().getNumFactory());
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("unused")
    public void testBuildWithBarsAndWithNumFactory() {

        // When we create a series with predefined bars, we need to make sure that the
        // NumFactory of the series and the NumFactory of the bars are the same.

        final NumFactory doubleNumFactory = DoubleNumFactory.getInstance();
        final NumFactory decimalNumFactory = DecimalNumFactory.getInstance();

        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        // we build bars with DoubleNumFactory
        final var bar1 = new TimeBarBuilder(doubleNumFactory).timePeriod(duration)
                .endTime(endTime)
                .openPrice(BigDecimal.valueOf(101.0))
                .highPrice(BigDecimal.valueOf(103))
                .lowPrice(BigDecimal.valueOf(100))
                .closePrice(BigDecimal.valueOf(102))
                .trades(4)
                .volume(BigDecimal.valueOf(40))
                .amount(BigDecimal.valueOf(4020))
                .build();

        final var bars = new ArrayList<Bar>();
        bars.add(bar1);

        // The user explicitly assigns DecimalNumFactory to the series, but the bar
        // uses DoubleNumFactory, therefore throw an exception.
        final var series = new BaseBarSeriesBuilder().withNumFactory(decimalNumFactory).withBars(bars).build();
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("unused")
    public void testBuildWithBarsWithDifferentNumFactory() {

        // When we create a series with predefined bars, we need to make sure that the
        // NumFactory of all the bars are the same.

        final NumFactory doubleNumFactory = DoubleNumFactory.getInstance();
        final NumFactory decimalNumFactory = DecimalNumFactory.getInstance();

        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        // we build bars with DoubleNumFactory
        final var bar1 = new TimeBarBuilder(doubleNumFactory).timePeriod(duration)
                .endTime(endTime)
                .openPrice(BigDecimal.valueOf(101.0))
                .highPrice(BigDecimal.valueOf(103))
                .lowPrice(BigDecimal.valueOf(100))
                .closePrice(BigDecimal.valueOf(102))
                .trades(4)
                .volume(BigDecimal.valueOf(40))
                .amount(BigDecimal.valueOf(4020))
                .build();

        // we build bars with DecimalNumFactory
        final var bar2 = new TimeBarBuilder(decimalNumFactory).timePeriod(duration)
                .endTime(endTime)
                .openPrice(BigDecimal.valueOf(101.0))
                .highPrice(BigDecimal.valueOf(103))
                .lowPrice(BigDecimal.valueOf(100))
                .closePrice(BigDecimal.valueOf(102))
                .trades(4)
                .volume(BigDecimal.valueOf(40))
                .amount(BigDecimal.valueOf(4020))
                .build();

        final var bars = new ArrayList<Bar>();
        bars.add(bar1);
        bars.add(bar2);

        // bar1 and bar2 have different numFactories, therefore throw an exception.
        final var series = new BaseBarSeriesBuilder().withBars(bars).build();
    }
}
