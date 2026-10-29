/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.strategy.named.NamedStrategyFixture;

public class PerAssetStrategyTest {

    @Test
    public void bindsLazilyAndUsesSourceIndexesForUnstableBars() {
        PortfolioSeriesView view = PortfolioSeries.union(Map.of("EARLY", series("early", 1, 3, 1, 3), "LATE",
                series("late", DoubleNumFactory.getInstance(), 2, "3", "3")));
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view,
                asset -> new BaseTradingRecord(TradeType.SELL));
        PerAssetStrategy strategy = PerAssetStrategy.of(source -> strategyFor(source, TradeType.SELL, 1));

        assertThrows(IllegalStateException.class, strategy::getStartingType);
        assertFalse(strategy.shouldEnter("LATE", 0, record));
        assertEquals(TradeType.SELL, strategy.getStartingType());
        assertEquals(1, strategy.getUnstableBars());
        assertFalse(strategy.shouldEnter("LATE", 2, record));
        assertTrue(strategy.shouldEnter("LATE", 3, record));
        assertTrue(strategy.shouldEnter("EARLY", 1, record));
        assertTrue(strategy.shouldExit("EARLY", 2, record));
        assertThrows(UnsupportedOperationException.class, strategy::toJson);
    }

    @Test
    public void rebuildsStrategyWhenLiveAssetIsReplaced() {
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("A", series("old", 1, 1)));
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view);
        PerAssetStrategy strategy = PerAssetStrategy.of(source -> strategyFor(source, TradeType.BUY, 0));
        assertFalse(strategy.shouldEnter("A", 0, record));

        assertTrue(view.removeAsset("A"));
        view.addAsset("A", series("replacement", 3, 3));
        assertTrue(strategy.shouldEnter("A", 0, record));
    }

    @Test
    public void rejectsInconsistentMetadataAndInvalidatesFailedBinding() {
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("A", series("long", 3)));
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view);
        PerAssetStrategy strategy = PerAssetStrategy.of(
                source -> strategyFor(source, source.getName().equals("short") ? TradeType.SELL : TradeType.BUY, 0));
        strategy.bind(view);
        assertEquals(TradeType.BUY, strategy.getStartingType());

        view.addAsset("B", series("short", 3));
        assertThrows(IllegalArgumentException.class, () -> strategy.shouldEnter("B", 0, record));
        assertThrows(IllegalStateException.class, strategy::getStartingType);
        assertThrows(IllegalArgumentException.class, () -> strategy.bind(view));
        assertThrows(IllegalStateException.class, strategy::getUnstableBars);
        assertTrue(view.removeAsset("B"));
        strategy.bind(view);
        assertEquals(TradeType.BUY, strategy.getStartingType());
    }

    @Test
    public void serializesDescriptorBackedStrategiesBeforeBinding() {
        BarSeries bars = series("sample", 1, 3, 1, 3);
        ClosePriceIndicator close = new ClosePriceIndicator(bars);
        Strategy shortStrategy = new BaseStrategy("short", new OverIndicatorRule(close, 2),
                new UnderIndicatorRule(close, 2), 2, TradeType.SELL);
        Strategy namedStrategy = NamedStrategyFixture.create(bars, bars.numFactory().numOf(2), 1);
        PortfolioSeriesView view = PortfolioSeries.union(Map.of("A", bars));

        for (Strategy inner : new Strategy[] { shortStrategy, namedStrategy }) {
            PerAssetStrategy fromSample = PerAssetStrategy.of(inner);
            PerAssetStrategy fromJson = PerAssetStrategy.ofJson(inner.toJson());
            String json = fromSample.toJson();

            assertEquals(inner.getName(), fromSample.getName());
            assertEquals(inner.getName(), fromJson.getName());
            assertEquals(inner.getStartingType(), fromSample.getStartingType());
            assertEquals(inner.getUnstableBars(), fromSample.getUnstableBars());
            assertThrows(IllegalStateException.class, fromJson::getStartingType);
            assertThrows(IllegalStateException.class, fromJson::getUnstableBars);
            assertEquals(json, fromJson.toJson());

            PortfolioStrategy restored = PortfolioStrategy.fromJson(view, json);
            fromSample.bind(view);
            fromJson.bind(view);
            assertEquals(json, fromSample.toJson());
            assertEquals(json, fromJson.toJson());
            assertEquals(json, restored.toJson());
            assertEquals(inner.getStartingType(), restored.getStartingType());
            assertEquals(inner.getUnstableBars(), restored.getUnstableBars());
        }
    }

    @Test
    public void retainsV2InputPrecisionAcrossMixedFactorySnapshots() {
        BarSeries alpha = series("A", DoubleNumFactory.getInstance(), 0, "1");
        BarSeries beta = series("B", DecimalNumFactory.getInstance(30), 0, "1.23456789012345675");
        PortfolioSeriesView view = new PortfolioSeries(alpha, beta);
        String json = """
                {
                  "version": 2,
                  "name": "Mixed Factory",
                  "entryRule": { "type": "OverIndicatorRule", "args": ["ClosePrice", "1.2345678901234567891"] },
                  "exitRule": { "type": "UnderIndicatorRule", "args": ["ClosePrice", "1.2345678901234567891"] }
                }
                """;
        PerAssetStrategy original = PerAssetStrategy.ofJson(json);
        String unboundJson = original.toJson();
        assertEquals("Mixed Factory", original.getName());
        assertThrows(IllegalStateException.class, original::getStartingType);
        original.bind(view);
        assertEquals(unboundJson, original.toJson());
        PortfolioStrategy restored = PortfolioStrategy.fromJson(view, original.toJson());
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view);

        assertEquals(original.toDescriptor(), restored.toDescriptor());
        assertFalse(original.shouldEnter("B", 0, record));
        assertTrue(original.shouldExit("B", 0, record));
        assertEquals(original.shouldEnter("B", 0, record), restored.shouldEnter("B", 0, record));
        assertEquals(original.shouldExit("B", 0, record), restored.shouldExit("B", 0, record));
    }

    private static BaseStrategy strategyFor(BarSeries series, TradeType type, int unstable) {
        Rule aboveTwo = (index, record) -> series.getBar(index).getClosePrice().doubleValue() > 2;
        Rule belowTwo = (index, record) -> series.getBar(index).getClosePrice().doubleValue() < 2;
        return new BaseStrategy(aboveTwo, belowTwo, unstable, type);
    }
}
