/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.pivotpoints;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.ZonedDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

@Tag("benchmark")
class StandardReversalIndicatorBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(StandardReversalIndicatorBenchmarkTest.class);
    private static final int BARS = 20_000;
    private static final int ITERATIONS = 10;

    @Test
    void benchmarkStandardReversalIndicator() {
        BarSeries series = createSeries(BARS);
        PivotPointIndicator pivotPointIndicator = new PivotPointIndicator(series, TimeLevel.DAY);

        StandardReversalIndicator r3 = new StandardReversalIndicator(pivotPointIndicator, PivotLevel.RESISTANCE_3);
        StandardReversalIndicator r2 = new StandardReversalIndicator(pivotPointIndicator, PivotLevel.RESISTANCE_2);
        StandardReversalIndicator r1 = new StandardReversalIndicator(pivotPointIndicator, PivotLevel.RESISTANCE_1);
        StandardReversalIndicator s1 = new StandardReversalIndicator(pivotPointIndicator, PivotLevel.SUPPORT_1);
        StandardReversalIndicator s2 = new StandardReversalIndicator(pivotPointIndicator, PivotLevel.SUPPORT_2);
        StandardReversalIndicator s3 = new StandardReversalIndicator(pivotPointIndicator, PivotLevel.SUPPORT_3);

        // Warmup
        for (int i = 0; i < series.getBarCount(); i++) {
            r3.getValue(i);
            r2.getValue(i);
            r1.getValue(i);
            s1.getValue(i);
            s2.getValue(i);
            s3.getValue(i);
        }

        long totalNano = 0;
        Num checksum = series.numFactory().zero();

        for (int iter = 0; iter < ITERATIONS; iter++) {
            PivotPointIndicator pp = new PivotPointIndicator(series, TimeLevel.DAY);
            StandardReversalIndicator[] indicators = new StandardReversalIndicator[] {
                    new StandardReversalIndicator(pp, PivotLevel.RESISTANCE_3),
                    new StandardReversalIndicator(pp, PivotLevel.RESISTANCE_2),
                    new StandardReversalIndicator(pp, PivotLevel.RESISTANCE_1),
                    new StandardReversalIndicator(pp, PivotLevel.SUPPORT_1),
                    new StandardReversalIndicator(pp, PivotLevel.SUPPORT_2),
                    new StandardReversalIndicator(pp, PivotLevel.SUPPORT_3) };

            long start = System.nanoTime();
            for (int i = 0; i < series.getBarCount(); i++) {
                for (StandardReversalIndicator ind : indicators) {
                    Num val = ind.getValue(i);
                    if (!val.isNaN()) {
                        checksum = checksum.plus(val);
                    }
                }
            }
            long elapsed = System.nanoTime() - start;
            totalNano += elapsed;
        }

        long avgMs = (totalNano / ITERATIONS) / 1_000_000;
        LOG.info("StandardReversalIndicator benchmark over {} bars ({} iterations avg): {} ms, checksum: {}", BARS,
                ITERATIONS, avgMs, checksum);
        System.out.printf("StandardReversalIndicator benchmark over %d bars (%d iterations avg): %d ms%n", BARS,
                ITERATIONS, avgMs);
        assertNotNull(checksum);
    }

    private BarSeries createSeries(int count) {
        BarSeries series = new MockBarSeriesBuilder().build();
        ZonedDateTime now = ZonedDateTime.now();
        for (int i = 0; i < count; i++) {
            double open = 100 + (i % 10);
            double high = open + 5;
            double low = open - 5;
            double close = open + 1;
            series.barBuilder()
                    .endTime(now.plusMinutes(i * 5).toInstant())
                    .openPrice(open)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .volume(1000)
                    .add();
        }
        return series;
    }
}
