/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies that the analysis curves ({@link CashFlow}, {@link CumulativePnL}
 * and {@link Returns}) produce identical values inside the window of a windowed
 * bar series (logical begin index greater than zero) as the same bars indexed
 * from zero, and expose the documented neutral values outside the window.
 */
class WindowedSeriesAnalysisTest {

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");
    private static final double[] CLOSES = { 100, 102, 105, 103, 110 };
    private static final int BEGIN = 2;

    private static List<NumFactory> factories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    private static FuturesContract linearPerpetual(NumFactory numFactory) {
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

    private static BarSeries series(NumFactory numFactory, int beginIndex) {
        List<Bar> bars = new ArrayList<>();
        Instant endTime = T0;
        for (double close : CLOSES) {
            Num price = numFactory.numOf(close);
            bars.add(new BaseBar(Duration.ofMinutes(1), endTime.minus(Duration.ofMinutes(1)), endTime, price, price,
                    price, price, numFactory.zero(), numFactory.zero(), 0));
            endTime = endTime.plus(Duration.ofMinutes(1));
        }
        return new BaseBarSeriesBuilder().withNumFactory(numFactory).withBeginIndex(beginIndex).withBars(bars).build();
    }

    private static TradeFill fill(FuturesContract contract, int index, ExecutionSide side, double amount,
            double price) {
        NumFactory numFactory = contract.contractSize().getNumFactory();
        return TradeFill.builder()
                .index(index)
                .time(T0.plusSeconds(index))
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .orderId("order-" + index)
                .futuresContract(contract)
                .fees(List.of())
                .build();
    }

    private static BaseTradingRecord futuresRecord(FuturesContract contract, int indexOffset) {
        NumFactory numFactory = contract.contractSize().getNumFactory();
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(500))
                .build();
        record.operate(fill(contract, indexOffset, ExecutionSide.BUY, 1_000, 100));
        record.operate(fill(contract, indexOffset + 4, ExecutionSide.SELL, 1_000, 110));
        return record;
    }

    private static Position spotPosition(NumFactory numFactory, int indexOffset) {
        Num one = numFactory.numOf(1);
        Trade entry = Trade.buyAt(1 + indexOffset, numFactory.numOf(CLOSES[1]), one, RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.sellAt(4 + indexOffset, numFactory.numOf(CLOSES[4]), one, RecordedTradeCostModel.INSTANCE);
        return new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
    }

    @Test
    void cashFlowWindowedSeriesMatchesUnwindowedInsideWindow() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearPerpetual(numFactory);
            BarSeries full = series(numFactory, 0);
            BarSeries windowed = series(numFactory, BEGIN);
            BaseTradingRecord fullRecord = futuresRecord(contract, 0);
            BaseTradingRecord windowedRecord = futuresRecord(contract, BEGIN);

            CashFlow fullCashFlow = new CashFlow(full, fullRecord, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            CashFlow windowedCashFlow = new CashFlow(windowed, windowedRecord, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertEquals(5, fullCashFlow.getSize());
            assertEquals(5, windowedCashFlow.getSize());
            assertNumEquals(numFactory.one(), windowedCashFlow.getValue(0));
            assertNumEquals(numFactory.one(), windowedCashFlow.getValue(BEGIN - 1));
            for (int index = 0; index < CLOSES.length; index++) {
                assertNumEquals(fullCashFlow.getValue(index), windowedCashFlow.getValue(BEGIN + index));
            }
        }
    }

    @Test
    void cumulativePnLWindowedSeriesMatchesUnwindowedInsideWindow() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearPerpetual(numFactory);
            BarSeries full = series(numFactory, 0);
            BarSeries windowed = series(numFactory, BEGIN);
            BaseTradingRecord fullRecord = futuresRecord(contract, 0);
            BaseTradingRecord windowedRecord = futuresRecord(contract, BEGIN);

            CumulativePnL fullPnL = new CumulativePnL(full, fullRecord, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            CumulativePnL windowedPnL = new CumulativePnL(windowed, windowedRecord, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertEquals(5, fullPnL.getSize());
            assertEquals(5, windowedPnL.getSize());
            assertNumEquals(numFactory.zero(), windowedPnL.getValue(0));
            assertNumEquals(numFactory.zero(), windowedPnL.getValue(BEGIN - 1));
            for (int index = 0; index < CLOSES.length; index++) {
                assertNumEquals(fullPnL.getValue(index), windowedPnL.getValue(BEGIN + index));
            }
        }
    }

    @Test
    void futuresReturnsWindowedSeriesMatchesUnwindowedInsideWindow() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearPerpetual(numFactory);
            BarSeries full = series(numFactory, 0);
            BarSeries windowed = series(numFactory, BEGIN);
            BaseTradingRecord fullRecord = futuresRecord(contract, 0);
            BaseTradingRecord windowedRecord = futuresRecord(contract, BEGIN);

            Returns fullReturns = new Returns(full, fullRecord, ReturnRepresentation.DECIMAL);
            Returns windowedReturns = new Returns(windowed, windowedRecord, ReturnRepresentation.DECIMAL);

            assertEquals(full.getEndIndex() + 1, fullReturns.getValues().size());
            assertEquals(windowed.getEndIndex() + 1, windowedReturns.getValues().size());
            for (int index = 0; index <= windowed.getEndIndex(); index++) {
                assertEquals(windowedReturns.getValue(index), windowedReturns.getValues().get(index),
                        "getValue must agree with getValues at " + index);
            }
            assertTrue(windowedReturns.getRawValues().get(0).isNaN(), "returns before the first bar are undefined");
            assertNumEquals(numFactory.zero(), windowedReturns.getRawValues().get(BEGIN - 1));
            for (int index = 1; index < CLOSES.length; index++) {
                assertNumEquals(fullReturns.getRawValues().get(index),
                        windowedReturns.getRawValues().get(BEGIN + index));
            }
        }
    }

    @Test
    void spotReturnsWindowedSeriesMatchesUnwindowedInsideWindow() {
        for (NumFactory numFactory : factories()) {
            BarSeries full = series(numFactory, 0);
            BarSeries windowed = series(numFactory, BEGIN);
            Position fullPosition = spotPosition(numFactory, 0);
            Position windowedPosition = spotPosition(numFactory, BEGIN);

            Returns fullReturns = new Returns(full, fullPosition, ReturnRepresentation.DECIMAL);
            Returns windowedReturns = new Returns(windowed, windowedPosition, ReturnRepresentation.DECIMAL);

            assertEquals(windowed.getEndIndex() + 1, windowedReturns.getValues().size());
            assertTrue(fullReturns.getRawValues().get(0).isNaN(),
                    "first bar of a series from index 0 is a placeholder");
            assertTrue(windowedReturns.getRawValues().get(0).isNaN(), "returns before the first bar are undefined");
            assertNumEquals(numFactory.zero(), windowedReturns.getRawValues().get(BEGIN - 1));
            for (int index = 1; index < CLOSES.length; index++) {
                assertNumEquals(fullReturns.getRawValues().get(index),
                        windowedReturns.getRawValues().get(BEGIN + index));
            }
        }
    }
}