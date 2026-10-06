/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import java.util.ArrayList;
import org.ta4j.core.TradeFee;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class RecordedTradeCostModelTest {

    @Test
    public void calculateSpotPositionAtFinalIndexUsesOnlyExecutedFillFees() {
        TradeFill firstFill = new TradeFill(0, Instant.EPOCH, DoubleNum.valueOf(100), DoubleNum.valueOf(1),
                DoubleNum.valueOf(0.1), ExecutionSide.BUY, null, null);
        TradeFill secondFill = new TradeFill(2, Instant.EPOCH.plusSeconds(120), DoubleNum.valueOf(110),
                DoubleNum.valueOf(1), DoubleNum.valueOf(0.2), ExecutionSide.BUY, null, null);
        Trade entry = Trade.fromFills(Trade.TradeType.BUY, List.of(firstFill, secondFill),
                RecordedTradeCostModel.INSTANCE);
        Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

        Num costAtFirstFill = RecordedTradeCostModel.INSTANCE.calculate(position, 0);
        Num costAtSecondFill = RecordedTradeCostModel.INSTANCE.calculate(position, 2);

        assertNumEquals(0.1, costAtFirstFill);
        assertNumEquals(0.3, costAtSecondFill);
    }

    @Test
    public void nativeRecordedFeesAcrossFillsRetainCancellationResidualDouble() {
        assertNativeFeeCompensation(DoubleNumFactory.getInstance(), false);
    }

    @Test
    public void nativeRecordedFeesAcrossFillsRetainCancellationResidualDecimal() {
        assertNativeFeeCompensation(DecimalNumFactory.getInstance(), false);
    }

    @Test
    public void nativeRecordedFeesAcrossTradesRetainCancellationResidualDouble() {
        assertNativeFeeCompensation(DoubleNumFactory.getInstance(), true);
    }

    @Test
    public void nativeRecordedFeesAcrossTradesRetainCancellationResidualDecimal() {
        assertNativeFeeCompensation(DecimalNumFactory.getInstance(), true);
    }

    private static void assertNativeFeeCompensation(NumFactory factory, boolean splitAcrossTrades) {
        CostModel model = RecordedTradeCostModel.INSTANCE;
        Position position = RecordedTradeCostModelTest.feeCancellationPosition(factory, model, splitAcrossTrades);
        assertNumEquals(1, model.calculate(position, 2));
        assertNumEquals(1, model.calculate(position));
        assertNumEquals(1, position.getPositionCost(2));
        assertNumEquals(1e16, model.calculate(position, 0));
    }

    static Position feeCancellationPosition(NumFactory factory, CostModel model, boolean splitAcrossTrades) {
        return feeCancellationPosition(factory, model, splitAcrossTrades, false);
    }

    static Position feeCancellationPosition(NumFactory factory, CostModel model, boolean splitAcrossTrades,
            boolean groupedComponents) {
        FuturesContract contract = FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(factory.one())
                .build();
        List<TradeFill> entries = new ArrayList<>();
        double[] fees = { 1e16, 1d, -1e16 };
        for (int index = 0; index < (splitAcrossTrades ? 2 : 3); index++) {
            TradeFee fee = TradeFee.builder()
                    .type(TradeFee.Type.COMMISSION)
                    .amount(factory.numOf(fees[index]))
                    .currency("USD")
                    .build();
            entries.add(TradeFill.builder()
                    .index(index)
                    .time(Instant.EPOCH.plusSeconds(index))
                    .side(ExecutionSide.BUY)
                    .price(factory.hundred())
                    .amount(factory.one())
                    .futuresContract(contract)
                    .fees(List.of(fee))
                    .build());
        }
        if (groupedComponents) {
            TradeFee smallFee = entries.get(1).fees().getFirst().toBuilder().type(TradeFee.Type.EXCHANGE).build();
            TradeFill first = entries.getFirst()
                    .toBuilder()
                    .fees(List.of(entries.getFirst().fees().getFirst(), smallFee))
                    .build();
            entries = splitAcrossTrades ? List.of(first)
                    : List.of(first, entries.get(2).toBuilder().index(1).time(Instant.EPOCH.plusSeconds(1)).build());
        }
        Trade entry = Trade.fromFills(Trade.TradeType.BUY, entries, model);
        if (!splitAcrossTrades)
            return new Position(entry, model, new ZeroCostModel());
        TradeFee exitFee = TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(factory.numOf(-1e16))
                .currency("USD")
                .build();
        Trade exit = Trade.fromFill(TradeFill.builder()
                .index(2)
                .time(Instant.EPOCH.plusSeconds(2))
                .side(ExecutionSide.SELL)
                .price(factory.hundred())
                .amount(groupedComponents ? factory.one() : factory.two())
                .futuresContract(contract)
                .fees(List.of(exitFee))
                .build(), model);
        return new Position(entry, exit, model, new ZeroCostModel());
    }

    @Test
    public void groupedNativeFeesAcrossFillsRetainResidualDouble() {
        assertGroupedNativeFeeCompensation(DoubleNumFactory.getInstance(), false);
    }

    @Test
    public void groupedNativeFeesAcrossFillsRetainResidualDecimal() {
        assertGroupedNativeFeeCompensation(DecimalNumFactory.getInstance(), false);
    }

    @Test
    public void groupedNativeFeesAcrossTradesRetainResidualDouble() {
        assertGroupedNativeFeeCompensation(DoubleNumFactory.getInstance(), true);
    }

    @Test
    public void groupedNativeFeesAcrossTradesRetainResidualDecimal() {
        assertGroupedNativeFeeCompensation(DecimalNumFactory.getInstance(), true);
    }

    private static void assertGroupedNativeFeeCompensation(NumFactory factory, boolean splitAcrossTrades) {
        CostModel model = RecordedTradeCostModel.INSTANCE;
        Position position = RecordedTradeCostModelTest.feeCancellationPosition(factory, model, splitAcrossTrades, true);
        assertNumEquals(1, model.calculate(position, 2));
        assertNumEquals(1, model.calculate(position));
        assertNumEquals(1, position.getPositionCost(2));
        assertNumEquals(1e16, model.calculate(position, 0));
        assertNumEquals(0, model.calculate(position, -1));
    }
}
