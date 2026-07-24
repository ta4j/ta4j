/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.statistics.SampleType;
import org.ta4j.core.num.Num;

public class PortfolioCorrelationsTest {

    @Test
    public void buildsClosePriceCorrelationMatrixMatchingPandasCorr() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");
        PortfolioAsset gamma = PortfolioAsset.of("GAMMA");
        AlignedPortfolioSeries series = AlignedPortfolioSeries
                .of(List.of(new PortfolioSeries(alpha, series("alpha", start, 10, 11, 12, 11, 13)),
                        new PortfolioSeries(beta, series("beta", start, 20, 21, 19, 22, 24)),
                        new PortfolioSeries(gamma, series("gamma", start, 8, 7, 7.5, 6, 6.5))));

        PortfolioCorrelations.CorrelationMatrix matrix = PortfolioCorrelations.priceMatrix(series);

        assertEquals(4, matrix.index());
        assertEquals(5, matrix.barCount());
        assertEquals(4, matrix.getCountOfUnstableBars());
        assertTrue(matrix.isStable());
        assertNumEquals(0.5243548655, matrix.coefficient(alpha, beta));
        assertNumEquals(-0.4160251472, matrix.coefficient(alpha, gamma));
        assertNumEquals(-0.7397954429, matrix.coefficient(beta, gamma));
    }

    @Test
    public void buildsSimpleReturnCorrelationMatrixMatchingPandasPctChangeCorr() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");
        PortfolioAsset gamma = PortfolioAsset.of("GAMMA");
        BarSeries alphaSeries = series("alpha", start, closesFromSimpleReturns(100, 0.10, -0.05, 0.20, 0.03));
        BarSeries betaSeries = series("beta", start, closesFromSimpleReturns(200, 0.20, -0.10, 0.40, 0.06));
        BarSeries gammaSeries = series("gamma", start, closesFromSimpleReturns(300, -0.10, 0.05, -0.20, -0.03));
        AlignedPortfolioSeries series = AlignedPortfolioSeries.of(List.of(new PortfolioSeries(alpha, alphaSeries),
                new PortfolioSeries(beta, betaSeries), new PortfolioSeries(gamma, gammaSeries)));

        PortfolioCorrelations.CorrelationMatrix matrix = PortfolioCorrelations.simpleReturnMatrix(series);

        assertEquals(4, matrix.index());
        assertEquals(4, matrix.barCount());
        assertEquals(4, matrix.getCountOfUnstableBars());
        assertTrue(matrix.isStable());
        assertNumEquals(1d, matrix.coefficient(alpha, beta));
        assertNumEquals(-1d, matrix.coefficient(alpha, gamma));
        assertNumEquals(-1d, matrix.coefficient(beta, gamma));
    }

    @Test
    public void buildsSymmetricLogReturnCorrelationMatrix() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");
        PortfolioAsset gamma = PortfolioAsset.of("GAMMA");
        BarSeries alphaSeries = series("alpha", start, closesFromLogReturns(100, 0.10, -0.05, 0.20, 0.03));
        BarSeries betaSeries = series("beta", start, closesFromLogReturns(200, 0.20, -0.10, 0.40, 0.06));
        BarSeries gammaSeries = series("gamma", start, closesFromLogReturns(300, -0.10, 0.05, -0.20, -0.03));
        AlignedPortfolioSeries series = AlignedPortfolioSeries.of(List.of(new PortfolioSeries(alpha, alphaSeries),
                new PortfolioSeries(beta, betaSeries), new PortfolioSeries(gamma, gammaSeries)));

        PortfolioCorrelations.CorrelationMatrix matrix = PortfolioCorrelations.logReturnMatrix(series, 4);

        assertEquals(List.of(alpha, beta, gamma), matrix.assets());
        assertEquals(4, matrix.index());
        assertEquals(4, matrix.barCount());
        assertEquals(4, matrix.getCountOfUnstableBars());
        assertEquals(SampleType.POPULATION, matrix.sampleType());
        assertTrue(matrix.isStable());
        assertNumEquals(1, matrix.coefficient(alpha, alpha));
        assertNumEquals(1d, matrix.coefficient(alpha, beta));
        assertNumEquals(-1d, matrix.coefficient(alpha, gamma));
        assertNumEquals(matrix.coefficient(beta, gamma), matrix.coefficient(gamma, beta), 0.0001);
        assertEquals(3, matrix.pairs().size());
        assertEquals(alpha, matrix.pairs().getFirst().firstAsset());
        assertEquals(beta, matrix.pairs().getFirst().secondAsset());
        assertNumEquals(1d, matrix.pairs().getFirst().absoluteCoefficient());
    }

    @Test
    public void usesAlignedEndTimeTimelineWhenSourceIndexesDiffer() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");
        BarSeries alphaSeries = series("alpha", start, new int[] { 0, 1, 2, 3, 4 },
                new double[] { 100, 110, 500, 121, 108.9 });
        BarSeries betaSeries = series("beta", start, new int[] { 0, 1, 3, 4 }, new double[] { 50, 55, 60.5, 54.45 });
        AlignedPortfolioSeries series = AlignedPortfolioSeries
                .of(List.of(new PortfolioSeries(alpha, alphaSeries), new PortfolioSeries(beta, betaSeries)));

        PortfolioCorrelations.CorrelationMatrix matrix = PortfolioCorrelations.logReturnMatrix(series, 3);

        assertTrue(matrix.isStable());
        assertNumEquals(1d, matrix.coefficient(alpha, beta));
    }

    @Test
    public void marksEarlyWindowUnstableWithoutRejectingValidAlignedIndex() {
        Fixture fixture = fixture();

        PortfolioCorrelations.CorrelationMatrix matrix = PortfolioCorrelations.logReturnMatrix(fixture.series(), 1, 3);

        assertFalse(matrix.isStable());
        assertEquals(3, matrix.getCountOfUnstableBars());
        assertTrue(matrix.coefficient(fixture.alpha(), fixture.beta()).isNaN());
        assertNumEquals(1, matrix.coefficient(fixture.alpha(), fixture.alpha()));
    }

    @Test
    public void rejectsInvalidRequestsAndKeepsMatrixImmutable() {
        Fixture fixture = fixture();
        PortfolioCorrelations.CorrelationMatrix matrix = PortfolioCorrelations.logReturnMatrix(fixture.series(), 2);

        assertThrows(IllegalArgumentException.class, () -> PortfolioCorrelations.logReturnMatrix(fixture.series(), 1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> PortfolioCorrelations.logReturnMatrix(fixture.series(), fixture.series().getBarCount(), 2));
        assertThrows(IllegalArgumentException.class,
                () -> matrix.coefficient(fixture.alpha(), PortfolioAsset.of("MISSING")));
        assertThrows(UnsupportedOperationException.class, () -> matrix.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> matrix.values().get(fixture.alpha()).clear());
        assertThrows(UnsupportedOperationException.class, () -> matrix.pairs().clear());
    }

    private static Fixture fixture() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");
        BarSeries alphaSeries = series("alpha", start, 100, 110, 99, 125, 115);
        BarSeries betaSeries = series("beta", start, 50, 45, 47, 43, 51);
        AlignedPortfolioSeries series = AlignedPortfolioSeries
                .of(List.of(new PortfolioSeries(alpha, alphaSeries), new PortfolioSeries(beta, betaSeries)));
        return new Fixture(alpha, beta, series);
    }

    private static BarSeries series(String name, Instant start, double... closes) {
        int[] dayOffsets = new int[closes.length];
        for (int i = 0; i < closes.length; i++) {
            dayOffsets[i] = i;
        }
        return series(name, start, dayOffsets, closes);
    }

    private static BarSeries series(String name, Instant start, int[] dayOffsets, double[] closes) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        Num zero = series.numFactory().zero();
        for (int i = 0; i < closes.length; i++) {
            Num close = series.numFactory().numOf(closes[i]);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(dayOffsets[i])))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(zero)
                    .add();
        }
        return series;
    }

    private static double[] closesFromLogReturns(double initialClose, double... logReturns) {
        double[] closes = new double[logReturns.length + 1];
        closes[0] = initialClose;
        for (int i = 0; i < logReturns.length; i++) {
            closes[i + 1] = closes[i] * Math.exp(logReturns[i]);
        }
        return closes;
    }

    private static double[] closesFromSimpleReturns(double initialClose, double... returns) {
        double[] closes = new double[returns.length + 1];
        closes[0] = initialClose;
        for (int i = 0; i < returns.length; i++) {
            closes[i + 1] = closes[i] * (1 + returns[i]);
        }
        return closes;
    }

    private record Fixture(PortfolioAsset alpha, PortfolioAsset beta, AlignedPortfolioSeries series) {
    }
}
