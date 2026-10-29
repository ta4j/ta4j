/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.portfolio.PortfolioFixtures.START;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PortfolioSeriesTest {

    private static final NumFactory DOUBLE = DoubleNumFactory.getInstance();

    @Test
    public void alignsOnlyCommonEndTimesInChronologicalOrder() {
        BarSeries alpha = series("ALPHA", 100, 110, 120);
        BarSeries beta = new BaseBarSeriesBuilder().withName("BETA")
                .withBars(List.of(series("b0", DOUBLE, 0, "50").getFirstBar(),
                        series("b2", DOUBLE, 2, "60").getFirstBar()))
                .build();

        PortfolioSeries portfolio = new PortfolioSeries(alpha, beta);

        assertEquals(List.of("ALPHA", "BETA"), portfolio.getAssets());
        assertEquals(2, portfolio.getBarCount());
        assertEquals(0, portfolio.getBeginIndex());
        assertEquals(1, portfolio.getEndIndex());
        assertEquals(List.of(START, START.plus(Duration.ofDays(2))), portfolio.getEndTimes());
        assertEquals(2, portfolio.getSourceIndex("ALPHA", 1));
        assertEquals(1, portfolio.getSourceIndex("BETA", 1));
        assertNumEquals(120, portfolio.getClosePrice("ALPHA", 1));
        assertNumEquals(60, portfolio.getClosePrice("BETA", 1));
        assertEquals(
                "PortfolioSeries{assets=[ALPHA, BETA], bars=2, from=2026-01-01T00:00:00Z, to=2026-01-03T00:00:00Z}",
                portfolio.toString());
    }

    @Test
    public void singleSeriesIsAValidPortfolio() {
        PortfolioSeries portfolio = new PortfolioSeries(series("SPY", 100, 101));

        assertEquals(List.of("SPY"), portfolio.getAssets());
        assertEquals(2, portfolio.getBarCount());
    }

    @Test
    public void preservesRetainedSourceIndexes() {
        BarSeries alpha = new BaseBarSeriesBuilder().withName("ALPHA")
                .withBeginIndex(10)
                .withBars(series("a", 100, 110).getBarData())
                .build();
        BarSeries beta = new BaseBarSeriesBuilder().withName("BETA")
                .withBeginIndex(20)
                .withBars(series("b", 50, 55).getBarData())
                .build();

        PortfolioSeries portfolio = new PortfolioSeries(List.of(alpha, beta));

        assertEquals(11, portfolio.getSourceIndex("ALPHA", 1));
        assertEquals(21, portfolio.getSourceIndex("BETA", 1));
        assertEquals(20, portfolio.getBarSeries("BETA").getBeginIndex());
    }

    @Test
    public void alignsSeriesWhoseRetainedIndexEndsAtIntegerMaxValue() {
        BarSeries terminal = new BaseBarSeriesBuilder().withName("TERMINAL")
                .withBeginIndex(Integer.MAX_VALUE)
                .withBars(series("t", 100).getBarData())
                .build();

        PortfolioSeries portfolio = new PortfolioSeries(terminal, series("OTHER", 50));

        assertEquals(1, portfolio.getBarCount());
        assertEquals(Integer.MAX_VALUE, portfolio.getSourceIndex("TERMINAL", 0));
        assertNumEquals(100, portfolio.getClosePrice("TERMINAL", 0));
    }

    @Test
    public void ownsDetachedBarsThatSourceAndViewMutationsCannotReach() {
        BarSeries alpha = series("ALPHA", 100, 110);
        PortfolioSeries portfolio = new PortfolioSeries(alpha, series("BETA", 50, 55));

        alpha.addPrice(DOUBLE.numOf(111));
        alpha.getLastBar().addPrice(DOUBLE.numOf(112));
        portfolio.getBarSeries("ALPHA").addPrice(DOUBLE.numOf(113));
        portfolio.getBar("ALPHA", 1).addPrice(DOUBLE.numOf(114));

        assertNumEquals(110, portfolio.getClosePrice("ALPHA", 1));
        assertNumEquals(110, portfolio.getBar("ALPHA", 1).getClosePrice());
        assertNumEquals(110, portfolio.getBarSeries("ALPHA").getLastBar().getClosePrice());
    }

    @Test
    public void explicitAliasesPreserveEncounterOrder() {
        Map<String, BarSeries> aliases = new LinkedHashMap<>();
        aliases.put("EQUITY", series("source-a", 100, 101));
        aliases.put("BONDS", series("source-b", 50, 51));

        PortfolioSeries portfolio = new PortfolioSeries(aliases);

        assertEquals(List.of("EQUITY", "BONDS"), portfolio.getAssets());
        assertEquals("source-a", portfolio.getBarSeries("EQUITY").getName());
    }

    @Test
    public void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new PortfolioSeries(series("ALPHA", 100, 101), series("ALPHA", 102, 103)));
        assertThrows(IllegalArgumentException.class, () -> new PortfolioSeries(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PortfolioSeries(series("ALPHA", 100), series("BETA", DOUBLE, 1, "50")));
        assertThrows(IllegalArgumentException.class,
                () -> new PortfolioSeries(new BaseBarSeriesBuilder().withName("EMPTY").build()));
    }

    @Test
    public void rejectsUnknownAssetsAndIndexes() {
        PortfolioSeries portfolio = new PortfolioSeries(series("ALPHA", 100, 101));

        assertThrows(IllegalArgumentException.class, () -> portfolio.getClosePrice("MISSING", 0));
        assertThrows(IndexOutOfBoundsException.class, () -> portfolio.getClosePrice("ALPHA", 2));
        assertThrows(IndexOutOfBoundsException.class, () -> portfolio.getSourceIndex("ALPHA", -1));
    }

    @Test
    public void unionAlignsStaggeredSourcesAndDistinguishesExactBarsFromAsOfIndexes() {
        BarSeries alpha = new BaseBarSeriesBuilder().withName("ALPHA")
                .withNumFactory(DOUBLE)
                .withBeginIndex(10)
                .withBars(List.of(series("a0", DOUBLE, 0, "100").getFirstBar(),
                        series("a2", DOUBLE, 2, "120").getFirstBar(),
                        series("a4", DOUBLE, 4, "140").getFirstBar()))
                .build();
        BarSeries beta = new BaseBarSeriesBuilder().withName("BETA")
                .withNumFactory(DOUBLE)
                .withBeginIndex(20)
                .withBars(series("b", DOUBLE, 1, "50", "55", "60").getBarData())
                .build();
        BarSeries gamma = series("GAMMA", DOUBLE, 3, "30");
        Map<String, BarSeries> sources = new LinkedHashMap<>();
        sources.put("ALPHA", alpha);
        sources.put("BETA", beta);
        sources.put("GAMMA", gamma);

        PortfolioSeriesView view = PortfolioSeries.union(sources);
        sources.clear();

        assertEquals(List.of("ALPHA", "BETA", "GAMMA"), view.assets());
        assertEquals(0, view.getBeginIndex());
        assertEquals(4, view.getEndIndex());
        assertEquals(START.plus(Duration.ofDays(4)), view.getEndTime(4));
        assertFalse(view.hasBar("BETA", 0));
        assertEquals(-1, view.getSourceIndex("BETA", 0));
        assertFalse(view.hasBar("GAMMA", 2));
        assertEquals(-1, view.getSourceIndex("GAMMA", 2));
        assertFalse(view.hasBar("ALPHA", 1));
        assertEquals(10, view.getSourceIndex("ALPHA", 1));
        assertTrue(view.hasBar("ALPHA", 2));
        assertEquals(11, view.getSourceIndex("ALPHA", 2));
        assertTrue(view.hasBar("GAMMA", 3));
        assertEquals(0, view.getSourceIndex("GAMMA", 4));
        assertFalse(view.hasBar("GAMMA", 4));
        assertFalse(view.hasBar("UNKNOWN", 0));
        assertEquals(-1, view.getSourceIndex("UNKNOWN", 0));
        assertEquals(20, view.getBarSeries("BETA").getBeginIndex());
        assertThrows(IndexOutOfBoundsException.class, () -> view.hasBar("ALPHA", -1));
        assertThrows(IndexOutOfBoundsException.class, () -> view.getEndTime(5));
        assertThrows(UnsupportedOperationException.class, () -> view.assets().add("NO"));
        PortfolioSeries snapshot = (PortfolioSeries) view;
        assertNumEquals(100, snapshot.getClosePrice("ALPHA", 1));
        assertThrows(IllegalStateException.class, () -> snapshot.getBar("BETA", 0));
        assertThrows(IllegalStateException.class, () -> snapshot.getClosePrice("BETA", 0));

        alpha.addPrice(DOUBLE.numOf(999));
        beta.clear();
        assertEquals(22, snapshot.getSourceIndex("BETA", 3));
        assertNumEquals(140, snapshot.getClosePrice("ALPHA", 4));
    }

    @Test
    public void unionAcceptsEmptyMembersAndAWhollyEmptyTimeline() {
        BarSeries empty = new BaseBarSeriesBuilder().withName("EMPTY").withNumFactory(DOUBLE).build();
        PortfolioSeries snapshot = PortfolioSeries.union(Map.of("EMPTY", empty));

        assertEquals(List.of("EMPTY"), snapshot.assets());
        assertEquals(0, snapshot.getBeginIndex());
        assertEquals(-1, snapshot.getEndIndex());
        assertEquals(0, snapshot.getBarCount());
        assertTrue(snapshot.getBarSeries("EMPTY").isEmpty());
        assertEquals("PortfolioSeries{assets=[EMPTY], bars=0}", snapshot.toString());
        assertThrows(IndexOutOfBoundsException.class, () -> snapshot.hasBar("EMPTY", 0));
        assertThrows(IllegalArgumentException.class, () -> PortfolioSeries.union(Map.of()));
    }

    @Test
    public void unionRequiresMatchingFactoriesButAcceptsEqualDecimalContexts() {
        NumFactory decimal = DecimalNumFactory.getInstance(8);
        NumFactory equivalent = DecimalNumFactory.getInstance(8);
        BarSeries alpha = series("ALPHA", decimal, 0, "1.25");
        BarSeries beta = series("BETA", equivalent, 1, "2.50");

        PortfolioSeries snapshot = PortfolioSeries.union(Map.of("ALPHA", alpha, "BETA", beta));

        assertEquals(2, snapshot.getBarCount());
        assertThrows(IllegalArgumentException.class, () -> PortfolioSeries.union(
                Map.of("ALPHA", alpha, "BETA", series("BETA", DecimalNumFactory.getInstance(9), 0, "2"))));
        assertThrows(IllegalArgumentException.class,
                () -> PortfolioSeries.union(Map.of("ALPHA", alpha, "BETA", series("BETA", 2))));
        assertThrows(IllegalArgumentException.class,
                () -> new PortfolioSeries(series("ALPHA", 1)).getSourceIndex("MISSING", 0));
    }

    @Test
    public void normalizesPricesToPortfolioFactoryPrecision() {
        NumFactory portfolioFactory = DecimalNumFactory.getInstance(3);
        BarSeries alpha = series("ALPHA", portfolioFactory, 0, "1.00", "1.01");
        BarSeries beta = series("BETA", DecimalNumFactory.getInstance(40), 0, "1.234567", "1.345678");

        PortfolioSeries portfolio = new PortfolioSeries(alpha, beta);
        Num betaClose = portfolio.getClosePrice("BETA", 0);
        Bar betaBar = portfolio.getBar("BETA", 0);

        assertNumEquals(portfolioFactory.numOf("1.23"), betaClose);
        assertEquals(3, ((DecimalNum) betaClose).getMathContext().getPrecision());
        assertNumEquals(DecimalNumFactory.getInstance(40).numOf("1.234567"), betaBar.getClosePrice());
    }
}
