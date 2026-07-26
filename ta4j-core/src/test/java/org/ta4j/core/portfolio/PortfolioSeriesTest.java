/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PortfolioSeriesTest {

    @Test
    public void alignsOnlyCommonEndTimesInChronologicalOrder() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        BarSeries alphaSeries = series("ALPHA", start, 100, 110, 120);
        BarSeries betaSeries = series("BETA", start, new int[] { 0, 2 }, new double[] { 50, 60 });

        PortfolioSeries portfolio = new PortfolioSeries(alphaSeries, betaSeries);

        assertEquals(List.of("ALPHA", "BETA"), portfolio.getAssets());
        assertEquals(2, portfolio.getBarCount());
        assertEquals(0, portfolio.getBeginIndex());
        assertEquals(1, portfolio.getEndIndex());
        assertEquals(List.of(start, start.plus(Duration.ofDays(2))), portfolio.getEndTimes());
        assertEquals(2, portfolio.getSourceIndex("ALPHA", 1));
        assertEquals(1, portfolio.getSourceIndex("BETA", 1));
        assertNumEquals(120, portfolio.getClosePrice("ALPHA", 1));
        assertNumEquals(60, portfolio.getClosePrice("BETA", 1));
    }

    @Test
    public void preservesRetainedSourceIndexesThroughSnapshots() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        BarSeries alphaSeries = retainedSeries("ALPHA", start, 10, 100, 110);
        BarSeries betaSeries = retainedSeries("BETA", start, 20, 50, 55);

        PortfolioSeries portfolio = new PortfolioSeries(List.of(alphaSeries, betaSeries));

        assertEquals(10, portfolio.getSourceIndex("ALPHA", 0));
        assertEquals(11, portfolio.getSourceIndex("ALPHA", 1));
        assertEquals(20, portfolio.getSourceIndex("BETA", 0));
        assertEquals(21, portfolio.getSourceIndex("BETA", 1));
        assertEquals(10, portfolio.getBarSeries("ALPHA").getBeginIndex());
        assertEquals(20, portfolio.getBarSeries("BETA").getBeginIndex());
    }

    @Test
    public void rejectsDuplicateAssets() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new PortfolioSeries(List.of(series("ALPHA", start, 100, 101), series("ALPHA", start, 102, 103))));
    }

    @Test
    public void rejectsSingleAssetSeries() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> new PortfolioSeries(series("ALPHA", start, 100)));
    }

    @Test
    public void rejectsSeriesWithoutCommonEndTimes() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> new PortfolioSeries(series("ALPHA", start, 100),
                series("BETA", start.plus(Duration.ofDays(1)), 50)));
    }

    @Test
    public void explicitAliasesPreserveEncounterOrderAndSnapshotInputs() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        BarSeries first = series("source-a", start, 100, 101);
        BarSeries second = series("source-b", start, 50, 51);
        Map<String, BarSeries> aliases = new LinkedHashMap<>();
        aliases.put("EQUITY", first);
        aliases.put("BONDS", second);

        PortfolioSeries portfolio = new PortfolioSeries(aliases);
        first.setMaximumBarCount(1);

        assertEquals(List.of("EQUITY", "BONDS"), portfolio.getAssets());
        assertEquals(2, portfolio.getBarCount());
        assertEquals(2, portfolio.getBarSeries("EQUITY").getBarCount());
        assertEquals("source-a", portfolio.getBarSeries("EQUITY").getName());
    }

    @Test
    public void normalizesSameClassValuesToPortfolioFactoryPrecision() {
        NumFactory portfolioFactory = DecimalNumFactory.getInstance(3);
        NumFactory sourceFactory = DecimalNumFactory.getInstance(40);
        BarSeries alpha = decimalSeries("ALPHA", portfolioFactory, "1.00", "1.01");
        BarSeries beta = decimalSeries("BETA", sourceFactory, "1.234567", "1.345678");

        PortfolioSeries portfolio = new PortfolioSeries(alpha, beta);
        Num betaClose = portfolio.getClosePrice("BETA", 0);

        assertNumEquals(portfolioFactory.numOf("1.23"), betaClose);
        assertEquals(3, ((DecimalNum) betaClose).getMathContext().getPrecision());
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

    private static BarSeries decimalSeries(String name, NumFactory numFactory, String... closes) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        BarSeries series = new BaseBarSeriesBuilder().withName(name).withNumFactory(numFactory).build();
        for (int index = 0; index < closes.length; index++) {
            Num close = numFactory.numOf(closes[index]);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(numFactory.zero())
                    .add();
        }
        return series;
    }
}
