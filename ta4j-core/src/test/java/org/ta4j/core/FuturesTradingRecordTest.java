/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies the native futures behaviour of {@link BaseTradingRecord} and
 * {@link Position}: contract settlement, recorded fee allocation across partial
 * closes, variation-margin conservation, isolation and serialization
 * preservation.
 */
class FuturesTradingRecordTest {

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private static List<NumFactory> factories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    private static FuturesContract linearBtcPerpetual(NumFactory numFactory) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(0.01))
                .build();
    }

    private static FuturesContract inverseBtcPerpetual(NumFactory numFactory) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTCUSD-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.INVERSE)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("BTC")
                .contractSize(numFactory.numOf(100))
                .build();
    }

    private static TradeFee commission(NumFactory numFactory, double amount, String currency) {
        return TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(numFactory.numOf(amount))
                .currency(currency)
                .build();
    }

    private static TradeFill fill(FuturesContract contract, int index, ExecutionSide side, double amount, double price,
            List<TradeFee> fees) {
        NumFactory numFactory = contract.contractSize().getNumFactory();
        return TradeFill.builder()
                .index(index)
                .time(T0.plusSeconds(index))
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .orderId("order-" + index)
                .futuresContract(contract)
                .fees(fees)
                .build();
    }

    private static Position openPosition(FuturesContract contract, int index, double amount, double price,
            List<TradeFee> fees) {
        Trade entry = Trade.fromFill(fill(contract, index, ExecutionSide.BUY, amount, price, fees),
                RecordedTradeCostModel.INSTANCE);
        return new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
    }

    private static FuturesFunding fundingEvent(FuturesContract contract, int index, double rate,
            double referencePrice) {
        NumFactory numFactory = contract.contractSize().getNumFactory();
        return FuturesFunding.builder()
                .contract(contract)
                .eventId("funding-" + index)
                .index(index)
                .time(T0.plusSeconds(index))
                .rate(numFactory.numOf(rate))
                .referencePrice(numFactory.numOf(referencePrice))
                .build();
    }

    private static FuturesCashFlow cashFlow(FuturesContract contract, FuturesCashFlow.Type type, String eventId,
            int index, double amount) {
        return FuturesCashFlow.builder()
                .contract(contract)
                .type(type)
                .eventId(eventId)
                .index(index)
                .time(T0.plusSeconds(index))
                .amount(contract.contractSize().getNumFactory().numOf(amount))
                .currency(contract.settlementCurrency())
                .build();
    }

    @Test
    void builderExposesFuturesConfiguration() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .initialMarginRate(numFactory.numOf(0.1))
                    .build();

            assertEquals(contract, record.getFuturesContract());
            assertNumEquals(1_000, record.getInitialCapital());
            assertNumEquals(0.1, record.getInitialMarginRate());
            assertTrue(record.getFundingSchedule().isEmpty());
            assertTrue(record.getPositions().isEmpty());
            assertTrue(record.getOpenPositions().isEmpty());
        }
    }

    @Test
    void linearRecordSettlesInSettlementCurrency() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(
                    fill(contract, 0, ExecutionSide.BUY, 3, 50_000, List.of(commission(numFactory, 0.15, "USD"))));
            record.operate(
                    fill(contract, 1, ExecutionSide.SELL, 3, 52_000, List.of(commission(numFactory, 0.15, "USD"))));

            assertEquals(1, record.getPositions().size());
            Position position = record.getPositions().getFirst();
            assertEquals(contract, position.getFuturesContract());
            assertNumEquals(60, position.getGrossProfit());
            assertNumEquals(59.7, position.getProfit());
            assertNumEquals(1.04, position.getGrossReturn());
            assertNumEquals(59.7, position.getRealizedProfit(1));
            assertNumEquals(0, position.getUnrealizedProfit(numFactory.numOf(52_000), 1));
            assertNumEquals(1_500, contract.settlementNotional(numFactory.numOf(3), numFactory.numOf(50_000)));
            assertNumEquals(150,
                    contract.marginRequirement(numFactory.numOf(3), numFactory.numOf(50_000), numFactory.numOf(0.1)));

            List<TradeFee> entryFees = position.getEntry().getFees();
            assertEquals(1, entryFees.size());
            assertEquals(TradeFee.Type.COMMISSION, entryFees.getFirst().type());
            assertNumEquals(0.15, entryFees.getFirst().settlementAmount());
            assertEquals("order-0", position.getEntry().getOrderId());
            assertEquals("order-1", position.getExit().getOrderId());
        }
    }

    @Test
    void inverseRecordSettlesInBaseCurrency() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = inverseBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(
                    fill(contract, 0, ExecutionSide.BUY, 100, 20_000, List.of(commission(numFactory, 0.0001, "BTC"))));
            record.operate(fill(contract, 1, ExecutionSide.SELL, 100, 25_000,
                    List.of(commission(numFactory, 0.00012, "BTC"))));

            Position position = record.getPositions().getFirst();
            assertNumEquals(0.5, contract.settlementNotional(numFactory.numOf(100), numFactory.numOf(20_000)));
            assertNumEquals(0.1, position.getGrossProfit());
            assertNumEquals(0.09978, position.getProfit());
            assertNumEquals(1.2, position.getGrossReturn());
            assertNumEquals(0.09978, position.getRealizedProfit(1));
        }
    }

    @Test
    void inverseOpenPositionUsesHarmonicAverageEntryPrice() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = inverseBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(
                    fill(contract, 0, ExecutionSide.BUY, 50, 20_000, List.of(commission(numFactory, 0.0001, "BTC"))));
            record.operate(
                    fill(contract, 1, ExecutionSide.BUY, 50, 40_000, List.of(commission(numFactory, 0.0001, "BTC"))));

            Position open = record.getCurrentPosition();
            assertTrue(open.isOpened());
            assertNumEquals(80_000d / 3d, open.getEntry().getPricePerAsset());
            assertNumEquals(100, open.getEntry().getAmount());
            assertNumEquals(0.125, open.getGrossProfit(numFactory.numOf(40_000)));
            assertEquals(2, open.getEntry().getFees().size());
            assertNumEquals(0.0001, open.getEntry().getFees().get(0).settlementAmount());
        }
    }

    @Test
    void partialCloseAllocatesEntryFeesAndKeepsRemainingExposure() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(fill(contract, 1, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));

            assertEquals(1, record.getPositions().size());
            Position closed = record.getPositions().getFirst();
            assertNumEquals(10, closed.getGrossProfit());
            assertNumEquals(8, closed.getProfit());
            assertNumEquals(8, closed.getRealizedProfit(1));
            assertNumEquals(0, closed.getUnrealizedProfit(numFactory.numOf(11_000), 1));

            assertEquals(1, record.getOpenPositions().size());
            Position open = record.getOpenPositions().getFirst();
            assertNumEquals(3, open.getEntry().getAmount());
            assertNumEquals(3, open.getEntry().getFees().getFirst().settlementAmount());
            assertNumEquals(60, open.getGrossProfit(numFactory.numOf(12_000)));
            assertNumEquals(57, open.getProfit(2, numFactory.numOf(12_000)));
            assertNumEquals(-3, open.getRealizedProfit(2));
            assertNumEquals(60, open.getUnrealizedProfit(numFactory.numOf(12_000), 2));
        }
    }

    @Test
    void variationMarginMovesProfitFromUnrealizedToRealized() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            Position withoutMargin = openPosition(contract, 0, 3, 10_000, List.of(commission(numFactory, 3, "USD")));
            Position withMargin = new Position(withoutMargin.getEntry(), RecordedTradeCostModel.INSTANCE,
                    new ZeroCostModel(),
                    List.of(FuturesCashFlow.builder()
                            .contract(contract)
                            .type(FuturesCashFlow.Type.VARIATION_MARGIN)
                            .eventId("vm-1")
                            .index(1)
                            .time(T0.plusSeconds(1))
                            .amount(numFactory.numOf(20))
                            .currency("USD")
                            .build()));

            Num mark = numFactory.numOf(12_000);
            assertNumEquals(57, withoutMargin.getProfit(1, mark));
            assertNumEquals(57, withMargin.getProfit(1, mark));
            assertNumEquals(-3, withoutMargin.getRealizedProfit(1));
            assertNumEquals(60, withoutMargin.getUnrealizedProfit(mark, 1));
            assertNumEquals(17, withMargin.getRealizedProfit(1));
            assertNumEquals(40, withMargin.getUnrealizedProfit(mark, 1));
        }
    }

    @Test
    void rejectsMixingSpotAndFuturesAndScalarOperations() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            TradeFill futuresFill = fill(contract, 0, ExecutionSide.BUY, 1, 50_000,
                    List.of(commission(numFactory, 0.1, "USD")));
            TradeFill spotFill = TradeFill.builder()
                    .index(0)
                    .time(T0)
                    .price(numFactory.numOf(50_000))
                    .amount(numFactory.one())
                    .side(ExecutionSide.BUY)
                    .build();

            BaseTradingRecord spotRecord = new BaseTradingRecord();
            IllegalArgumentException spotRejection = assertThrows(IllegalArgumentException.class,
                    () -> spotRecord.operate(futuresFill));
            assertTrue(spotRejection.getMessage().contains("A spot record cannot record fills of futures contract"));

            BaseTradingRecord futuresRecord = BaseTradingRecord.builder().futuresContract(contract).build();
            IllegalArgumentException futuresRejection = assertThrows(IllegalArgumentException.class,
                    () -> futuresRecord.operate(spotFill));
            assertTrue(futuresRejection.getMessage()
                    .contains("A futures record requires fills that reference the futures contract"));

            IllegalStateException scalarRejection = assertThrows(IllegalStateException.class,
                    () -> futuresRecord.operate(0, numFactory.numOf(50_000), numFactory.one()));
            assertTrue(scalarRejection.getMessage().contains("cannot be used with a futures record"));
            assertTrue(futuresRecord.getPositions().isEmpty());
            assertTrue(futuresRecord.getOpenPositions().isEmpty());

            FuturesContract otherContract = linearBtcPerpetual(numFactory).toBuilder().symbol("ETH-PERP").build();
            Trade futuresTrade = Trade.fromFill(futuresFill, RecordedTradeCostModel.INSTANCE);
            Trade otherTrade = Trade.fromFill(TradeFill.builder()
                    .index(1)
                    .time(T0.plusSeconds(1))
                    .price(numFactory.numOf(3_000))
                    .amount(numFactory.one())
                    .side(ExecutionSide.BUY)
                    .futuresContract(otherContract)
                    .fees(List.of(commission(numFactory, 0.1, "USD")))
                    .build(), RecordedTradeCostModel.INSTANCE);
            IllegalArgumentException mixedTrades = assertThrows(IllegalArgumentException.class,
                    () -> new BaseTradingRecord(futuresTrade, otherTrade));
            assertTrue(mixedTrades.getMessage().contains("All trades must reference the same futures contract"));

            Position futuresPosition = openPosition(contract, 0, 1, 50_000,
                    List.of(commission(numFactory, 0.1, "USD")));
            Trade spotEntry = new BaseTrade(0, T0, numFactory.numOf(100), numFactory.one(), numFactory.zero(),
                    ExecutionSide.BUY, null, null);
            Trade spotExit = new BaseTrade(1, T0.plusSeconds(1), numFactory.numOf(110), numFactory.one(),
                    numFactory.zero(), ExecutionSide.SELL, null, null);
            Position spotPosition = new Position(spotEntry, spotExit, spotEntry.getCostModel(), new ZeroCostModel());
            IllegalArgumentException mixedPositions = assertThrows(IllegalArgumentException.class,
                    () -> new BaseTradingRecord(List.of(futuresPosition, spotPosition)));
            assertTrue(mixedPositions.getMessage().contains("Cannot mix spot and futures positions in one record"));
        }
    }

    @Test
    void futuresRecordSurvivesSerializationAndKeepsTrading() throws Exception {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .initialMarginRate(numFactory.numOf(0.1))
                    .build();
            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(fill(contract, 1, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));

            BaseTradingRecord rehydrated = serializedCopy(record);

            assertEquals(contract, rehydrated.getFuturesContract());
            assertNumEquals(1_000, rehydrated.getInitialCapital());
            assertNumEquals(0.1, rehydrated.getInitialMarginRate());
            Position rehydratedClosed = rehydrated.getPositions().getFirst();
            assertNumEquals(8, rehydratedClosed.getProfit());
            assertNumEquals(1, rehydratedClosed.getEntry().getFees().getFirst().settlementAmount());
            Position rehydratedOpen = rehydrated.getOpenPositions().getFirst();
            assertNumEquals(3, rehydratedOpen.getEntry().getFees().getFirst().settlementAmount());
            assertNotNull(rehydratedClosed.getTransactionCostModel());

            record.operate(fill(contract, 2, ExecutionSide.SELL, 3, 12_000, List.of(commission(numFactory, 3, "USD"))));
            rehydrated.operate(
                    fill(contract, 2, ExecutionSide.SELL, 3, 12_000, List.of(commission(numFactory, 3, "USD"))));

            assertEquals(2, rehydrated.getPositions().size());
            for (int i = 0; i < 2; i++) {
                Position expected = record.getPositions().get(i);
                Position actual = rehydrated.getPositions().get(i);
                assertNumEquals(expected.getProfit(), actual.getProfit(), 1e-12);
                assertNumEquals(expected.getEntry().getCost(), actual.getEntry().getCost(), 1e-12);
                assertNumEquals(expected.getExit().getCost(), actual.getExit().getCost(), 1e-12);
                assertEquals(expected.getEntry().getOrderId(), actual.getEntry().getOrderId());
                assertEquals(expected.getExit().getOrderId(), actual.getExit().getOrderId());
            }
            assertTrue(rehydrated.getOpenPositions().isEmpty());
            assertNumEquals(8, rehydrated.getPositions().getFirst().getProfit());
            assertNumEquals(54, rehydrated.getPositions().get(1).getProfit());
        }
    }

    @Test
    void futuresPositionsConstructorPreservesCashFlowsThroughSerialization() throws Exception {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            Position position = new Position(
                    Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, 3, 10_000,
                            List.of(commission(numFactory, 3, "USD"))), RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel(),
                    List.of(FuturesCashFlow.builder()
                            .contract(contract)
                            .type(FuturesCashFlow.Type.VARIATION_MARGIN)
                            .eventId("vm-1")
                            .index(1)
                            .time(T0.plusSeconds(1))
                            .amount(numFactory.numOf(20))
                            .currency("USD")
                            .build()));
            BaseTradingRecord record = new BaseTradingRecord(List.of(position));

            assertEquals(contract, record.getFuturesContract());
            assertEquals(1, record.getOpenPositions().size());
            assertNumEquals(17, record.getOpenPositions().getFirst().getRealizedProfit(1));
            assertNumEquals(3, record.getTotalFees());

            BaseTradingRecord rehydrated = serializedCopy(record);

            Position restored = rehydrated.getOpenPositions().getFirst();
            assertEquals(1, restored.getCashFlows().size());
            assertNumEquals(20, restored.getCashFlows().getFirst().settlementAmount());
            assertNumEquals(17, restored.getRealizedProfit(1));
            assertNumEquals(40, restored.getUnrealizedProfit(numFactory.numOf(12_000), 1));
            assertNumEquals(57, restored.getProfit(1, numFactory.numOf(12_000)));
        }
    }

    @Test
    void spotPositionsKeepRealizedAndUnrealizedSplit() {
        for (NumFactory numFactory : factories()) {
            Trade entry = new BaseTrade(0, T0, numFactory.numOf(100), numFactory.one(), numFactory.zero(),
                    ExecutionSide.BUY, null, null);
            Trade exit = new BaseTrade(1, T0.plusSeconds(1), numFactory.numOf(110), numFactory.one(), numFactory.zero(),
                    ExecutionSide.SELL, null, null);

            Position closed = new Position(entry, exit, entry.getCostModel(), new ZeroCostModel());
            assertNumEquals(10, closed.getProfit());
            assertNumEquals(10, closed.getRealizedProfit(1));
            assertNumEquals(0, closed.getUnrealizedProfit(numFactory.numOf(110), 1));
            assertNumEquals(3, closed.getReturnOnMargin(numFactory.numOf(5), numFactory.numOf(110), 1));

            Trade costlyEntry = new BaseTrade(0, T0, numFactory.numOf(100), numFactory.one(), numFactory.one(),
                    ExecutionSide.BUY, null, null);
            Position open = new Position(costlyEntry, costlyEntry.getCostModel(), new ZeroCostModel());
            assertNumEquals(9, open.getProfit(0, numFactory.numOf(110)));
            assertNumEquals(-1, open.getRealizedProfit(0));
            assertNumEquals(10, open.getUnrealizedProfit(numFactory.numOf(110), 0));
            assertNumEquals(open.getProfit(0, numFactory.numOf(110)),
                    open.getUnrealizedProfit(numFactory.numOf(110), 0).plus(open.getRealizedProfit(0)));
        }
    }

    @Test
    void netReturnCriterionUsesEntrySettlementNotionalForFutures() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = inverseBtcPerpetual(numFactory);
            Position position = new Position(
                    Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, 100, 20_000,
                            List.of(commission(numFactory, 0.0001, "BTC"))), RecordedTradeCostModel.INSTANCE),
                    Trade.fromFill(fill(contract, 1, ExecutionSide.SELL, 100, 25_000,
                            List.of(commission(numFactory, 0.00012, "BTC"))), RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();

            assertNumEquals(1.19956, new NetReturnCriterion().calculate(series, position));
        }
    }

    @Test
    void fundingBoundariesDecideWhoPaysAndEventsCannotMoveBackInTime() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            FuturesFunding atEntry = fundingEvent(contract, 10, 0.001, 10_000);
            FuturesFunding afterClose = fundingEvent(contract, 40, 0.001, 10_000);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .fundingSchedule(List.of(atEntry, afterClose))
                    .build();

            record.operate(fill(contract, 9, ExecutionSide.BUY, 2, 10_000, List.of()));
            record.recordFunding(atEntry);

            assertEquals(1, record.getCashFlows().size());
            FuturesCashFlow charged = record.getCashFlows().getFirst();
            assertEquals("funding-10", charged.eventId());
            assertEquals(FuturesCashFlow.Type.FUNDING, charged.type());
            assertNumEquals(-0.2, charged.amount());
            assertNumEquals(-0.2, record.getCurrentPosition().getRealizedProfit(10));

            // A duplicate of the same event is counted once, and a lot entered
            // exactly at the funding boundary is not charged for it.
            record.recordFunding(atEntry);
            record.operate(fill(contract, 10, ExecutionSide.BUY, 1, 10_000, List.of()));
            assertEquals(1, record.getCashFlows().size());
            assertEquals(2, record.getOpenPositions().size());
            assertNumEquals(-0.2, record.getCurrentPosition().getRealizedProfit(10));
            assertNumEquals(-0.2, record.getOpenPositions().get(0).getCashFlows().getFirst().amount());
            assertTrue(record.getOpenPositions().get(1).getCashFlows().isEmpty());

            IllegalArgumentException conflicting = assertThrows(IllegalArgumentException.class, () -> record
                    .recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-10", 10, 0.3)));
            assertTrue(conflicting.getMessage().contains("already recorded with different values"));
            assertEquals(1, record.getCashFlows().size());

            record.operate(fill(contract, 19, ExecutionSide.SELL, 3, 11_000, List.of()));
            assertEquals(2, record.getPositions().size());
            assertNumEquals(19.8, record.getPositions().get(0).getProfit());
            assertNumEquals(10, record.getPositions().get(1).getProfit());

            // A funding event observed at the exit instant still charges the slices
            // that were held immediately before it.
            record.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "late-at-exit", 19, -0.3));

            Position firstClose = record.getPositions().get(0);
            Position secondClose = record.getPositions().get(1);
            assertEquals(2, record.getCashFlows().size());
            assertEquals(2, firstClose.getCashFlows().size());
            assertNumEquals(-0.2, firstClose.getCashFlows().getLast().amount());
            assertEquals(1, secondClose.getCashFlows().size());
            assertNumEquals(-0.1, secondClose.getCashFlows().getLast().amount());
            assertNumEquals(19.6, firstClose.getProfit());
            assertNumEquals(19.6, firstClose.getRealizedProfit(19));
            assertNumEquals(9.9, secondClose.getProfit());
            assertNumEquals(29.5, firstClose.getProfit().plus(secondClose.getProfit()));

            // An event after every exit has no held exposure: rejected without mutation.
            IllegalArgumentException unheld = assertThrows(IllegalArgumentException.class, () -> record
                    .recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "after-exit", 20, -0.3)));
            assertTrue(unheld.getMessage().contains("No eligible futures exposure"));
            assertEquals(2, record.getCashFlows().size());
            assertNumEquals(29.5,
                    record.getPositions().get(0).getProfit().plus(record.getPositions().get(1).getProfit()));

            IllegalArgumentException backwards = assertThrows(IllegalArgumentException.class,
                    () -> record.operate(fill(contract, 15, ExecutionSide.BUY, 1, 10_000, List.of())));
            assertTrue(backwards.getMessage().contains("precedes the processed event horizon"));
            assertEquals(2, record.getPositions().size());
            assertEquals(2, record.getCashFlows().size());

            record.advanceTo(T0.plusSeconds(40));
            assertEquals(3, record.getCashFlows().size());
            assertTrue(record.getCashFlows().getLast().amount().isZero());
            record.advanceTo(T0.plusSeconds(40));
            record.advanceTo(T0);
            assertEquals(3, record.getCashFlows().size());
        }
    }

    @Test
    void partialCloseFundingAndVariationMarginConserveRecordProfit() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .fundingSchedule(
                            List.of(fundingEvent(contract, 1, 0.001, 11_000), fundingEvent(contract, 3, 0.001, 12_000)))
                    .build();

            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(fill(contract, 2, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));

            assertNumEquals(7.89, record.getPositions().getFirst().getProfit());
            assertNumEquals(-0.44, record.getCashFlows().getFirst().amount());

            Position openAfterPartialClose = record.getCurrentPosition();
            assertNumEquals(3, openAfterPartialClose.getEntry().getAmount());
            assertNumEquals(-3.33, openAfterPartialClose.getRealizedProfit(2));
            assertNumEquals(26.67, openAfterPartialClose.getProfit(2, numFactory.numOf(11_000)));

            record.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.VARIATION_MARGIN, "vm-4", 4, 20));

            assertEquals(3, record.getCashFlows().size());
            assertNumEquals(-0.36, record.getCashFlows().get(1).amount());
            Position open = record.getCurrentPosition();
            assertNumEquals(16.31, open.getRealizedProfit(4));
            assertNumEquals(40, open.getUnrealizedProfit(numFactory.numOf(12_000), 4));
            assertNumEquals(56.31, open.getProfit(4, numFactory.numOf(12_000)));
            assertNumEquals(24.2,
                    record.getPositions().getFirst().getRealizedProfit(4).plus(open.getRealizedProfit(4)));
            assertNumEquals(5, record.getTotalFees());

            record.operate(fill(contract, 5, ExecutionSide.SELL, 3, 12_000, List.of(commission(numFactory, 3, "USD"))));

            assertEquals(2, record.getPositions().size());
            assertNumEquals(7.89, record.getPositions().get(0).getProfit());
            assertNumEquals(53.31, record.getPositions().get(1).getProfit());
            assertNumEquals(61.2,
                    record.getPositions().get(0).getProfit().plus(record.getPositions().get(1).getProfit()));
            assertNumEquals(61.2,
                    record.getPositions()
                            .get(1)
                            .getRealizedProfit(5)
                            .plus(record.getPositions().get(0).getRealizedProfit(5)));
            assertTrue(record.getOpenPositions().isEmpty());
            assertNumEquals(8, record.getTotalFees());
        }
    }

    @Test
    void lateObservedCashFlowAllocatesAcrossClosedAndOpenSlices() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(
                    fill(contract, 10, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));

            Position closedBeforeFlow = record.getPositions().getFirst();
            Position openBeforeFlow = record.getCurrentPosition();
            assertNumEquals(8, closedBeforeFlow.getProfit());
            assertTrue(closedBeforeFlow.getCashFlows().isEmpty());
            assertNumEquals(27, openBeforeFlow.getProfit(10, numFactory.numOf(11_000)));

            FuturesCashFlow late = cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-late", 5, 4);
            record.recordCashFlow(late);

            assertTrue(closedBeforeFlow.getCashFlows().isEmpty());
            assertNumEquals(8, closedBeforeFlow.getRealizedProfit(10));
            assertTrue(openBeforeFlow.getCashFlows().isEmpty());
            assertNumEquals(27, openBeforeFlow.getProfit(10, numFactory.numOf(11_000)));

            Position closed = record.getPositions().getFirst();
            assertEquals(1, closed.getCashFlows().size());
            assertNumEquals(1, closed.getCashFlows().getFirst().amount());
            assertNumEquals(9, closed.getRealizedProfit(10));

            Position open = record.getCurrentPosition();
            assertEquals(1, open.getCashFlows().size());
            assertNumEquals(3, open.getCashFlows().getFirst().amount());
            assertNumEquals(0, open.getRealizedProfit(10));
            assertNumEquals(30, open.getUnrealizedProfit(numFactory.numOf(11_000), 10));
            assertNumEquals(30, open.getProfit(10, numFactory.numOf(11_000)));
            assertNumEquals(30,
                    open.getRealizedProfit(10).plus(open.getUnrealizedProfit(numFactory.numOf(11_000), 10)));

            assertEquals(1, record.getCashFlows().size());
            assertNumEquals(4, record.getCashFlows().getFirst().amount());

            record.recordCashFlow(late);
            assertEquals(1, record.getCashFlows().size());
            assertNumEquals(9, record.getPositions().getFirst().getRealizedProfit(10));
            assertNumEquals(0, record.getCurrentPosition().getRealizedProfit(10));
        }
    }

    @Test
    void snapshotsAreRetainedForFuturesRecordsAndImmutable() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .fundingSchedule(
                            List.of(fundingEvent(contract, 3, 0.001, 12_000), fundingEvent(contract, 1, 0.001, 11_000)))
                    .build();

            assertEquals("funding-1", record.getFundingSchedule().get(0).eventId());
            assertEquals("funding-3", record.getFundingSchedule().get(1).eventId());

            FuturesMarketSnapshot market = FuturesMarketSnapshot.builder()
                    .contract(contract)
                    .observedAt(T0)
                    .markPrice(numFactory.numOf(11_000))
                    .build();
            FuturesPositionSnapshot position = FuturesPositionSnapshot.builder()
                    .contract(contract)
                    .observedAt(T0)
                    .signedContracts(numFactory.numOf(3))
                    .realizedPnl(numFactory.numOf(1))
                    .build();
            record.recordMarketSnapshot(market);
            record.recordPositionSnapshot(position);

            assertEquals(1, record.getMarketSnapshots().size());
            assertNumEquals(11_000, record.getMarketSnapshots().getFirst().markPrice());
            assertEquals(1, record.getPositionSnapshots().size());
            assertNumEquals(3, record.getPositionSnapshots().getFirst().signedContracts());
            assertThrows(UnsupportedOperationException.class, () -> record.getMarketSnapshots().clear());
            assertThrows(UnsupportedOperationException.class, () -> record.getPositionSnapshots().clear());

            FuturesContract foreign = contract.toBuilder().symbol("ETH-PERP").build();
            IllegalArgumentException foreignMarket = assertThrows(IllegalArgumentException.class, () -> record
                    .recordMarketSnapshot(FuturesMarketSnapshot.builder().contract(foreign).observedAt(T0).build()));
            assertTrue(foreignMarket.getMessage().contains("does not match the record contract"));
            IllegalArgumentException foreignPosition = assertThrows(IllegalArgumentException.class,
                    () -> record.recordPositionSnapshot(FuturesPositionSnapshot.builder()
                            .contract(foreign)
                            .observedAt(T0)
                            .signedContracts(numFactory.numOf(1))
                            .build()));
            assertTrue(foreignPosition.getMessage().contains("does not match the record contract"));
            assertEquals(1, record.getMarketSnapshots().size());
            assertEquals(1, record.getPositionSnapshots().size());

            BaseTradingRecord spot = new BaseTradingRecord();
            assertThrows(UnsupportedOperationException.class,
                    () -> spot.recordFunding(fundingEvent(contract, 1, 0.001, 11_000)));
            assertThrows(UnsupportedOperationException.class,
                    () -> spot.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-1", 1, -0.1)));
            assertThrows(UnsupportedOperationException.class, () -> spot.recordMarketSnapshot(market));
            assertThrows(UnsupportedOperationException.class, () -> spot.recordPositionSnapshot(position));
            spot.advanceTo(T0);
            assertTrue(spot.getCashFlows().isEmpty());
            assertTrue(spot.getMarketSnapshots().isEmpty());
            assertTrue(spot.getPositionSnapshots().isEmpty());
            assertTrue(spot.getFundingSchedule().isEmpty());
        }
    }

    @Test
    void fundingScheduleAndCursorSurviveSerialization() throws Exception {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .fundingSchedule(
                            List.of(fundingEvent(contract, 1, 0.001, 11_000), fundingEvent(contract, 3, 0.001, 12_000)))
                    .build();
            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(fill(contract, 4, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));

            assertEquals(2, record.getFundingSchedule().size());
            assertEquals(2, record.getCashFlows().size());
            assertNumEquals(-0.44, record.getCashFlows().get(0).amount());
            assertNumEquals(-0.48, record.getCashFlows().get(1).amount());
            assertNumEquals(7.77, record.getPositions().getFirst().getProfit());
            assertNumEquals(-3.69, record.getOpenPositions().getFirst().getRealizedProfit(4));

            BaseTradingRecord rehydrated = serializedCopy(record);

            assertEquals(contract, rehydrated.getFuturesContract());
            assertEquals(2, rehydrated.getFundingSchedule().size());
            assertEquals(2, rehydrated.getCashFlows().size());
            assertNumEquals(-0.44, rehydrated.getCashFlows().get(0).amount());
            assertNumEquals(-0.48, rehydrated.getCashFlows().get(1).amount());
            assertNumEquals(7.77, rehydrated.getPositions().getFirst().getProfit());
            assertNumEquals(-3.69, rehydrated.getOpenPositions().getFirst().getRealizedProfit(4));

            record.operate(fill(contract, 5, ExecutionSide.BUY, 2, 12_000, List.of(commission(numFactory, 2, "USD"))));
            rehydrated.operate(
                    fill(contract, 5, ExecutionSide.BUY, 2, 12_000, List.of(commission(numFactory, 2, "USD"))));

            assertEquals(2, record.getCashFlows().size());
            assertEquals(2, rehydrated.getCashFlows().size());
            assertEquals(2, rehydrated.getOpenPositions().size());
            assertNumEquals(-3.69, rehydrated.getOpenPositions().get(0).getRealizedProfit(5));
            assertNumEquals(-2, rehydrated.getOpenPositions().get(1).getRealizedProfit(5));
            assertNumEquals(5,
                    rehydrated.getOpenPositions()
                            .get(0)
                            .getEntry()
                            .getAmount()
                            .plus(rehydrated.getOpenPositions().get(1).getEntry().getAmount()));
            assertNumEquals(54.31, rehydrated.getCurrentPosition().getProfit(5, numFactory.numOf(12_000)));
            assertNumEquals(record.getCurrentPosition().getProfit(5, numFactory.numOf(12_000)),
                    rehydrated.getCurrentPosition().getProfit(5, numFactory.numOf(12_000)), 1e-12);
        }
    }

    @Test
    void rehydratedRecordAppliesPendingFundingAndSnapshotsExactlyOnce() throws Exception {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .fundingSchedule(
                            List.of(fundingEvent(contract, 1, 0.001, 11_000), fundingEvent(contract, 5, 0.001, 12_000)))
                    .build();
            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(fill(contract, 2, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));
            record.recordMarketSnapshot(FuturesMarketSnapshot.builder()
                    .contract(contract)
                    .observedAt(T0)
                    .markPrice(numFactory.numOf(11_000))
                    .build());
            record.recordPositionSnapshot(FuturesPositionSnapshot.builder()
                    .contract(contract)
                    .observedAt(T0)
                    .signedContracts(numFactory.numOf(3))
                    .realizedPnl(numFactory.numOf(1))
                    .build());

            // The schedule entry behind the processed horizon stays pending.
            assertEquals(1, record.getCashFlows().size());
            assertNumEquals(-0.44, record.getCashFlows().getFirst().amount());

            BaseTradingRecord rehydrated = serializedCopy(record);
            assertEquals(record.getFundingSchedule(), rehydrated.getFundingSchedule());
            assertEquals(record.getCashFlows(), rehydrated.getCashFlows());
            assertEquals(record.getMarketSnapshots(), rehydrated.getMarketSnapshots());
            assertEquals(record.getPositionSnapshots(), rehydrated.getPositionSnapshots());
            assertNumEquals(7.89, rehydrated.getPositions().getFirst().getProfit());
            assertNumEquals(-3.33, rehydrated.getCurrentPosition().getRealizedProfit(2));

            record.advanceTo(T0.plusSeconds(6));
            rehydrated.advanceTo(T0.plusSeconds(6));

            // The pending entry is charged once and only once after rehydration.
            assertEquals(2, record.getCashFlows().size());
            assertEquals(2, rehydrated.getCashFlows().size());
            assertEquals(record.getCashFlows(), rehydrated.getCashFlows());
            assertNumEquals(-0.36, rehydrated.getCashFlows().get(1).amount());
            assertNumEquals(-0.8,
                    rehydrated.getCashFlows()
                            .stream()
                            .map(FuturesCashFlow::amount)
                            .reduce(numFactory.zero(), Num::plus));
            assertNumEquals(-3.69, rehydrated.getCurrentPosition().getRealizedProfit(6));
            assertEquals(record.getPositions().getFirst().getRealizedProfit(6),
                    rehydrated.getPositions().getFirst().getRealizedProfit(6));
        }
    }

    private static BaseTradingRecord serializedCopy(BaseTradingRecord record) throws Exception {
        byte[] data;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(record);
            objectOutput.flush();
            data = output.toByteArray();
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(data);
                ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return (BaseTradingRecord) objectInput.readObject();
        }
    }

    @Test
    void scheduledFundingChargesExposureHeldAtItsOwnTimestamp() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord scheduled = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .fundingSchedule(List.of(fundingEvent(contract, 10, 0.001, 10_000)))
                    .build();

            scheduled.operate(fill(contract, 0, ExecutionSide.BUY, 2, 10_000, List.of()));
            // The close fill advances the accounting horizon past the funding time
            // before the schedule is ever reached.
            scheduled.operate(fill(contract, 15, ExecutionSide.SELL, 2, 11_000, List.of()));

            assertEquals(1, scheduled.getCashFlows().size());
            assertNumEquals(-0.2, scheduled.getCashFlows().getFirst().amount());
            Position scheduledClose = scheduled.getPositions().getFirst();
            assertEquals(1, scheduledClose.getCashFlows().size());
            assertNumEquals(-0.2, scheduledClose.getCashFlows().getFirst().amount());
            assertNumEquals(19.8, scheduledClose.getRealizedProfit(15));

            // An out-of-band observation older than the horizon charges the slice
            // that was held at the event's own timestamp.
            BaseTradingRecord observed = BaseTradingRecord.builder().futuresContract(contract).build();
            observed.operate(fill(contract, 0, ExecutionSide.BUY, 2, 10_000, List.of()));
            observed.operate(fill(contract, 15, ExecutionSide.SELL, 2, 11_000, List.of()));
            observed.recordFunding(fundingEvent(contract, 10, 0.001, 10_000));

            assertEquals(1, observed.getCashFlows().size());
            assertNumEquals(-0.2, observed.getCashFlows().getFirst().amount());
            Position observedClose = observed.getPositions().getFirst();
            assertNumEquals(-0.2, observedClose.getCashFlows().getFirst().amount());
            assertNumEquals(19.8, observedClose.getRealizedProfit(15));

            // Every funding time is behind the horizon: nothing more is charged.
            observed.advanceTo(T0);
            assertEquals(1, observed.getCashFlows().size());
        }
    }

    @Test
    void projectedFuturesCopiesOnlyTheSelectedPositionsAndTheirFees() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord source = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .initialMarginRate(numFactory.numOf(0.5))
                    .build();
            source.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            source.operate(fill(contract, 5, ExecutionSide.SELL, 4, 11_000, List.of(commission(numFactory, 2, "USD"))));
            source.operate(fill(contract, 6, ExecutionSide.BUY, 2, 11_000, List.of(commission(numFactory, 3, "USD"))));
            source.operate(fill(contract, 9, ExecutionSide.SELL, 2, 12_000, List.of(commission(numFactory, 1, "USD"))));
            assertNumEquals(10, source.getRecordedTotalFees());

            BaseTradingRecord projected = BaseTradingRecord.projectedFutures(source,
                    List.of(source.getPositions().get(1)), 6, 9);

            assertEquals(contract, projected.getFuturesContract());
            assertNumEquals(numFactory.numOf(1_000), projected.getInitialCapital());
            assertNumEquals(numFactory.numOf(0.5), projected.getInitialMarginRate());
            assertTrue(projected.getFundingSchedule().isEmpty());
            assertEquals(1, projected.getPositions().size());
            assertTrue(projected.getOpenPositions().isEmpty());

            Position selected = projected.getPositions().getFirst();
            assertEquals(6, selected.getEntry().getIndex());
            assertEquals(9, selected.getExit().getIndex());
            assertNumEquals(4, projected.getRecordedTotalFees());
            assertNumEquals(16, selected.getRealizedProfit(9));
        }
    }

    @Test
    void projectedFuturesAggregatesOnlyTheAllocatedEvents() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord source = BaseTradingRecord.builder().futuresContract(contract).build();
            source.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            source.operate(
                    fill(contract, 10, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));
            source.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-late", 5, 4));

            List<Position> slices = new ArrayList<>(source.getPositions());
            slices.addAll(source.getOpenPositions());
            assertEquals(2, slices.size());

            // The event is split across both slices: the projection reports it once
            // with the summed allocation, never twice.
            BaseTradingRecord whole = BaseTradingRecord.projectedFutures(source, slices, 0, 10);
            assertEquals(1, whole.getCashFlows().size());
            assertNumEquals(4, whole.getCashFlows().getFirst().amount());
            assertEquals("funding-late", whole.getCashFlows().getFirst().eventId());
            assertNumEquals(9, whole.getPositions().getFirst().getRealizedProfit(10));

            // Only the closed slice is selected: only its own allocation is reported.
            BaseTradingRecord closedOnly = BaseTradingRecord.projectedFutures(source, List.of(slices.getFirst()), 0,
                    10);
            assertEquals(1, closedOnly.getCashFlows().size());
            assertNumEquals(1, closedOnly.getCashFlows().getFirst().amount());

            // The window end cuts the allocations away from both slices.
            BaseTradingRecord trimmed = BaseTradingRecord.projectedFutures(source, slices, 0, 3);
            assertTrue(trimmed.getCashFlows().isEmpty());
            assertNumEquals(8, trimmed.getPositions().getFirst().getRealizedProfit(10));

            // The source record keeps every allocation and the live event row.
            assertEquals(1, source.getCashFlows().size());
            assertNumEquals(4, source.getCashFlows().getFirst().amount());
            assertNumEquals(1, source.getPositions().getFirst().getCashFlows().getFirst().amount());
            assertNumEquals(3, source.getCurrentPosition().getCashFlows().getFirst().amount());
        }
    }

    @Test
    void projectedFuturesRejectsEveryMutation() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord source = BaseTradingRecord.builder().futuresContract(contract).build();
            source.operate(fill(contract, 0, ExecutionSide.BUY, 1, 10_000, List.of(commission(numFactory, 1, "USD"))));
            BaseTradingRecord projected = BaseTradingRecord.projectedFutures(source, source.getOpenPositions(), 0, 1);

            assertThrows(UnsupportedOperationException.class,
                    () -> projected.operate(fill(contract, 1, ExecutionSide.BUY, 1, 10_000, List.of())));
            assertThrows(UnsupportedOperationException.class, () -> projected.operate(1));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.operate(1, numFactory.numOf(10_000), numFactory.numOf(1)));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.enter(1, numFactory.numOf(10_000), numFactory.numOf(1)));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.exit(1, numFactory.numOf(10_000), numFactory.numOf(1)));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "later", 1, 1)));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.recordFunding(fundingEvent(contract, 1, 0.001, 10_000)));
            assertThrows(UnsupportedOperationException.class, () -> projected.recordMarketSnapshot(
                    FuturesMarketSnapshot.builder().contract(contract).observedAt(T0.plusSeconds(1)).build()));
            assertThrows(UnsupportedOperationException.class, () -> projected.advanceTo(T0.plusSeconds(1)));
            assertThrows(UnsupportedOperationException.class, () -> projected.setName("projected"));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.rehydrate(new ZeroCostModel(), new ZeroCostModel()));

            assertEquals(1, projected.getPositions().size() + projected.getOpenPositions().size());
        }
    }

    @Test
    void projectedFuturesRequiresAFuturesSource() {
        BaseTradingRecord spot = new BaseTradingRecord();
        assertThrows(IllegalArgumentException.class, () -> BaseTradingRecord.projectedFutures(spot, List.of(), 0, 1));
    }
}
