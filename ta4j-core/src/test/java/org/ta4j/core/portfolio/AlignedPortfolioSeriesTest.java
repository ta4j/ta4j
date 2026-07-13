/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class AlignedPortfolioSeriesTest {

    @Test
    public void alignsOnlyCommonEndTimesInChronologicalOrder() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");
        BarSeries alphaSeries = series("alpha", start, 100, 110, 120);
        BarSeries betaSeries = series("beta", start, new int[] { 0, 2 }, new double[] { 50, 60 });

        AlignedPortfolioSeries aligned = AlignedPortfolioSeries
                .of(List.of(new PortfolioSeries(alpha, alphaSeries), new PortfolioSeries(beta, betaSeries)));

        assertEquals(List.of(alpha, beta), aligned.assets());
        assertEquals(2, aligned.getBarCount());
        assertEquals(List.of(start, start.plus(Duration.ofDays(2))), aligned.endTimes());
        assertEquals(2, aligned.getSourceIndex(alpha, 1));
        assertEquals(1, aligned.getSourceIndex(beta, 1));
        assertNumEquals(120, aligned.getClosePrice(alpha, 1));
        assertNumEquals(60, aligned.getClosePrice(beta, 1));
    }

    @Test
    public void preservesRetainedSourceIndexesThroughSnapshots() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");
        BarSeries alphaSeries = retainedSeries("alpha", start, 10, 100, 110);
        BarSeries betaSeries = retainedSeries("beta", start, 20, 50, 55);

        AlignedPortfolioSeries aligned = AlignedPortfolioSeries
                .of(List.of(new PortfolioSeries(alpha, alphaSeries), new PortfolioSeries(beta, betaSeries)));

        assertEquals(10, aligned.getSourceIndex(alpha, 0));
        assertEquals(11, aligned.getSourceIndex(alpha, 1));
        assertEquals(20, aligned.getSourceIndex(beta, 0));
        assertEquals(21, aligned.getSourceIndex(beta, 1));
        assertEquals(10, aligned.getSeries(alpha).getBeginIndex());
        assertEquals(20, aligned.getSeries(beta).getBeginIndex());
    }

    @Test
    public void rejectsDuplicateAssets() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");

        assertThrows(IllegalArgumentException.class,
                () -> AlignedPortfolioSeries.of(List.of(new PortfolioSeries(alpha, series("alpha-a", start, 100, 101)),
                        new PortfolioSeries(alpha, series("alpha-b", start, 102, 103)))));
    }

    @Test
    public void rejectsSingleAssetSeries() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class,
                () -> AlignedPortfolioSeries.of(List.of(PortfolioSeries.of("ALPHA", series("alpha", start, 100)))));
    }

    @Test
    public void rejectsSeriesWithoutCommonEndTimes() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class,
                () -> AlignedPortfolioSeries.of(List.of(PortfolioSeries.of("ALPHA", series("alpha", start, 100)),
                        PortfolioSeries.of("BETA", series("beta", start.plus(Duration.ofDays(1)), 50)))));
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

    private static BarSeries retainedSeries(String name, Instant start, int beginIndex, double... closes) {
        BarSeries source = series(name, start, closes);
        return new BaseBarSeriesBuilder().withName(name)
                .withBeginIndex(beginIndex)
                .withBars(source.getBarData())
                .build();
    }
}
