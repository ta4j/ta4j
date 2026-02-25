/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.mocks.MockBarBuilder;
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
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBarBuilder(numFactory).timePeriod(Duration.ofMinutes(1))
                .endTime(BASE_END_TIME)
                .openPrice(100d)
                .highPrice(101d)
                .lowPrice(99d)
                .closePrice(100d)
                .volume(10d)
                .amount(1000d)
                .trades(2)
                .build());
        bars.add(new MockBarBuilder(numFactory).timePeriod(Duration.ofMinutes(2))
                .endTime(BASE_END_TIME.plus(Duration.ofMinutes(2)))
                .openPrice(100d)
                .highPrice(102d)
                .lowPrice(98d)
                .closePrice(101d)
                .volume(11d)
                .amount(1111d)
                .trades(2)
                .build());
        bars.add(new MockBarBuilder(numFactory).timePeriod(Duration.ofMinutes(1))
                .endTime(BASE_END_TIME.plus(Duration.ofMinutes(3)))
                .openPrice(101d)
                .highPrice(103d)
                .lowPrice(100d)
                .closePrice(102d)
                .volume(12d)
                .amount(1224d)
                .trades(3)
                .build());
        return bars;
    }

    static List<Bar> barsFromClosePrices(NumFactory numFactory, double... closePrices) {
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < closePrices.length; i++) {
            double closePrice = closePrices[i];
            double openPrice = i == 0 ? closePrice : closePrices[i - 1];
            double highPrice = Math.max(openPrice, closePrice);
            double lowPrice = Math.min(openPrice, closePrice);
            double volume = 10d;
            bars.add(buildBar(numFactory, i + 1, openPrice, highPrice, lowPrice, closePrice, volume));
        }
        return bars;
    }

    private static List<Bar> fromRows(NumFactory numFactory, double[][] rows, List<Integer> endOffsetsInMinutes) {
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            double[] row = rows[i];
            bars.add(buildBar(numFactory, endOffsetsInMinutes.get(i), row[0], row[1], row[2], row[3], row[4]));
        }
        return bars;
    }

    private static Bar buildBar(NumFactory numFactory, int endOffsetInMinutes, double open, double high, double low,
            double close, double volume) {
        long trades = Math.max(1L, Math.round(volume / 5d));
        double amount = close * volume;
        return new MockBarBuilder(numFactory).timePeriod(SOURCE_PERIOD)
                .endTime(BASE_END_TIME.plus(Duration.ofMinutes(endOffsetInMinutes - 1L)))
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(volume)
                .amount(amount)
                .trades(trades)
                .build();
    }
}
