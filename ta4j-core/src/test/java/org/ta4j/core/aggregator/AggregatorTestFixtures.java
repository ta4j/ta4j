/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

final class AggregatorTestFixtures {

    private static final Duration SOURCE_PERIOD = Duration.ofMinutes(1);
    private static final Instant BASE_END_TIME = Instant.parse("2026-01-01T00:01:00Z");

    private AggregatorTestFixtures() {
    }

    static List<Bar> trendingBars(NumFactory numFactory) {
        double[][] rows = new double[][] { { 100d, 102d, 99d, 101d, 30d }, { 101d, 104d, 100d, 103d, 25d },
                { 103d, 106d, 102d, 105d, 28d }, { 105d, 108d, 104d, 107d, 22d }, { 107d, 110d, 106d, 109d, 26d },
                { 109d, 112d, 108d, 111d, 24d } };
        return fromRows(numFactory, rows, List.of(1, 2, 3, 4, 5, 6));
    }

    static List<Bar> volatileBars(NumFactory numFactory) {
        double[][] rows = new double[][] { { 100d, 108d, 94d, 96d, 40d }, { 96d, 112d, 95d, 110d, 45d },
                { 110d, 111d, 90d, 92d, 35d }, { 92d, 118d, 91d, 115d, 50d }, { 115d, 116d, 88d, 90d, 55d },
                { 90d, 120d, 89d, 118d, 60d } };
        return fromRows(numFactory, rows, List.of(1, 2, 3, 4, 5, 6));
    }

    static List<Bar> flatBars(NumFactory numFactory) {
        double[][] rows = new double[][] { { 100d, 101d, 99d, 100d, 20d }, { 100d, 101d, 99d, 100d, 20d },
                { 100d, 101d, 99d, 100d, 20d }, { 100d, 101d, 99d, 100d, 20d }, { 100d, 101d, 99d, 100d, 20d },
                { 100d, 101d, 99d, 100d, 20d } };
        return fromRows(numFactory, rows, List.of(1, 2, 3, 4, 5, 6));
    }

    static List<Bar> unevenIntervalBars(NumFactory numFactory) {
        double[][] rows = new double[][] { { 100d, 102d, 99d, 101d, 10d }, { 101d, 103d, 100d, 102d, 10d },
                { 102d, 104d, 101d, 103d, 10d } };
        return fromRows(numFactory, rows, List.of(1, 2, 5));
    }

    static List<Bar> inconsistentPeriodBars(NumFactory numFactory) {
        BarSeries series = newSeries(numFactory);
        addBar(series, Duration.ofMinutes(1), BASE_END_TIME, 100d, 101d, 99d, 100d, 10d);
        addBar(series, Duration.ofMinutes(2), BASE_END_TIME.plus(Duration.ofMinutes(2)), 100d, 102d, 98d, 101d, 11d);
        addBar(series, Duration.ofMinutes(1), BASE_END_TIME.plus(Duration.ofMinutes(3)), 101d, 103d, 100d, 102d, 12d);
        return List.copyOf(series.getBarData());
    }

    static List<Bar> barsFromClosePrices(NumFactory numFactory, double... closePrices) {
        BarSeries series = newSeries(numFactory);
        for (int i = 0; i < closePrices.length; i++) {
            double closePrice = closePrices[i];
            double openPrice = i == 0 ? closePrice : closePrices[i - 1];
            double highPrice = Math.max(openPrice, closePrice);
            double lowPrice = Math.min(openPrice, closePrice);
            double volume = 10d;
            Instant endTime = BASE_END_TIME.plus(Duration.ofMinutes(i));
            addBar(series, SOURCE_PERIOD, endTime, openPrice, highPrice, lowPrice, closePrice, volume);
        }
        return List.copyOf(series.getBarData());
    }

    static List<Bar> barsWithMissingClosePrice(NumFactory numFactory) {
        BarSeries series = newSeries(numFactory);
        addBar(series, SOURCE_PERIOD, BASE_END_TIME, 100d, 101d, 99d, 100d, 10d);
        addBar(series, SOURCE_PERIOD, BASE_END_TIME.plus(Duration.ofMinutes(1)), 100d, 102d, 98d, null, 10d);
        return List.copyOf(series.getBarData());
    }

    private static List<Bar> fromRows(NumFactory numFactory, double[][] rows, List<Integer> endOffsetsInMinutes) {
        BarSeries series = newSeries(numFactory);
        for (int i = 0; i < rows.length; i++) {
            double[] row = rows[i];
            Instant endTime = BASE_END_TIME.plus(Duration.ofMinutes(endOffsetsInMinutes.get(i) - 1L));
            addBar(series, SOURCE_PERIOD, endTime, row[0], row[1], row[2], row[3], row[4]);
        }
        return List.copyOf(series.getBarData());
    }

    private static BarSeries newSeries(NumFactory numFactory) {
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withName("aggregator-fixtures").build();
    }

    private static void addBar(BarSeries series, Duration period, Instant endTime, double open, double high, double low,
            Double close, double volume) {
        long trades = Math.max(1L, Math.round(volume / 5d));
        Num amount = close == null ? series.numFactory().zero() : series.numFactory().numOf(close * volume);
        BarBuilder barBuilder = series.barBuilder()
                .timePeriod(period)
                .endTime(endTime)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low);
        if (close == null) {
            barBuilder.closePrice((Num) null);
        } else {
            barBuilder.closePrice(close);
        }
        barBuilder.volume(volume).amount(amount).trades(trades).add();
    }
}
