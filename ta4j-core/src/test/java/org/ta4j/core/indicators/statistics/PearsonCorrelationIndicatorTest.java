/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PearsonCorrelationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private Indicator<Num> close, volume;

    public PearsonCorrelationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        int i = 20;
        var now = Instant.now();
        BarSeries data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        // close, volume
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(6).volume(100).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(7).volume(105).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(9).volume(130).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(12).volume(160).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(10).volume(130).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(95).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(13).volume(120).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(15).volume(180).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(12).volume(160).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(8).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(4).volume(200).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(3).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(4).volume(85).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(3).volume(70).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(5).volume(90).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(8).volume(100).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(9).volume(95).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(110).add();
        data.barBuilder().endTime(now.minusSeconds(i)).closePrice(10).volume(95).add();

        close = new ClosePriceIndicator(data);
        volume = new VolumeIndicator(data, 2);
    }

    @Test
    public void test() {
        var coef = new PearsonCorrelationIndicator(close, volume, 5);

        assertNumEquals(0.94947469058476818628408908843839, coef.getValue(1));
        assertNumEquals(0.9640797490298872, coef.getValue(2));
        assertNumEquals(0.9666189661412724, coef.getValue(3));
        assertNumEquals(0.9219, coef.getValue(4));
        assertNumEquals(0.9205, coef.getValue(5));
        assertNumEquals(0.4565, coef.getValue(6));
        assertNumEquals(-0.4622, coef.getValue(7));
        assertNumEquals(0.05747, coef.getValue(8));
        assertNumEquals(0.1442, coef.getValue(9));
        assertNumEquals(-0.1263, coef.getValue(10));
        assertNumEquals(-0.5345, coef.getValue(11));
        assertNumEquals(-0.7275, coef.getValue(12));
        assertNumEquals(0.1676, coef.getValue(13));
        assertNumEquals(0.2506, coef.getValue(14));
        assertNumEquals(-0.2938, coef.getValue(15));
        assertNumEquals(-0.3586, coef.getValue(16));
        assertNumEquals(0.1713, coef.getValue(17));
        assertNumEquals(0.9841, coef.getValue(18));
        assertNumEquals(0.9799, coef.getValue(19));
    }

    @Test
    public void expirationOfLargeValuesRebuildsAllRollingSums() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        for (int sign : new int[] { 1, -1 }) {
            Indicator<Num> large = new FixedIndicator<>(series, numOf(sign * 1e100), numOf(sign * 1e100), numOf(sign),
                    numOf(sign * 2), numOf(sign * 3), numOf(sign * 4));
            Indicator<Num> small = new FixedIndicator<>(series, numOf(1), numOf(1), numOf(1), numOf(2), numOf(3),
                    numOf(4));
            for (boolean swapped : new boolean[] { false, true }) {
                PearsonCorrelationIndicator correlation = swapped ? new PearsonCorrelationIndicator(small, large, 3)
                        : new PearsonCorrelationIndicator(large, small, 3);
                assertTrue(correlation.getValue(2).isNaN());
                assertNumEquals(numOf(-0.5 * sign), correlation.getValue(3), 1e-12);
                assertNumEquals(numOf(sign), correlation.getValue(4), 1e-12);
                assertNumEquals(numOf(sign), correlation.getValue(5), 1e-12);
            }
        }
    }

    @Test
    public void expirationWithNonzeroRoundedResidualRebuildsTheWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        Indicator<Num> x = new FixedIndicator<>(series, numOf(1e8), numOf(1e8), numOf(6), numOf(5), numOf(4), numOf(3));
        Indicator<Num> y = new FixedIndicator<>(series, numOf(1), numOf(1), numOf(6), numOf(5), numOf(4), numOf(3));
        PearsonCorrelationIndicator correlation = new PearsonCorrelationIndicator(x, y, 3);

        correlation.getValue(2);
        correlation.getValue(3);
        assertNumEquals(numFactory.one(), correlation.getValue(4), 1e-12);
        assertNumEquals(numFactory.one(), correlation.getValue(5), 1e-12);
    }

    @Test
    public void signedAdditionCancellationRebuildsTheWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        Indicator<Num> x = new FixedIndicator<>(series, numOf(1), numOf(1e20), numOf(-1e20), numOf(1), numOf(2));
        Indicator<Num> y = new FixedIndicator<>(series, numOf(1), numOf(1), numOf(1), numOf(2), numOf(3));
        PearsonCorrelationIndicator correlation = new PearsonCorrelationIndicator(x, y, 3);

        assertTrue(correlation.getValue(2).isNaN());
        Num scaled = correlation.getValue(3).multipliedBy(numOf(1e20));
        assertNumEquals(numFactory.one().dividedBy(numOf(3).sqrt()), scaled, 1e-12);
    }

    @Test
    public void signedProductCancellationRebuildsTheWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        Indicator<Num> x = new FixedIndicator<>(series, numOf(1), numOf(1e20), numOf(-1e20), numOf(100), numOf(99),
                numOf(100));
        Indicator<Num> y = new FixedIndicator<>(series, numOf(1), numOf(1), numOf(1), numOf(1), numOf(-1), numOf(0));
        PearsonCorrelationIndicator correlation = new PearsonCorrelationIndicator(x, y, 4);

        assertTrue(correlation.getValue(3).isNaN());
        Num scaled = correlation.getValue(4).multipliedBy(numOf(1e20));
        assertNumEquals(numOf(-394).dividedBy(numOf(96).sqrt()), scaled, 1e-12);
    }

    @Test
    public void exactIntegerCancellationRetainsTheSequentialFastPath() {
        int barCount = 19;
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[21]).build();
        Num[] values = new Num[21];
        for (int i = 0; i < values.length; i++) {
            values[i] = numOf(i % 2 == 0 ? 1 : -1);
        }
        CountingIndicator source = new CountingIndicator(series, values);
        PearsonCorrelationIndicator correlation = new PearsonCorrelationIndicator(source, source, barCount);

        correlation.getValue(18);
        source.resetReadCount();
        assertNumEquals(numFactory.one(), correlation.getValue(19), 1e-12);
        assertTrue(source.readCount() <= 4, "An exact sequential update rescanned the window");
    }

    @Test
    public void historicalReplacementAndExpirationUpdateCorrelation() {
        var series = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6)
                .build();
        var x = new ClosePriceIndicator(series);
        var y = new FixedIndicator<Num>(series, numOf(1), numOf(2), numOf(3), numOf(4), numOf(5), numOf(6));
        var correlation = new PearsonCorrelationIndicator(x, y, 3);
        for (int i = 0; i <= 3; i++) {
            correlation.getValue(i);
        }
        var old = series.getBar(1);
        series.replaceBar(1,
                series.barBuilder()
                        .timePeriod(old.getTimePeriod())
                        .endTime(old.getEndTime())
                        .openPrice(20)
                        .highPrice(20)
                        .lowPrice(20)
                        .closePrice(20)
                        .volume(old.getVolume())
                        .build());

        assertNumEquals(-16 / Math.sqrt(364), correlation.getValue(3));
        assertNumEquals(1, correlation.getValue(4));
    }

    /**
     * Verifies that the correlation normalizes by the number of values actually
     * iterated when bar eviction leaves fewer retained bars than the requested
     * window. The four retained pairs (1,1), (2,2), (3,1), (4,2) have the known
     * exact correlation 1/&radic;5 &asymp; 0.447214.
     */
    @Test
    public void normalizesByRetainedWindowSizeAfterEviction() {
        TraceTestLogger capture = new TraceTestLogger();
        capture.open();
        try {
            BarSeries pruned = new MockBarSeriesBuilder().withNumFactory(numFactory)
                    .withData(9, 9, 9, 9, 9, 9, 1, 2, 3, 4)
                    .withMaxBarCount(4)
                    .build();
            var x = new ClosePriceIndicator(pruned);
            var y = new FixedIndicator<Num>(pruned, numOf(7), numOf(7), numOf(7), numOf(7), numOf(7), numOf(7),
                    numOf(1), numOf(2), numOf(1), numOf(2));

            var coef = new PearsonCorrelationIndicator(x, y, 6);

            // Retained pairs at indices 6..9: (1,1), (2,2), (3,1), (4,2) -> r = 1/sqrt(5)
            assertNumEquals(numFactory.numOf(1).dividedBy(numFactory.numOf(5).sqrt()), coef.getValue(9));
        } finally {
            capture.close();
        }
    }

    @Test
    public void acceptsAlignedInputsFromSeparateSeries() {
        BarSeries xSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries ySeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var now = Instant.now();
        for (int j = 0; j < 5; j++) {
            xSeries.barBuilder().endTime(now.minusSeconds(2L * (4 - j))).closePrice(2 * (j + 1)).add();
            ySeries.barBuilder().endTime(now.minusSeconds(2L * (4 - j))).closePrice(5 * (j + 1)).add();
        }

        var coef = new PearsonCorrelationIndicator(new ClosePriceIndicator(xSeries), new ClosePriceIndicator(ySeries),
                5);

        // Perfectly linear pairs (2,5), (4,10), (6,15), (8,20), (10,25) -> r = 1
        assertNumEquals(1, coef.getValue(4));
    }

    private static final class CountingIndicator extends FixedIndicator<Num> {

        private int readCount;

        private CountingIndicator(BarSeries series, Num... values) {
            super(series, values);
        }

        @Override
        public Num getValue(int index) {
            readCount++;
            return super.getValue(index);
        }

        private int readCount() {
            return readCount;
        }

        private void resetReadCount() {
            readCount = 0;
        }
    }
}
