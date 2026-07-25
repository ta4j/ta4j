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
        String alpha = "ALPHA";
        String beta = "BETA";
        String gamma = "GAMMA";
        PortfolioSeries series = new PortfolioSeries(series(alpha, start, 10, 11, 12, 11, 13),
                series(beta, start, 20, 21, 19, 22, 24), series(gamma, start, 8, 7, 7.5, 6, 6.5));

        PortfolioCorrelations.CorrelationMatrix matrix = new PortfolioCorrelations(series).getPriceMatrix();

        assertEquals(4, matrix.getIndex());
        assertEquals(5, matrix.getBarCount());
        assertEquals(4, matrix.getCountOfUnstableBars());
        assertTrue(matrix.isStable());
        assertNumEquals(0.5243548655, matrix.getCoefficient(alpha, beta));
        assertNumEquals(-0.4160251472, matrix.getCoefficient(alpha, gamma));
        assertNumEquals(-0.7397954429, matrix.getCoefficient(beta, gamma));
    }

    @Test
    public void buildsSimpleReturnCorrelationMatrixMatchingPandasPctChangeCorr() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        String alpha = "ALPHA";
        String beta = "BETA";
        String gamma = "GAMMA";
        BarSeries alphaSeries = series(alpha, start, closesFromSimpleReturns(100, 0.10, -0.05, 0.20, 0.03));
        BarSeries betaSeries = series(beta, start, closesFromSimpleReturns(200, 0.20, -0.10, 0.40, 0.06));
        BarSeries gammaSeries = series(gamma, start, closesFromSimpleReturns(300, -0.10, 0.05, -0.20, -0.03));
        PortfolioSeries series = new PortfolioSeries(alphaSeries, betaSeries, gammaSeries);

        PortfolioCorrelations.CorrelationMatrix matrix = new PortfolioCorrelations(series).getSimpleReturnMatrix();

        assertEquals(4, matrix.getIndex());
        assertEquals(4, matrix.getBarCount());
        assertEquals(4, matrix.getCountOfUnstableBars());
        assertTrue(matrix.isStable());
        assertNumEquals(1d, matrix.getCoefficient(alpha, beta));
        assertNumEquals(-1d, matrix.getCoefficient(alpha, gamma));
        assertNumEquals(-1d, matrix.getCoefficient(beta, gamma));
    }

    @Test
    public void buildsSymmetricLogReturnCorrelationMatrix() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        String alpha = "ALPHA";
        String beta = "BETA";
        String gamma = "GAMMA";
        BarSeries alphaSeries = series(alpha, start, closesFromLogReturns(100, 0.10, -0.05, 0.20, 0.03));
        BarSeries betaSeries = series(beta, start, closesFromLogReturns(200, 0.20, -0.10, 0.40, 0.06));
        BarSeries gammaSeries = series(gamma, start, closesFromLogReturns(300, -0.10, 0.05, -0.20, -0.03));
        PortfolioSeries series = new PortfolioSeries(alphaSeries, betaSeries, gammaSeries);

        PortfolioCorrelations.CorrelationMatrix matrix = new PortfolioCorrelations(series).getLogReturnMatrix(4);

        assertEquals(List.of(alpha, beta, gamma), matrix.getAssets());
        assertEquals(4, matrix.getIndex());
        assertEquals(4, matrix.getBarCount());
        assertEquals(4, matrix.getCountOfUnstableBars());
        assertEquals(SampleType.POPULATION, matrix.getSampleType());
        assertTrue(matrix.isStable());
        assertNumEquals(1, matrix.getCoefficient(alpha, alpha));
        assertNumEquals(1d, matrix.getCoefficient(alpha, beta));
        assertNumEquals(-1d, matrix.getCoefficient(alpha, gamma));
        assertNumEquals(matrix.getCoefficient(beta, gamma), matrix.getCoefficient(gamma, beta), 0.0001);
        assertEquals(3, matrix.getPairs().size());
        assertEquals(alpha, matrix.getPairs().getFirst().getFirstAsset());
        assertEquals(beta, matrix.getPairs().getFirst().getSecondAsset());
        assertNumEquals(1d, matrix.getPairs().getFirst().getAbsoluteCoefficient());

        PortfolioCorrelations.CorrelationHierarchy hierarchy = matrix.completeLinkage();
        assertEquals(List.of(gamma, alpha, beta), hierarchy.getLeafOrder());
        assertEquals(2, hierarchy.getMerges().size());
        assertNumEquals(series.numFactory().zero(), hierarchy.getMerges().get(0).getDistance(), 0.000000000001);
        assertEquals(2, hierarchy.getMerges().get(0).getSize());
        assertNumEquals(series.numFactory().numOf(Math.sqrt(12)), hierarchy.getMerges().get(1).getDistance(),
                0.000000000001);
        assertEquals(3, hierarchy.getMerges().get(1).getSize());
    }

    @Test
    public void usesAlignedEndTimeTimelineWhenSourceIndexesDiffer() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        String alpha = "ALPHA";
        String beta = "BETA";
        BarSeries alphaSeries = series(alpha, start, new int[] { 0, 1, 2, 3, 4 },
                new double[] { 100, 110, 500, 121, 108.9 });
        BarSeries betaSeries = series(beta, start, new int[] { 0, 1, 3, 4 }, new double[] { 50, 55, 60.5, 54.45 });
        PortfolioSeries series = new PortfolioSeries(alphaSeries, betaSeries);

        PortfolioCorrelations.CorrelationMatrix matrix = new PortfolioCorrelations(series).getLogReturnMatrix(3);

        assertTrue(matrix.isStable());
        assertNumEquals(1d, matrix.getCoefficient(alpha, beta));
    }

    @Test
    public void marksEarlyWindowUnstableWithoutRejectingValidAlignedIndex() {
        Fixture fixture = fixture();

        PortfolioCorrelations.CorrelationMatrix matrix = new PortfolioCorrelations(fixture.series())
                .getLogReturnMatrix(1, 3);

        assertFalse(matrix.isStable());
        assertEquals(3, matrix.getCountOfUnstableBars());
        assertTrue(matrix.getCoefficient(fixture.alpha(), fixture.beta()).isNaN());
        assertNumEquals(1, matrix.getCoefficient(fixture.alpha(), fixture.alpha()));
    }

    @Test
    public void rejectsInvalidRequestsAndKeepsMatrixImmutable() {
        Fixture fixture = fixture();
        PortfolioCorrelations correlations = new PortfolioCorrelations(fixture.series());
        PortfolioCorrelations.CorrelationMatrix matrix = correlations.getLogReturnMatrix(2);

        assertThrows(IllegalArgumentException.class, () -> correlations.getLogReturnMatrix(1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> correlations.getLogReturnMatrix(fixture.series().getBarCount(), 2));
        assertThrows(IllegalArgumentException.class, () -> matrix.getCoefficient(fixture.alpha(), "MISSING"));
        assertThrows(UnsupportedOperationException.class, () -> matrix.getValues().clear());
        assertThrows(UnsupportedOperationException.class, () -> matrix.getValues().get(fixture.alpha()).clear());
        assertThrows(UnsupportedOperationException.class, () -> matrix.getPairs().clear());
    }

    private static Fixture fixture() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        String alpha = "ALPHA";
        String beta = "BETA";
        BarSeries alphaSeries = series(alpha, start, 100, 110, 99, 125, 115);
        BarSeries betaSeries = series(beta, start, 50, 45, 47, 43, 51);
        PortfolioSeries series = new PortfolioSeries(alphaSeries, betaSeries);
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

    private record Fixture(String alpha, String beta, PortfolioSeries series) {
    }
}
