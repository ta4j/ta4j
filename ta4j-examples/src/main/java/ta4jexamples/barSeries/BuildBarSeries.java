/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.barSeries;

import java.time.Duration;
import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;

public class BuildBarSeries {
    private static final Logger LOG = LogManager.getLogger(BuildBarSeries.class);

    /**
     * Calls different functions that shows how a BaseBarSeries could be created and
     * how Bars could be added
     *
     * @param args command line arguments (ignored)
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        BarSeries a = buildAndAddData();
        LOG.debug("a: {}", a.getBar(0).getClosePrice().getName());
        a = buildAndAddData();
        LOG.debug("a: {}", a.getBar(0).getClosePrice().getName());
        BarSeries b = buildWithDouble();
        BarSeries c = buildWithBigDecimal();
        BarSeries d = buildManually();
        BarSeries e = buildManuallyDoubleNum();
        BarSeries f = buildManuallyAndAddBarManually();
    }

    private static BarSeries buildAndAddData() {
        var series = new BaseBarSeriesBuilder().withName("mySeries").build();

        var endTime = Instant.now();
        // Instant endTime, Number openPrice, Number highPrice, Number lowPrice,
        // Number closePrice, volume
        addBars(series, endTime);

        return series;
    }

    private static void addBars(final BarSeries series, final Instant endTime) {
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime)
                .openPrice(105.42)
                .highPrice(112.99)
                .lowPrice(104.01)
                .closePrice(111.42)
                .volume(1337)
                .build());
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime.plus(Duration.ofDays(1)))
                .openPrice(111.43)
                .highPrice(112.83)
                .lowPrice(107.77)
                .closePrice(107.99)
                .volume(1234)
                .build());
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime.plus(Duration.ofDays(2)))
                .openPrice(107.90)
                .highPrice(117.50)
                .lowPrice(107.90)
                .closePrice(115.42)
                .volume(4242)
                .build());
    }

    private static BarSeries buildWithDouble() {
        var series = new BaseBarSeriesBuilder().withName("mySeries")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();

        var endTime = Instant.now();
        addBars(series, endTime);

        return series;
    }

    private static BarSeries buildWithBigDecimal() {
        var series = new BaseBarSeriesBuilder().withName("mySeries")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();

        var endTime = Instant.now();
        addBars(series, endTime);
        // ...

        return series;
    }

    private static BarSeries buildManually() {
        var series = new BaseBarSeriesBuilder().withName("mySeries").build(); // uses BigDecimalNum

        var endTime = Instant.now();
        addBars(series, endTime);
        // ...

        return series;
    }

    private static BarSeries buildManuallyDoubleNum() {
        var series = new BaseBarSeriesBuilder().withName("mySeries")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        var endTime = Instant.now();
        addBars(series, endTime);
        // ...

        return series;
    }

    private static BarSeries buildManuallyAndAddBarManually() {
        var series = new BaseBarSeriesBuilder().withName("mySeries")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();

        // create bars and add them to the series. The bars have the same Num type
        // as the series
        var endTime = Instant.now();
        Bar b1 = series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime)
                .openPrice(105.42)
                .highPrice(112.99)
                .lowPrice(104.01)
                .closePrice(111.42)
                .volume(1337.0)
                .build();
        Bar b2 = series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime.plus(Duration.ofDays(1)))
                .openPrice(111.43)
                .highPrice(112.83)
                .lowPrice(107.77)
                .closePrice(107.99)
                .volume(1234.0)
                .build();
        Bar b3 = series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime.plus(Duration.ofDays(2)))
                .openPrice(107.90)
                .highPrice(117.50)
                .lowPrice(107.90)
                .closePrice(115.42)
                .volume(4242.0)
                .build();
        // ...

        series.addBar(b1);
        series.addBar(b2);
        series.addBar(b3);

        return series;
    }
}
