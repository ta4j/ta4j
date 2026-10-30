/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.portfolio.PortfolioFixtures.START;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

public class BasePortfolioStrategyTest {

    @Test
    public void roundTripsCompositeStrategyAndUsesEachAssetsSourceStability() {
        PortfolioSeriesView view = PortfolioSeries.union(
                Map.of("A", series("a", 1, 2, 3, 4), "B", series("b", DoubleNumFactory.getInstance(), 2, "3", "4")));
        PortfolioRule entry = new EndTimeRule(START.plus(Duration.ofDays(2))).and(new MaxOpenPositionsRule(1));
        PortfolioRule exit = new EndTimeRule(START.plus(Duration.ofDays(3)));
        PortfolioStrategy original = new BasePortfolioStrategy("sample", entry, exit, 1, TradeType.SELL);
        PortfolioStrategy restored = PortfolioStrategy.fromJson(view, original.toJson());
        PortfolioTradingRecord originalRecord = new BasePortfolioTradingRecord(view,
                asset -> new BaseTradingRecord(TradeType.SELL));
        PortfolioTradingRecord restoredRecord = new BasePortfolioTradingRecord(view,
                asset -> new BaseTradingRecord(TradeType.SELL));

        assertEquals(original.toDescriptor(), restored.toDescriptor());
        assertEquals(TradeType.SELL, restored.getStartingType());
        assertEquals(1, restored.getUnstableBars());
        for (String asset : view.assets()) {
            for (int index = view.getBeginIndex(); index <= view.getEndIndex(); index++) {
                assertEquals(original.shouldEnter(asset, index, originalRecord),
                        restored.shouldEnter(asset, index, restoredRecord));
                assertEquals(original.shouldExit(asset, index, originalRecord),
                        restored.shouldExit(asset, index, restoredRecord));
            }
        }
        assertTrue(restored.shouldEnter("A", 2, restoredRecord));
        assertFalse(restored.shouldEnter("B", 2, restoredRecord));
        assertTrue(originalRecord.enter("A", 2, view.numFactory().numOf(3), view.numFactory().one()));
        assertTrue(restoredRecord.enter("A", 2, view.numFactory().numOf(3), view.numFactory().one()));
        assertFalse(original.shouldEnter("A", 2, originalRecord));
        assertFalse(restored.shouldEnter("A", 2, restoredRecord));
        assertTrue(original.shouldExit("A", 3, originalRecord));
        assertTrue(restored.shouldExit("A", 3, restoredRecord));
    }

    @Test
    public void compositeRulesShortCircuitAndPredicatesRequireExactBars() {
        PortfolioSeriesView view = PortfolioSeries
                .union(Map.of("A", series("a", 1, 2), "B", series("b", DoubleNumFactory.getInstance(), 1, "3")));
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view);
        AtomicInteger calls = new AtomicInteger();
        PortfolioRule no = (asset, index, state) -> false;
        PortfolioRule yes = (asset, index, state) -> true;
        PortfolioRule counted = (asset, index, state) -> {
            calls.incrementAndGet();
            return true;
        };
        assertFalse(no.and(counted).isSatisfied("A", 0, record));
        assertTrue(yes.or(counted).isSatisfied("A", 0, record));
        assertEquals(0, calls.get());
        assertTrue(no.negation().isSatisfied("A", 0, record));
        assertTrue(yes.and(counted).isSatisfied("A", 0, record));
        assertEquals(1, calls.get());
        EndTimeRule predicate = new EndTimeRule(time -> !time.isBefore(START));
        assertFalse(predicate.isSatisfied("B", 0, record));
        assertTrue(predicate.isSatisfied("B", 1, record));
        assertThrows(UnsupportedOperationException.class, predicate::toJson);
        assertThrows(UnsupportedOperationException.class, () -> yes.and(new MaxOpenPositionsRule(1)).toJson());
    }

    @Test
    public void positionLimitTracksRecordChangesAndRejectsInvalidDescriptors() {
        PortfolioSeriesView view = PortfolioSeries.union(Map.of("A", series("a", 1, 2), "B", series("b", 3, 4)));
        PortfolioTradingRecord record = new BasePortfolioTradingRecord(view);
        MaxOpenPositionsRule limit = new MaxOpenPositionsRule(1);
        assertFalse(new MaxOpenPositionsRule(0).isSatisfied("A", 0, record));
        assertTrue(limit.isSatisfied("A", 0, record));
        assertTrue(record.enter("A", 0, view.numFactory().one(), view.numFactory().one()));
        assertFalse(limit.isSatisfied("B", 0, record));
        assertTrue(record.exit("A", 1, view.numFactory().numOf(2), view.numFactory().one()));
        assertTrue(limit.isSatisfied("B", 1, record));
        assertThrows(IllegalArgumentException.class, () -> new MaxOpenPositionsRule(-1));
        assertThrows(IllegalArgumentException.class, () -> new BasePortfolioStrategy(limit, limit, -1));

        ComponentDescriptor invalid = ComponentDescriptor.builder()
                .withType(MaxOpenPositionsRule.class.getName())
                .withLabel("bad")
                .withParameters(Map.of("maximum", 1, "unknown", true))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> PortfolioRule.fromJson(view, ComponentSerialization.toJson(invalid)));
        ComponentDescriptor unknown = ComponentDescriptor.builder().withType("unknown.rule").withLabel("bad").build();
        assertThrows(IllegalArgumentException.class,
                () -> PortfolioRule.fromJson(view, ComponentSerialization.toJson(unknown)));
    }
}
