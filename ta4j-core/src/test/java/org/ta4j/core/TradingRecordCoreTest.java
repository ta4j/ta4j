/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class TradingRecordCoreTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @Test
    void applyTradeRejectsWhenTradeMutationHookIsMissing() {
        TradingRecordCore core = emptyCore(null, null);
        Trade trade = trade(TradeType.BUY);

        assertThrows(UnsupportedOperationException.class, () -> core.applyTrade(0, trade, 0L));
    }

    @Test
    void applySyntheticRejectsWhenSyntheticMutationHookIsMissing() {
        TradingRecordCore core = emptyCore(null, null);

        assertThrows(UnsupportedOperationException.class,
                () -> core.applySynthetic(1, TradeType.BUY, numFactory.one(), numFactory.one(), new ZeroCostModel()));
    }

    @Test
    void applyTradeForwardsIndexTradeAndSequenceToHook() {
        AtomicInteger capturedIndex = new AtomicInteger(-1);
        AtomicLong capturedSequence = new AtomicLong(-1L);
        AtomicReference<Trade> capturedTrade = new AtomicReference<>();
        Trade trade = trade(TradeType.SELL);
        TradingRecordCore.TradeApplier tradeApplier = (index, forwardedTrade, sequence) -> {
            capturedIndex.set(index);
            capturedTrade.set(forwardedTrade);
            capturedSequence.set(sequence);
        };
        TradingRecordCore core = emptyCore(tradeApplier, null);

        core.applyTrade(9, trade, 33L);

        assertEquals(9, capturedIndex.get());
        assertEquals(trade, capturedTrade.get());
        assertEquals(33L, capturedSequence.get());
    }

    @Test
    void applySyntheticForwardsSyntheticArgumentsToHook() {
        AtomicInteger capturedIndex = new AtomicInteger(-1);
        AtomicReference<TradeType> capturedType = new AtomicReference<>();
        AtomicReference<Num> capturedPrice = new AtomicReference<>();
        AtomicReference<Num> capturedAmount = new AtomicReference<>();
        AtomicReference<CostModel> capturedCostModel = new AtomicReference<>();
        Num price = numFactory.numOf(123);
        Num amount = numFactory.two();
        ZeroCostModel transactionCostModel = new ZeroCostModel();
        TradingRecordCore.SyntheticApplier syntheticApplier = (index, type, forwardedPrice, forwardedAmount,
                forwardedCostModel) -> {
            capturedIndex.set(index);
            capturedType.set(type);
            capturedPrice.set(forwardedPrice);
            capturedAmount.set(forwardedAmount);
            capturedCostModel.set(forwardedCostModel);
        };
        TradingRecordCore core = emptyCore(null, syntheticApplier);

        core.applySynthetic(7, TradeType.BUY, price, amount, transactionCostModel);

        assertEquals(7, capturedIndex.get());
        assertEquals(TradeType.BUY, capturedType.get());
        assertEquals(price, capturedPrice.get());
        assertEquals(amount, capturedAmount.get());
        assertEquals(transactionCostModel, capturedCostModel.get());
    }

    @Test
    void exposesMutableViewsAndImmutableSnapshotsIndependently() {
        List<Trade> trades = new ArrayList<>();
        List<Position> positions = new ArrayList<>();
        TradingRecordCore core = new TradingRecordCore(TradeType.BUY, () -> trades, () -> positions,
                () -> new Position(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel()), List::of, () -> null,
                () -> numFactory.zero(), null, null);

        List<Trade> tradeView = core.getTradesView();
        List<Position> closedPositionsView = core.getClosedPositionsView();

        assertSame(trades, tradeView);
        assertSame(positions, closedPositionsView);
        assertThrows(UnsupportedOperationException.class, () -> core.getTradesSnapshot().add(trade(TradeType.BUY)));
        assertThrows(UnsupportedOperationException.class, () -> core.getClosedPositionsSnapshot()
                .add(new Position(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel())));
    }

    @Test
    void getTotalFeesUsesSupplierNumWithoutFallbackFactoryConversion() {
        Num fees = numFactory.numOf(2.5);
        TradingRecordCore core = new TradingRecordCore(TradeType.BUY, List::of, List::of,
                () -> new Position(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel()), List::of, () -> null,
                () -> fees, null, null);

        assertEquals(fees, core.getTotalFees());
    }

    @Test
    void getTotalFeesRejectsNullSupplierValues() {
        TradingRecordCore core = new TradingRecordCore(TradeType.BUY, List::of, List::of,
                () -> new Position(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel()), List::of, () -> null,
                () -> null, null, null);

        assertThrows(NullPointerException.class, core::getTotalFees);
    }

    private TradingRecordCore emptyCore(TradingRecordCore.TradeApplier tradeApplier,
            TradingRecordCore.SyntheticApplier syntheticApplier) {
        List<Trade> trades = new ArrayList<>();
        List<Position> positions = new ArrayList<>();
        return new TradingRecordCore(TradeType.BUY, () -> trades, () -> positions,
                () -> new Position(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel()), List::of, () -> null,
                () -> numFactory.zero(), tradeApplier, syntheticApplier);
    }

    private Trade trade(TradeType type) {
        return new BaseTrade(0, type, numFactory.one(), numFactory.one(), new ZeroCostModel());
    }
}
