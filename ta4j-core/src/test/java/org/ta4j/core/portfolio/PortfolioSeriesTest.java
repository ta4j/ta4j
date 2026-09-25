/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.portfolio.PortfolioFixtures.START;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
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
        assertEquals("PortfolioSeries{assets=[ALPHA, BETA], bars=2, from=2026-01-01T00:00:00Z, to=2026-01-03T00:00:00Z}",
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
