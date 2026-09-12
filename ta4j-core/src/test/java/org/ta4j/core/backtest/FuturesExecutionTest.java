/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.FuturesTransactionCostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.risk.StopLossPositionRiskModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.rules.StopLossPriceModel;

/**
 * Verifies native futures execution: simulated fills carry contract metadata
 * and bar-boundary timestamps, partially filled orders are booked as they
 * execute, quantity and margin constraints are enforced, and stop-loss risk is
 * measured in contract settlement economics.
 */
class FuturesExecutionTest {

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private static List<NumFactory> factories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    private static FuturesContract linearContract(NumFactory numFactory, double contractSize) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(contractSize))
                .quantityIncrement(numFactory.one())
                .minimumQuantity(numFactory.one())
                .build();
    }

    private static FuturesContract inverseContract(NumFactory numFactory, double contractSize) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTCUSD-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.INVERSE)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("BTC")
                .contractSize(numFactory.numOf(contractSize))
                .quantityIncrement(numFactory.one())
                .minimumQuantity(numFactory.one())
                .build();
    }

    private static FuturesTransactionCostModel takerFees(NumFactory numFactory, double takerRate) {
        return FuturesTransactionCostModel.builder()
                .makerRate(numFactory.numOf(takerRate / 2))
                .takerRate(numFactory.numOf(takerRate))
                .build();
    }

    private static BarSeries flatSeries(NumFactory numFactory, double price, double... volumes) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double volume : volumes) {
            series.barBuilder()
                    .openPrice(price)
                    .highPrice(price)
                    .lowPrice(price)
                    .closePrice(price)
                    .volume(volume)
                    .add();
        }
        return series;
    }

    private static BaseTradingRecord futuresRecord(FuturesContract contract, CostModel costModel) {
        return BaseTradingRecord.builder().futuresContract(contract).transactionCostModel(costModel).build();
    }

    private static Strategy entryOnFirstBar() {
        return new BaseStrategy(new FixedRule(0), new FixedRule());
    }

    private static PositionSizer.Context sizerContext(BarSeries series, TradingRecord tradingRecord,
            CostModel transactionCostModel, Num entryPrice) {
        return new PositionSizer.Context(0, 0, entryPrice, null, entryOnFirstBar(), series, TradeType.BUY,
                tradingRecord, transactionCostModel, new ZeroCostModel());
    }

    private static TradeFill fill(FuturesContract contract, int index, ExecutionSide side, Num amount, Num price) {
        return TradeFill.builder()
                .index(index)
                .time(T0.plusSeconds(index))
                .price(price)
                .amount(amount)
                .side(side)
                .futuresContract(contract)
                .fees(List.of())
                .build();
    }

    private static Position openPosition(FuturesContract contract, Num amount, Num price) {
        Trade entry = Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, amount, price),
                RecordedTradeCostModel.INSTANCE);
        return new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
    }

    private static Position closedPosition(FuturesContract contract, Num entryAmount, Num entryPrice, Num exitAmount,
            Num exitPrice) {
        Trade entry = Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, entryAmount, entryPrice),
                RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFill(fill(contract, 1, ExecutionSide.SELL, exitAmount, exitPrice),
                RecordedTradeCostModel.INSTANCE);
        return new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
    }

    private static void assertAmounts(List<Trade> trades, double... expected) {
        assertEquals(expected.length, trades.size());
        for (int i = 0; i < expected.length; i++) {
            assertNumEquals(expected[i], trades.get(i).getAmount());
        }
    }

    @Test
    void futuresStopLimitFillsCarryContractMetadataAndResolveConfiguredFees() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearContract(numFactory, 1);
            BarSeries series = flatSeries(numFactory, 100d, 100d, 6d, 8d, 6d);
            StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                    numFactory.numOf(0.5), 4);
            BaseTradingRecord tradingRecord = futuresRecord(contract, takerFees(numFactory, 0.001));

            new BarSeriesManager(series, model).run(entryOnFirstBar(), tradingRecord, numFactory.numOf(10));

            List<Trade> fills = tradingRecord.getTrades();
            assertAmounts(fills, 3d, 4d, 3d);
            for (int i = 0; i < fills.size(); i++) {
                Trade trade = fills.get(i);
                assertEquals(1, trade.getFills().size());
                TradeFill executionFill = trade.getFills().getFirst();
                assertEquals(contract, executionFill.futuresContract());
                assertEquals(ExecutionSide.BUY, executionFill.side());
                assertTrue(executionFill.price().isPositive());
                assertTrue(executionFill.amount().isPositive());
                assertEquals(series.getBar(i + 1).getEndTime(), executionFill.time());
                assertEquals("USD", trade.getFees().getFirst().currency());
            }
            // 3 contracts * 1 unit * 100 * 0.001 taker fee, resolved by the record cost
            // model
            assertNumEquals(0.3, fills.getFirst().getCost());
            assertNumEquals(10, tradingRecord.getCurrentPosition().getEntry().getAmount());
            assertEquals(3, tradingRecord.getOpenPositions().size());
            assertTrue(model.getPendingOrder(tradingRecord).isEmpty());
            assertTrue(model.getRejectedOrders(tradingRecord).isEmpty());
        }
    }

    @Test
    void expiringFuturesOrderKeepsItsBookedFillsWithoutReplayingThem() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearContract(numFactory, 1);
            BarSeries series = flatSeries(numFactory, 100d, 100d, 6d, 2d, 100d);
            StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                    numFactory.numOf(0.5), 2);
            BaseTradingRecord tradingRecord = futuresRecord(contract, takerFees(numFactory, 0.001));

            new BarSeriesManager(series, model).run(entryOnFirstBar(), tradingRecord, numFactory.numOf(10));

            // each booked partial fill stays its own trade: nothing is replayed on expiry
            assertAmounts(tradingRecord.getTrades(), 3d, 1d);
            assertEquals(series.getBar(1).getEndTime(),
                    tradingRecord.getTrades().getFirst().getFills().getFirst().time());
            assertEquals(series.getBar(2).getEndTime(), tradingRecord.getTrades().get(1).getFills().getFirst().time());
            assertNumEquals(4, tradingRecord.getCurrentPosition().getEntry().getAmount());
            assertEquals(1, model.getRejectedOrders(tradingRecord).size());
            StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
            assertTrue(rejection.reason().contains("expired"));
            assertNumEquals(10, rejection.requestedAmount());
            assertNumEquals(4, rejection.filledAmount());
            assertTrue(model.getPendingOrder(tradingRecord).isEmpty());
        }
    }

    @Test
    void opposingSignalCancelsUnfilledFuturesRemainderBeforeOpeningTheExit() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearContract(numFactory, 1);
            BarSeries series = flatSeries(numFactory, 100d, 100d, 6d, 6d, 6d, 6d);
            StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                    numFactory.numOf(0.5), 5);
            BaseTradingRecord tradingRecord = futuresRecord(contract, takerFees(numFactory, 0.001));
            Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(2));

            new BarSeriesManager(series, model).run(strategy, tradingRecord, numFactory.numOf(10));

            Num bookedEntry = numFactory.zero();
            Num bookedExit = numFactory.zero();
            for (Trade trade : tradingRecord.getTrades()) {
                if (trade.getType() == TradeType.BUY) {
                    bookedEntry = bookedEntry.plus(trade.getAmount());
                    // the cancelled remainder never reopens exposure after the exit signal
                    assertTrue(trade.getIndex() <= 2);
                } else {
                    bookedExit = bookedExit.plus(trade.getAmount());
                }
            }
            assertNumEquals(6, bookedEntry);
            assertNumEquals(6, bookedExit);
            assertTrue(tradingRecord.getOpenPositions().isEmpty());
            Num closedEntry = numFactory.zero();
            Num closedExit = numFactory.zero();
            for (Position position : tradingRecord.getPositions()) {
                closedEntry = closedEntry.plus(position.getEntry().getAmount());
                closedExit = closedExit.plus(position.getExit().getAmount());
            }
            assertNumEquals(6, closedEntry);
            assertNumEquals(6, closedExit);
            assertEquals(1, model.getRejectedOrders(tradingRecord).size());
            StopLimitExecutionModel.RejectedOrder cancellation = model.getRejectedOrders(tradingRecord).getFirst();
            assertTrue(cancellation.reason().contains("cancelled by an opposing signal"));
            assertNumEquals(4, cancellation.requestedAmount());
            assertNumEquals(6, cancellation.filledAmount());
        }
    }

    @Test
    void futuresEntrySizingUsesMarginPlusModeledFees() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearContract(numFactory, 0.01);
            BarSeries series = flatSeries(numFactory, 50_000d, 1d, 1d);
            FuturesTransactionCostModel fees = takerFees(numFactory, 0.0005);
            Num price = numFactory.numOf(50_000);
            BaseTradingRecord tradingRecord = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .initialMarginRate(numFactory.numOf(0.1))
                    .transactionCostModel(fees)
                    .build();
            PositionSizer.Context context = sizerContext(series, tradingRecord, fees, price);

            // 4 contracts of 500 notional: 200 margin plus a 1.0 taker fee
            assertNumEquals(201, context.entryCost(numFactory.numOf(4)));
            assertNumEquals(50.25, context.entryCost(numFactory.numOf(1)));
            assertNumEquals(9, context.maxAffordableAmount(numFactory.numOf(500)));
            assertNumEquals(1_000, context.currentBalance(numFactory.numOf(1_000)));

            BaseTradingRecord withoutMarginRate = futuresRecord(contract, fees);
            PositionSizer.Context withoutMargin = sizerContext(series, withoutMarginRate, fees, price);
            assertThrows(IllegalStateException.class, () -> withoutMargin.entryCost(numFactory.numOf(1)));
            assertThrows(IllegalArgumentException.class, () -> context.currentBalance(numFactory.numOf(500)));
        }
    }

    @Test
    void maxAffordableAmountLandsExactlyOnBoundsBeyondTheContinuousSearch() {
        // At default DoubleNum / 16-digit DecimalNum precision the unit fee is
        // below the ulp at 2^100, so both paths land on the precision boundary.
        // At 40-digit precision the fee is resolvable: the boundary sits
        // 2^20 grid units beyond what the 80-iteration continuous search
        // reaches, and the increment-grid binary search must recover it exactly.
        for (NumFactory numFactory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance(),
                DecimalNumFactory.getInstance(40))) {
            Num budget = numFactory.numOf(BigInteger.TWO.pow(100));
            FuturesContract contract = linearContract(numFactory, 1);
            BarSeries series = flatSeries(numFactory, 1, 1, 1);
            FixedTransactionCostModel fees = new FixedTransactionCostModel(1.0);
            BaseTradingRecord tradingRecord = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialMarginRate(numFactory.one())
                    .transactionCostModel(fees)
                    .build();
            PositionSizer.Context context = sizerContext(series, tradingRecord, fees, numFactory.one());
            Num expected = budget.minus(numFactory.one());
            assertNumEquals(expected, context.maxAffordableAmount(budget));
        }
    }

    @Test
    void futuresQuantityConstraintsRoundDownAndRejectUntradableOrders() {
        for (NumFactory numFactory : factories()) {
            Num price = numFactory.numOf(50_000);
            FuturesContract stepped = linearContract(numFactory, 0.01).toBuilder()
                    .quantityIncrement(numFactory.numOf(5))
                    .minimumQuantity(numFactory.numOf(5))
                    .build();
            BarSeries series = flatSeries(numFactory, 50_000d, 1d, 1d);
            FuturesTransactionCostModel fees = takerFees(numFactory, 0.0005);
            BaseTradingRecord tradingRecord = BaseTradingRecord.builder()
                    .futuresContract(stepped)
                    .initialCapital(numFactory.numOf(1_000))
                    .initialMarginRate(numFactory.numOf(0.1))
                    .transactionCostModel(fees)
                    .build();
            PositionSizer.Context context = sizerContext(series, tradingRecord, fees, price);

            assertNumEquals(5, FuturesOrderQuantitySupport.roundDown(stepped, numFactory.numOf(9.95)));
            assertNumEquals(5, context.maxAffordableAmount(numFactory.numOf(500)));
            assertThrows(IllegalArgumentException.class,
                    () -> FuturesOrderQuantitySupport.requireTradable(stepped, numFactory.numOf(3), price));
            assertThrows(IllegalArgumentException.class,
                    () -> PositionSizer.fixed(numFactory.numOf(3)).amount(context));
            assertNumEquals(5, PositionSizer.fixed(numFactory.numOf(5)).amount(context));

            FuturesContract cappedQuantity = linearContract(numFactory, 0.01).toBuilder()
                    .maximumQuantity(numFactory.numOf(3))
                    .build();
            assertNumEquals(3,
                    FuturesOrderQuantitySupport.largestTradable(cappedQuantity, numFactory.numOf(9.95), price));

            FuturesContract cappedNotional = linearContract(numFactory, 0.01).toBuilder()
                    .maximumNotional(numFactory.numOf(1_000))
                    .build();
            assertNumEquals(2,
                    FuturesOrderQuantitySupport.largestTradable(cappedNotional, numFactory.numOf(9.95), price));

            FuturesContract flooredNotional = linearContract(numFactory, 0.01).toBuilder()
                    .minimumNotional(numFactory.numOf(10_000))
                    .build();
            assertNumEquals(0,
                    FuturesOrderQuantitySupport.largestTradable(flooredNotional, numFactory.numOf(1), price));

            // a spot simulation has no contract metadata to enforce
            FuturesOrderQuantitySupport.requireTradable(null, numFactory.numOf(3), price);
        }
    }

    @Test
    void stopLossRiskModelUsesContractSettlementEconomicsForFuturesPositions() {
        for (NumFactory numFactory : factories()) {
            BarSeries series = flatSeries(numFactory, 50_000d, 1d);
            Num entryPrice = numFactory.numOf(50_000);
            Num stopPrice = numFactory.numOf(49_000);
            StopLossPriceModel stopPriceModel = (ignoredSeries, ignoredPosition) -> stopPrice;
            StopLossPositionRiskModel riskModel = new StopLossPositionRiskModel(stopPriceModel);

            // 4 linear contracts of 0.01 units: a 1,000 stop risks 4 * 0.01 * 1,000, not 4
            // * 1,000
            FuturesContract linear = linearContract(numFactory, 0.01);
            assertNumEquals(40, riskModel.risk(series, openPosition(linear, numFactory.numOf(4), entryPrice)));

            // inverse settlement risks the reciprocal-price difference in base currency
            FuturesContract inverse = inverseContract(numFactory, 100);
            assertNumEquals(numFactory.numOf(1.6326530612244898e-4),
                    riskModel.risk(series, openPosition(inverse, numFactory.numOf(4), entryPrice)), 1e-12);

            // only the unclosed contracts remain exposed
            assertNumEquals(30, riskModel.risk(series, closedPosition(linear, numFactory.numOf(4), entryPrice,
                    numFactory.numOf(1), numFactory.numOf(55_000))));
            assertNumEquals(0, riskModel.risk(series, closedPosition(linear, numFactory.numOf(4), entryPrice,
                    numFactory.numOf(4), numFactory.numOf(55_000))));
        }
    }

    @Test
    void marketExecutionModelsRouteFuturesFillsWithBarBoundaryTimestamps() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearContract(numFactory, 1);
            BarSeries series = flatSeries(numFactory, 100d, 100d, 100d);
            FuturesTransactionCostModel fees = takerFees(numFactory, 0.001);
            Strategy strategy = entryOnFirstBar();

            BaseTradingRecord closeRecord = futuresRecord(contract, fees);
            new BarSeriesManager(series, new TradeOnCurrentCloseModel()).run(strategy, closeRecord,
                    numFactory.numOf(2));
            TradeFill closeFill = closeRecord.getTrades().getFirst().getFills().getFirst();
            assertEquals(0, closeFill.index());
            assertEquals(series.getBar(0).getEndTime(), closeFill.time());
            assertEquals(contract, closeFill.futuresContract());
            assertEquals(ExecutionSide.BUY, closeFill.side());
            assertNumEquals(series.getBar(0).getClosePrice(), closeFill.price());

            BaseTradingRecord openRecord = futuresRecord(contract, fees);
            new BarSeriesManager(series, new TradeOnNextOpenModel()).run(strategy, openRecord, numFactory.numOf(2));
            Trade entry = openRecord.getTrades().getFirst();
            TradeFill openFill = entry.getFills().getFirst();
            assertEquals(1, openFill.index());
            assertEquals(series.getBar(1).getBeginTime(), openFill.time());
            assertEquals(ExecutionSide.BUY, openFill.side());
            assertNumEquals(series.getBar(1).getOpenPrice(), openFill.price());
            assertNumEquals(0.2, entry.getCost());
            assertNumEquals(2, openRecord.getCurrentPosition().getEntry().getAmount());
        }
    }

    @Test
    void largestTradableKeepsAToleranceMultipleInsteadOfSteppingDown() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = FuturesContract.builder()
                    .venue("CDE")
                    .symbol("BTC-PERP")
                    .productType(FuturesContract.ProductType.PERPETUAL)
                    .settlementType(FuturesContract.SettlementType.LINEAR)
                    .baseCurrency("BTC")
                    .quoteCurrency("USD")
                    .settlementCurrency("USD")
                    .contractSize(numFactory.numOf(1))
                    .quantityIncrement(numFactory.numOf(0.1))
                    .build();
            // The closest representable double below 0.3 sits within tolerance of the
            // 0.3 multiple, so it must stay on 0.3 instead of stepping down to 0.2.
            assertNumEquals(0.3,
                    FuturesOrderQuantitySupport.roundDown(contract, numFactory.numOf(0.29999999999999998d)));
            assertNumEquals(0.2, FuturesOrderQuantitySupport.roundDown(contract, numFactory.numOf(0.29)));
        }
    }

    @Test
    void entrySizingFeeCalculationReceivesTheResolvedFillTimestamp() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearContract(numFactory, 1);
            BarSeries series = flatSeries(numFactory, 100d, 100d, 100d);

            BaseTradingRecord nextOpenRecord = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .initialMarginRate(numFactory.numOf(0.1))
                    .transactionCostModel(new ZeroCostModel())
                    .build();
            CapturingCostModel nextOpenFees = new CapturingCostModel(numFactory);
            new BarSeriesManager(series, nextOpenFees, new ZeroCostModel()).run(entryOnFirstBar(), nextOpenRecord,
                    context -> context.entryCost(numFactory.numOf(2)));
            assertEquals(series.getBar(1).getBeginTime(), nextOpenFees.capturedTime(),
                    "a next-open fill must be fee-calculated at the open-bar begin time");

            BaseTradingRecord currentCloseRecord = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .initialMarginRate(numFactory.numOf(0.1))
                    .transactionCostModel(new ZeroCostModel())
                    .build();
            CapturingCostModel currentCloseFees = new CapturingCostModel(numFactory);
            new BarSeriesManager(series, currentCloseFees, new ZeroCostModel(), new TradeOnCurrentCloseModel())
                    .run(entryOnFirstBar(), currentCloseRecord, context -> context.entryCost(numFactory.numOf(2)));
            assertEquals(series.getBar(0).getEndTime(), currentCloseFees.capturedTime(),
                    "a current-close fill must be fee-calculated at the close time");
        }
    }

    @Test
    void rejectedFuturesFillLeavesThePendingOrderUnbooked() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearContract(numFactory, 1);
            BarSeries series = flatSeries(numFactory, 100d, 100d, 6d, 8d, 6d);
            StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                    numFactory.numOf(0.5), 4);
            BaseTradingRecord tradingRecord = futuresRecord(contract, new FlakyCostModel(numFactory, 2));

            assertThrows(IllegalStateException.class, () -> new BarSeriesManager(series, model).run(entryOnFirstBar(),
                    tradingRecord, numFactory.numOf(10)));

            StopLimitExecutionModel.PendingOrderSnapshot pending = model.getPendingOrder(tradingRecord).orElseThrow();
            assertNumEquals(3, pending.filledAmount());
            assertEquals(1, pending.fills().size());
            assertEquals(1, tradingRecord.getTrades().size());
        }
    }

    @Test
    void simulatedFuturesPartialFillsAreRoundedToTheContractIncrement() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearContract(numFactory, 0.01);
            BarSeries series = flatSeries(numFactory, 50_000d, 5d, 5d, 5d, 5d);
            StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                    numFactory.numOf(0.5), 4);
            BaseTradingRecord tradingRecord = futuresRecord(contract, new ZeroCostModel());

            new BarSeriesManager(series, model).run(entryOnFirstBar(), tradingRecord, numFactory.numOf(10));

            assertAmounts(tradingRecord.getTrades(), 2, 2, 2);
            List<StopLimitExecutionModel.RejectedOrder> rejected = model.getRejectedOrders(tradingRecord);
            assertEquals(1, rejected.size());
            assertNumEquals(10, rejected.get(0).requestedAmount());
            assertNumEquals(6, rejected.get(0).filledAmount());
        }
    }

    /**
     * A {@link CostModel} that records the timestamp of the fill it is asked to
     * price, for fee-calculation routing assertions.
     */
    private static final class CapturingCostModel implements CostModel {
        private final NumFactory numFactory;
        private final AtomicReference<Instant> capturedTime = new AtomicReference<>();

        private CapturingCostModel(NumFactory numFactory) {
            this.numFactory = numFactory;
        }

        @Override
        public Num calculate(Position position, int finalIndex) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(Position position) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(Num price, Num amount) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(TradeFill fill) {
            capturedTime.set(fill.time());
            return numFactory.zero();
        }

        @Override
        public boolean equals(CostModel otherModel) {
            return otherModel instanceof CapturingCostModel;
        }

        Instant capturedTime() {
            return capturedTime.get();
        }
    }

    /**
     * A {@link CostModel} that fails when pricing the fill at a configured index,
     * simulating a contextual cost-model failure during fill booking.
     */
    private static final class FlakyCostModel implements CostModel {
        private final NumFactory numFactory;
        private final int failingIndex;

        private FlakyCostModel(NumFactory numFactory, int failingIndex) {
            this.numFactory = numFactory;
            this.failingIndex = failingIndex;
        }

        @Override
        public Num calculate(Position position, int finalIndex) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(Position position) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(Num price, Num amount) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(TradeFill fill) {
            if (fill.index() == failingIndex) {
                throw new IllegalStateException("simulated cost model failure at index " + failingIndex);
            }
            return numFactory.numOf(0.1);
        }

        @Override
        public boolean equals(CostModel otherModel) {
            return otherModel instanceof FlakyCostModel;
        }
    }
}
