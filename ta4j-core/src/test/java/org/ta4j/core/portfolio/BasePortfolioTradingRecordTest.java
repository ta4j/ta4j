/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

class BasePortfolioTradingRecordTest {

    @Test
    void restoredAndDirectlyMutatedRecordsAreCountedAcrossDynamicAssets() {
        BarSeries alpha = series("ALPHA", 10, 11);
        BarSeries beta = series("BETA", 20, 21);
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", alpha, "BETA", beta));
        Num one = alpha.numFactory().one();
        Num price = alpha.numFactory().numOf(10);
        TradingRecord restored = new BaseTradingRecord();
        assertTrue(restored.enter(0, price, one));
        AtomicInteger creations = new AtomicInteger();
        Map<String, TradingRecord> supplied = new LinkedHashMap<>();
        supplied.put("ALPHA", restored);
        supplied.put("BETA", new BaseTradingRecord());
        BasePortfolioTradingRecord portfolio = new BasePortfolioTradingRecord(view, asset -> {
            creations.incrementAndGet();
            return supplied.get(asset);
        });

        assertEquals(1, portfolio.openPositionCount());
        assertEquals(2, creations.get());
        assertTrue(portfolio.enter("BETA", 0, price, one));
        assertFalse(portfolio.enter("BETA", 1, price, one));
        Map<String, Position> positions = portfolio.openPositions();
        assertEquals(2, positions.size());
        assertThrows(UnsupportedOperationException.class, positions::clear);

        assertTrue(view.removeAsset("ALPHA"));
        assertEquals(2, portfolio.openPositionCount());
        assertTrue(restored.exit(1, price, one));
        assertEquals(1, portfolio.openPositionCount());
        assertTrue(portfolio.exit("BETA", 1, price, one));
        assertFalse(portfolio.exit("BETA", 1, price, one));
        assertTrue(portfolio.openPositions().isEmpty());
        assertEquals(2, positions.size());
        assertEquals(2, creations.get());
    }

    @Test
    void installsAnAbsentRecordWithoutCallingTheFactory() {
        BarSeries alpha = series("ALPHA", 10, 11);
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", alpha));
        AtomicInteger creations = new AtomicInteger();
        BasePortfolioTradingRecord portfolio = new BasePortfolioTradingRecord(view, asset -> {
            creations.incrementAndGet();
            return new BaseTradingRecord();
        });
        TradingRecord restored = new BaseTradingRecord();
        assertTrue(restored.enter(0, alpha.numFactory().numOf(10), alpha.numFactory().one()));

        portfolio.replaceRecord("ALPHA", restored);

        assertSame(restored, portfolio.record("ALPHA"));
        assertEquals(restored.getCurrentPosition(), portfolio.openPositions().get("ALPHA"));
        assertEquals(1, portfolio.openPositionCount());
        assertEquals(0, creations.get());
        assertThrows(NullPointerException.class, () -> portfolio.replaceRecord(null, restored));
        assertThrows(NullPointerException.class, () -> portfolio.replaceRecord("ALPHA", null));
    }

    @Test
    void refusesToDiscardAnOpenRecordButAcceptsTheSameInstance() {
        BarSeries alpha = series("ALPHA", 10, 11);
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", alpha));
        BasePortfolioTradingRecord portfolio = new BasePortfolioTradingRecord(view);
        TradingRecord existing = portfolio.record("ALPHA");
        assertTrue(existing.enter(0, alpha.numFactory().numOf(10), alpha.numFactory().one()));

        portfolio.replaceRecord("ALPHA", existing);
        assertThrows(IllegalStateException.class, () -> portfolio.replaceRecord("ALPHA", new BaseTradingRecord()));

        assertSame(existing, portfolio.record("ALPHA"));
        assertEquals(existing.getCurrentPosition(), portfolio.openPositions().get("ALPHA"));
        assertEquals(1, portfolio.openPositionCount());
    }

    @Test
    void replacesAFlatRetiredRecordWhenAnAssetIsRecreated() {
        BarSeries alpha = series("ALPHA", 10, 11);
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", alpha));
        BasePortfolioTradingRecord portfolio = new BasePortfolioTradingRecord(view);
        TradingRecord existing = portfolio.record("ALPHA");
        Num one = alpha.numFactory().one();
        Num price = alpha.numFactory().numOf(10);
        assertTrue(existing.enter(0, price, one));
        assertTrue(existing.exit(1, price, one));
        assertTrue(view.removeAsset("ALPHA"));
        TradingRecord restored = new BaseTradingRecord();
        assertTrue(restored.enter(0, price, one));

        portfolio.replaceRecord("ALPHA", restored);
        assertSame(restored, portfolio.record("ALPHA"));
        assertEquals(1, portfolio.openPositionCount());
        view.addAsset("ALPHA", alpha);

        assertSame(restored, portfolio.record("ALPHA"));
        assertEquals(restored.getCurrentPosition(), portfolio.openPositions().get("ALPHA"));
        assertEquals(1, portfolio.openPositionCount());
    }
}
