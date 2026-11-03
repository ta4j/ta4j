
/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.time.Instant;
import java.util.List;
import java.util.function.IntConsumer;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesCashFlow;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

final class FuturesAnalysisTestSupport {

    static final Instant T0 = Instant.parse("1960-01-01T00:00:00Z");

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

    static FuturesContract inverseBtcPerpetual(NumFactory numFactory) {
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

    static BarSeries series(NumFactory numFactory, double... closes) {
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
    }

    static Indicator<Num> markWithReadAction(BarSeries series, IntConsumer beforeRead) {
        return new ConstantIndicator<Num>(series, series.numFactory().zero()) {
            @Override
            public Num getValue(int index) {
                beforeRead.accept(index);
                return series.getBar(index).getClosePrice();
            }
        };
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

    static BaseTradingRecord crossLotFeeRecord(FuturesContract contract, Trade.TradeType type, boolean closed) {
        NumFactory factory = contract.contractSize().getNumFactory();
        ExecutionSide entrySide = type == Trade.TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .startingType(type)
                .initialCapital(factory.numOf(500))
                .build();
        TradeFill first = fill(contract, 1, entrySide, 1, 100,
                List.of(commission(factory, 1e16).toBuilder().currency(contract.settlementCurrency()).build(),
                        commission(factory, 1).toBuilder().currency(contract.settlementCurrency()).build()));
        TradeFill second = fill(contract, 1, entrySide, 1, 100,
                List.of(commission(factory, -1e16).toBuilder().currency(contract.settlementCurrency()).build()))
                .toBuilder()
                .time(first.time().plusNanos(1))
                .build();
        record.operate(first);
        record.operate(second);
        if (closed) {
            ExecutionSide exitSide = type == Trade.TradeType.BUY ? ExecutionSide.SELL : ExecutionSide.BUY;
            record.operate(fill(contract, 2, exitSide, 1, 100, List.of()));
            record.operate(fill(contract, 3, exitSide, 1, 100, List.of()));
        }
        return record;
    }

    static BaseTradingRecord roundedPreWindowProfitRecord(NumFactory factory) {
        FuturesContract contract = linearBtcPerpetual(factory).toBuilder().contractSize(factory.one()).build();
        BaseTradingRecord record = fundedRecord(contract, factory, 500);
        record.operate(fill(contract, 0, ExecutionSide.BUY, 1, 100, List.of()));
        record.operate(fill(contract, 1, ExecutionSide.SELL, 1, 1e16 + 100,
                List.of(commission(factory, 1e16), commission(factory, 1))));
        record.operate(fill(contract, 2, ExecutionSide.BUY, 1, 100, List.of()));
        return record;
    }

    static TradingRecord mixedFactoryRecord(NumFactory analysisFactory, boolean reverse, boolean closed) {
        NumFactory doubles = DoubleNumFactory.getInstance();
        NumFactory decimals = DecimalNumFactory.getInstance();
        FuturesContract contract = linearBtcPerpetual(doubles).toBuilder().contractSize(doubles.one()).build();
        Position zero = factoryPosition(contract, doubles, 0, closed);
        Position gain = factoryPosition(contract, decimals, 1, closed);
        List<Position> positions = reverse ? List.of(gain, zero) : List.of(zero, gain);
        return new BaseTradingRecord() {
            @Override
            public FuturesContract getFuturesContract() {
                return contract;
            }

            @Override
            public Num getInitialCapital() {
                return analysisFactory.numOf(500);
            }

            @Override
            public List<Position> getPositions() {
                return closed ? positions : List.of();
            }

            @Override
            public List<Position> getOpenPositions() {
                return closed ? List.of() : positions;
            }

            @Override
            public Position getCurrentPosition() {
                return new Position();
            }
        };
    }

    private static Position factoryPosition(FuturesContract contract, NumFactory factory, double gain, boolean closed) {
        TradeFee fee = TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .currency("USD")
                .amount(factory.numOf(gain == 0 ? "0" : "1e-400"))
                .build();
        Trade entry = Trade.fromFill(TradeFill.builder()
                .futuresContract(contract)
                .index(0)
                .time(T0)
                .price(factory.numOf(closed ? 100 : 100 - gain))
                .amount(factory.one())
                .side(ExecutionSide.BUY)
                .fees(closed ? List.of() : List.of(fee))
                .build(), RecordedTradeCostModel.INSTANCE);
        if (!closed) {
            return new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        }
        Trade exit = Trade.fromFill(TradeFill.builder()
                .futuresContract(contract)
                .index(0)
                .time(T0.plusNanos(1))
                .price(factory.numOf(100 + gain))
                .amount(factory.one())
                .side(ExecutionSide.SELL)
                .fees(List.of(fee))
                .build(), RecordedTradeCostModel.INSTANCE);
        return new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
    }

}
