
/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.time.Instant;
import java.util.List;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesCashFlow;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

final class FuturesAnalysisTestSupport {

    static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private FuturesAnalysisTestSupport() {
    }

    static List<NumFactory> factories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    static FuturesContract linearBtcPerpetual(NumFactory numFactory) {
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

    static BarSeries series(NumFactory numFactory, double... closes) {
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
    }

    static BarSeries markToMarketSeries(NumFactory numFactory) {
        return series(numFactory, 100, 102, 105, 103, 110);
    }

    static TradeFee commission(NumFactory numFactory, double amount) {
        return TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(numFactory.numOf(amount))
                .currency("USD")
                .build();
    }

    static TradeFill fill(FuturesContract contract, int index, ExecutionSide side, double amount, double price,
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

    static Position openPosition(FuturesContract contract, int index, double amount, double price) {
        Trade entry = Trade.fromFill(fill(contract, index, ExecutionSide.BUY, amount, price, List.of()),
                RecordedTradeCostModel.INSTANCE);
        return new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
    }

    static FuturesCashFlow variationMargin(FuturesContract contract, int index, double amount) {
        return FuturesCashFlow.builder()
                .contract(contract)
                .type(FuturesCashFlow.Type.VARIATION_MARGIN)
                .eventId("vm-" + index)
                .index(index)
                .time(T0.plusSeconds(index))
                .amount(contract.contractSize().getNumFactory().numOf(amount))
                .currency(contract.settlementCurrency())
                .build();
    }

    static BaseTradingRecord fundedRecord(FuturesContract contract, NumFactory numFactory, double capital) {
        return BaseTradingRecord.builder().futuresContract(contract).initialCapital(numFactory.numOf(capital)).build();
    }
}
