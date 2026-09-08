/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.junit.Assert.assertTrue;

import java.util.SplittableRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

/**
 * Measures the sweep cost of the shared threshold profile over a
 * deterministically generated series and records the elapsed time for the log.
 * Excluded from the default and full gates via the {@code benchmark} tag.
 */
@Tag("benchmark")
class CandleThresholdSupportBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(CandleThresholdSupportBenchmarkTest.class);

    private static final int BARS = 50_000;

    @Test
    void sweepsAllPredicatesOverLongSeries() {
        BarSeries series = series();
        CandleThresholdSupport support = new CandleThresholdSupport(series);
        Indicator<Num> upperShadow = new UpperShadowIndicator(series);
        Indicator<Num> lowerShadow = new LowerShadowIndicator(series);
        Indicator<Num> body = new CandleBodyIndicator(series);
        Indicator<Num> range = new CandleRangeIndicator(series);

        final long start = System.nanoTime();
        int longBodies = 0;
        int shortBodies = 0;
        int dojis = 0;
        int longShadows = 0;
        int shortShadows = 0;
        int nearPairs = 0;
        for (int index = 5; index < BARS; index++) {
            if (support.isLongBody(index)) {
                longBodies++;
            }
            if (support.isShortBody(index)) {
                shortBodies++;
            }
            if (support.isDoji(index)) {
                dojis++;
            }
            if (support.isLongShadow(index, upperShadow) || support.isLongShadow(index, lowerShadow)) {
                longShadows++;
            }
            if (support.isShortShadow(index, upperShadow) || support.isShortShadow(index, lowerShadow)) {
                shortShadows++;
            }
            if (support.isNear(index, body, range)) {
                nearPairs++;
            }
        }
        final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue("expected some long bodies in a random sweep", longBodies > 0);
        assertTrue("expected some short bodies in a random sweep", shortBodies > 0);
        assertTrue("expected some long shadows in a random sweep", longShadows > 0);
        assertTrue("expected some short shadows in a random sweep", shortShadows > 0);
        LOG.info(
                "candle-threshold benchmark: {} bars -> {} ms, long {} short {} doji {} longShadow {} shortShadow {} near {}",
                BARS, elapsedMs, longBodies, shortBodies, dojis, longShadows, shortShadows, nearPairs);
    }

    private static BarSeries series() {
        final BarSeries series = new MockBarSeriesBuilder().build();
        final SplittableRandom random = new SplittableRandom(42);
        for (int index = 0; index < BARS; index++) {
            final double open = 90 + random.nextDouble() * 20;
            final double close = 90 + random.nextDouble() * 20;
            final double high = Math.max(open, close) + random.nextDouble() * 5;
            final double low = Math.min(open, close) - random.nextDouble() * 5;
            series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).add();
        }
        return series;
    }
}
