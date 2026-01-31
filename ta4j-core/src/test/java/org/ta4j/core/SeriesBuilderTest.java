/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;

public class SeriesBuilderTest {

    private final BaseBarSeriesBuilder seriesBuilder = new BaseBarSeriesBuilder()
            .withNumFactory(DecimalNumFactory.getInstance());

    @Test
    public void testBuilder() {

        // build a new empty unnamed bar series
        BarSeries defaultSeries = seriesBuilder.build();

        // build a new empty bar series using BigDecimal as delegate
        BarSeries defaultSeriesName = seriesBuilder.withName("default").build();

        BarSeries doubleSeries = seriesBuilder.withMaxBarCount(100)
                .withNumFactory(DoubleNumFactory.getInstance())
                .withName("useDoubleNum")
                .build();
        BarSeries precisionSeries = seriesBuilder.withMaxBarCount(100)
                .withNumFactory(DecimalNumFactory.getInstance())
                .withName("usePrecisionNum")
                .build();

        var now = Instant.now();
        for (int i = 1000; i >= 0; i--) {
            defaultSeries.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(now.minusSeconds(i))
                    .openPrice(i)
                    .closePrice(i)
                    .highPrice(i)
                    .lowPrice(i)
                    .volume(i)
                    .add();
            defaultSeriesName.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(now.minusSeconds(i))
                    .openPrice(i)
                    .closePrice(i)
                    .highPrice(i)
                    .lowPrice(i)
                    .volume(i)
                    .add();
            doubleSeries.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(now.minusSeconds(i))
                    .openPrice(i)
                    .closePrice(i)
                    .highPrice(i)
                    .lowPrice(i)
                    .volume(i)
                    .add();
            precisionSeries.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(now.minusSeconds(i))
                    .openPrice(i)
                    .closePrice(i)
                    .highPrice(i)
                    .lowPrice(i)
                    .volume(i)
                    .add();
        }

        assertNumEquals(0, defaultSeries.getBar(1000).getClosePrice());
        assertNumEquals(1000, defaultSeries.getBar(0).getClosePrice());
        assertEquals(defaultSeriesName.getName(), "default");
        assertNumEquals(99, doubleSeries.getBar(0).getClosePrice());
        assertNumEquals(99, precisionSeries.getBar(0).getClosePrice());
    }

    @Test
    public void testNumFunctions() {
        BarSeries series = seriesBuilder.withNumFactory(DoubleNumFactory.getInstance()).build();
        assertNumEquals(series.numFactory().numOf(12), DoubleNum.valueOf(12));
    }

    @Test
    public void testWrongNumType() {
        BarSeries series = seriesBuilder.withNumFactory(DecimalNumFactory.getInstance()).build();
        assertNumEquals(series.numFactory().numOf(12), DecimalNum.valueOf(12));
    }
}
