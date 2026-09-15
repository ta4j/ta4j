/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.risk;

import java.time.Instant;
import java.util.List;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedAmountStopLossRule;
import org.ta4j.core.rules.StopLossRule;

public class StopLossPositionRiskModelTest {

    @Test
    public void calculatesRiskForLongPosition() {
        var series = new MockBarSeriesBuilder().withData(100, 110).build();
        var numFactory = series.numFactory();
        Position position = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.two()),
                Trade.sellAt(1, numFactory.numOf(110), numFactory.two()));

        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertNumEquals(10, model.risk(series, position));
    }

    @Test
    public void calculatesRiskForShortPosition() {
        var series = new MockBarSeriesBuilder().withData(200, 190).build();
        var numFactory = series.numFactory();
        Position position = new Position(Trade.sellAt(0, numFactory.numOf(200), numFactory.three()),
                Trade.buyAt(1, numFactory.numOf(190), numFactory.three()));

        PositionRiskModel model = new StopLossPositionRiskModel(2.5);

        assertNumEquals(15, model.risk(series, position));
    }

    @Test
    public void calculatesRiskWithInjectedStopLossRule() {
        var series = new MockBarSeriesBuilder().withData(100, 95).build();
        var numFactory = series.numFactory();
        Position position = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.two()),
                Trade.sellAt(1, numFactory.numOf(95), numFactory.two()));

        var rule = new FixedAmountStopLossRule(new ClosePriceIndicator(series), numFactory.numOf(7));
        PositionRiskModel model = new StopLossPositionRiskModel(rule);

        assertNumEquals(14, model.risk(series, position));
    }

    @Test
    public void rejectsNonPositiveLossPercentage() {
        assertThrows(IllegalArgumentException.class, () -> new StopLossPositionRiskModel(0));
        assertThrows(IllegalArgumentException.class, () -> new StopLossPositionRiskModel(-1));
    }

    @Test
    public void returnsZeroRiskForMissingPositionContext() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 110).build();
        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertNumEquals(0, model.risk(series, null));
        assertNumEquals(0, model.risk(series, new Position()));
    }

    @Test
    public void rejectsNullSeriesWhenCalculatingRisk() {
        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertThrows(IllegalArgumentException.class, () -> model.risk(null, new Position()));
    }

    @Test
    public void returnsZeroRiskForNaNEntryPrice() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 110).build();
        Position position = new Position(Trade.buyAt(0, NaN.NaN, series.numFactory().one()),
                Trade.sellAt(1, series.numFactory().numOf(110), series.numFactory().one()));
        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertNumEquals(0, model.risk(series, position));
    }

    @Test
    public void returnsZeroRiskForZeroAmount() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 110).build();
        Position position = new Position(Trade.buyAt(0, series.numFactory().hundred(), series.numFactory().zero()),
                Trade.sellAt(1, series.numFactory().numOf(110), series.numFactory().zero()));
        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertNumEquals(0, model.risk(series, position));
    }

    @Test
    public void excludesDeferredFuturesEntryFillsFromStopLossBasis() {
        BarSeries series = new MockBarSeriesBuilder().withData(100).build();
        NumFactory numFactory = series.numFactory();
        FuturesContract contract = FuturesContract.builder()
                .venue("TEST")
                .symbol("TEST-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.one())
                .quantityIncrement(numFactory.one())
                .minimumQuantity(numFactory.one())
                .build();
        TradeFill executedFill = TradeFill.builder()
                .index(0)
                .time(Instant.parse("2025-01-01T00:00:00Z"))
                .price(numFactory.hundred())
                .amount(numFactory.one())
                .side(ExecutionSide.BUY)
                .futuresContract(contract)
                .fees(List.of())
                .build();
        TradeFill deferredFill = TradeFill.builder()
                .index(-1)
                .time(Instant.parse("2025-01-01T00:00:01Z"))
                .price(numFactory.numOf(200))
                .amount(numFactory.one())
                .side(ExecutionSide.BUY)
                .futuresContract(contract)
                .fees(List.of())
                .build();
        Trade entry = Trade.fromFills(Trade.TradeType.BUY, List.of(executedFill, deferredFill));
        Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

        PositionRiskModel model = new StopLossPositionRiskModel(5);

        assertNumEquals(5, model.risk(series, position));
    }

    @Test
    public void subtractsExecutedFuturesExitFromStopLossRisk() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 90).build();
        NumFactory numFactory = series.numFactory();
        FuturesContract contract = FuturesContract.builder()
                .venue("TEST")
                .symbol("TEST-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.one())
                .quantityIncrement(numFactory.one())
                .minimumQuantity(numFactory.one())
                .build();
        Trade entry = Trade.fromFill(TradeFill.builder()
                .index(0)
                .time(Instant.parse("2025-01-01T00:00:00Z"))
                .price(numFactory.hundred())
                .amount(numFactory.two())
                .side(ExecutionSide.BUY)
                .futuresContract(contract)
                .fees(List.of(TradeFee.builder()
                        .type(TradeFee.Type.COMMISSION)
                        .amount(numFactory.two())
                        .currency("USD")
                        .build()))
                .build());
        Trade exit = Trade.fromFill(TradeFill.builder()
                .index(1)
                .time(Instant.parse("2025-01-01T00:00:01Z"))
                .price(numFactory.numOf(110))
                .amount(numFactory.one())
                .side(ExecutionSide.SELL)
                .futuresContract(contract)
                .fees(List.of())
                .build());
        Position position = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

        PositionRiskModel model = new StopLossPositionRiskModel(
                (ignoredSeries, ignoredPosition) -> numFactory.numOf(90));

        // The entry fee is allocated as one quote unit per contract, so the one
        // remaining contract has a net entry basis of 101 and risks 11.
        assertNumEquals(11, model.risk(series, position));
    }

    @Test
    public void returnsZeroRiskForDeferredScalarFuturesEntry() {
        BarSeries series = new MockBarSeriesBuilder().withData(100).build();
        NumFactory numFactory = series.numFactory();
        FuturesContract contract = FuturesContract.builder()
                .venue("TEST")
                .symbol("TEST-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.one())
                .quantityIncrement(numFactory.one())
                .minimumQuantity(numFactory.one())
                .build();
        ZeroCostModel costModel = new ZeroCostModel();
        Trade deferredEntry = new org.ta4j.core.BaseTrade(-1, Trade.TradeType.BUY, numFactory.hundred(),
                numFactory.one(), costModel) {
            @Override
            public FuturesContract getFuturesContract() {
                return contract;
            }

            @Override
            public List<TradeFill> getFills() {
                return List.of();
            }
        };
        Position position = new Position(deferredEntry, costModel, new ZeroCostModel());

        assertNumEquals(0, new StopLossPositionRiskModel(5).risk(series, position));
    }

    @Test
    public void preservesExecutedScalarFuturesEntryCompatibility() {
        BarSeries series = new MockBarSeriesBuilder().withData(100).build();
        NumFactory numFactory = series.numFactory();
        FuturesContract contract = FuturesContract.builder()
                .venue("TEST")
                .symbol("TEST-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.one())
                .quantityIncrement(numFactory.one())
                .minimumQuantity(numFactory.one())
                .build();
        ZeroCostModel costModel = new ZeroCostModel();
        Trade executedEntry = new org.ta4j.core.BaseTrade(0, Trade.TradeType.BUY, numFactory.hundred(),
                numFactory.one(), costModel) {
            @Override
            public FuturesContract getFuturesContract() {
                return contract;
            }

            @Override
            public List<TradeFill> getFills() {
                return List.of();
            }
        };
        Position position = new Position(executedEntry, costModel, new ZeroCostModel());

        assertNumEquals(5, new StopLossPositionRiskModel(5).risk(series, position));
    }

    @Test
    public void derivesInjectedStopLossRuleFromExecutedFuturesEntryFills() {
        BarSeries series = new MockBarSeriesBuilder().withData(100).build();
        NumFactory numFactory = series.numFactory();
        FuturesContract contract = FuturesContract.builder()
                .venue("TEST")
                .symbol("TEST-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.one())
                .quantityIncrement(numFactory.one())
                .minimumQuantity(numFactory.one())
                .build();
        TradeFill executedFill = TradeFill.builder()
                .index(0)
                .time(Instant.parse("2025-01-01T00:00:00Z"))
                .price(numFactory.hundred())
                .amount(numFactory.one())
                .side(ExecutionSide.BUY)
                .futuresContract(contract)
                .fees(List.of())
                .build();
        TradeFill deferredFill = TradeFill.builder()
                .index(-1)
                .time(Instant.parse("2025-01-01T00:00:01Z"))
                .price(numFactory.numOf(200))
                .amount(numFactory.one())
                .side(ExecutionSide.BUY)
                .futuresContract(contract)
                .fees(List.of())
                .build();
        Trade entry = Trade.fromFills(Trade.TradeType.BUY, List.of(executedFill, deferredFill));
        Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

        StopLossRule stopLossRule = new StopLossRule(new ClosePriceIndicator(series), 10);
        PositionRiskModel model = new StopLossPositionRiskModel(stopLossRule);

        assertNumEquals(10, model.risk(series, position));
    }
}
