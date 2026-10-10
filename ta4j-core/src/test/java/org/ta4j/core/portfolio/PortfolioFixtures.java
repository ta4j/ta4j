/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.Instant;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/** Daily flat-OHLC bar series shared by the portfolio tests. */
final class PortfolioFixtures {

    static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private PortfolioFixtures() {
    }

    static BarSeries series(String name, double... closes) {
        return series(name, DoubleNumFactory.getInstance(), closes);
    }

    static BarSeries series(String name, NumFactory numFactory, double... closes) {
        String[] values = new String[closes.length];
        for (int i = 0; i < closes.length; i++) {
            values[i] = Double.toString(closes[i]);
        }
        return series(name, numFactory, 0, values);
    }

    /**
     * Builds consecutive daily bars ending at {@code START + firstDay + i} days.
     */
    static BarSeries series(String name, NumFactory numFactory, int firstDay, String... closes) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).withNumFactory(numFactory).build();
        for (int i = 0; i < closes.length; i++) {
            Num close = numFactory.numOf(closes[i]);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(START.plus(Duration.ofDays(firstDay + i)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(numFactory.zero())
                    .add();
        }
        return series;
    }

    static void assertNumClose(double expected, Num actual, double delta) {
        assertEquals(expected, actual.doubleValue(), delta);
    }
}
