/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesCashFlow;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies the native futures behaviour of the analysis curves
 * ({@link CashFlow}, {@link CumulativePnL} and {@link Returns}): account
 * normalization by explicit capital, unlevered single position normalization by
 * entry settlement notional, realized versus mark-to-market exposure and the
 * undefined return conventions for nonpositive equity.
 */
class FuturesAnalysisTest {

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

    private static BarSeries series(NumFactory numFactory, double... closes) {
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
    }

    private static BarSeries markToMarketSeries(NumFactory numFactory) {
        return series(numFactory, 100, 102, 105, 103, 110);
    }

    private static TradeFee commission(NumFactory numFactory, double amount) {
        return TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(numFactory.numOf(amount))
                .currency("USD")
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

    private static Position openPosition(FuturesContract contract, int index, double amount, double price) {
        Trade entry = Trade.fromFill(fill(contract, index, ExecutionSide.BUY, amount, price, List.of()),
                RecordedTradeCostModel.INSTANCE);
        return new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
    }

    private static FuturesCashFlow variationMargin(FuturesContract contract, int index, double amount) {
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

    private static BaseTradingRecord fundedRecord(FuturesContract contract, NumFactory numFactory, double capital) {
        return BaseTradingRecord.builder().futuresContract(contract).initialCapital(numFactory.numOf(capital)).build();
    }

    @Test
    void markToMarketEquityIsNormalizedByAccountCapital() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = markToMarketSeries(numFactory);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 500);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
            record.operate(fill(contract, 4, ExecutionSide.SELL, 1_000, 110, List.of()));

            CashFlow cashFlow = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertEquals(EquityCurveMode.MARK_TO_MARKET, cashFlow.getEquityCurveMode());
            assertEquals(5, cashFlow.getSize());
            assertNumEquals(1.0, cashFlow.getValue(0));
            assertNumEquals(1.04, cashFlow.getValue(1));
            assertNumEquals(1.1, cashFlow.getValue(2));
            assertNumEquals(1.06, cashFlow.getValue(3));
            assertNumEquals(1.2, cashFlow.getValue(4));

            CashFlow window = new CashFlow(barSeries, record, 2, 3, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertNumEquals(1.1, window.getValue(2));
            assertNumEquals(1.06, window.getValue(3));
        }
    }

    @Test
    void realizedModeAndIgnoredOpenPositionsKeepPaidCashOnly() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = markToMarketSeries(numFactory);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 500);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of(commission(numFactory, 2))));
            record.recordCashFlow(variationMargin(contract, 3, 20));
            record.operate(fill(contract, 4, ExecutionSide.SELL, 1_000, 110, List.of(commission(numFactory, 3))));

            CashFlow realized = new CashFlow(barSeries, record, EquityCurveMode.REALIZED,
                    OpenPositionHandling.MARK_TO_MARKET);
            CashFlow ignored = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.IGNORE);
            CashFlow marked = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            for (int index = 0; index < 3; index++) {
                assertNumEquals(0.996, realized.getValue(index));
                assertNumEquals(0.996, ignored.getValue(index));
            }
            assertNumEquals(1.036, realized.getValue(3));
            assertNumEquals(1.19, realized.getValue(4));
            assertNumEquals(1.19, ignored.getValue(4));

            assertNumEquals(0.996, marked.getValue(0));
            assertNumEquals(1.036, marked.getValue(1));
            assertNumEquals(1.096, marked.getValue(2));
            assertNumEquals(1.056, marked.getValue(3));
            assertNumEquals(1.19, marked.getValue(4));
        }
    }

    @Test
    void futuresReturnsUseConsecutiveEquityRatios() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = markToMarketSeries(numFactory);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 500);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
            record.operate(fill(contract, 4, ExecutionSide.SELL, 1_000, 110, List.of()));

            Returns decimal = new Returns(barSeries, record, ReturnRepresentation.DECIMAL);
            Returns percentage = new Returns(barSeries, record, ReturnRepresentation.PERCENTAGE);
            Returns logarithmic = new Returns(barSeries, record, ReturnRepresentation.LOG);

            assertTrue(decimal.getValue(0).isNaN());
            assertNumEquals(0.04, decimal.getValue(1));
            assertNumEquals(0.05769230769230769, decimal.getValue(2));
            assertNumEquals(-0.03636363636363636, decimal.getValue(3));
            assertNumEquals(0.1320754716981132, decimal.getValue(4));

            assertNumEquals(4.0, percentage.getValue(1));
            assertNumEquals(-3.6363636363636362, percentage.getValue(3));

            assertNumEquals(0.03922071315328133, logarithmic.getValue(1));
            assertNumEquals(0.05608946665104358, logarithmic.getValue(2));
            assertNumEquals(-0.0370412716803491, logarithmic.getValue(3));
            assertNumEquals(0.12405264866997882, logarithmic.getValue(4));

            Num growth = numFactory.one();
            for (int index = 1; index <= 4; index++) {
                growth = growth.multipliedBy(numFactory.one().plus(decimal.getValue(index)));
            }
            assertNumEquals(1.2, growth);
        }
    }

    @Test
    void nonpositiveEquityYieldsUndefinedOrActualLoss() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = series(numFactory, 100, 95, 96);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 500);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 100_000, 100, List.of()));

            CashFlow cashFlow = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            CashFlow realized = new CashFlow(barSeries, record, EquityCurveMode.REALIZED,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertNumEquals(1.0, cashFlow.getValue(0));
            assertNumEquals(-9.0, cashFlow.getValue(1));
            assertNumEquals(-7.0, cashFlow.getValue(2));
            assertNumEquals(1.0, realized.getValue(0));
            assertNumEquals(1.0, realized.getValue(1));
            assertNumEquals(1.0, realized.getValue(2));

            Returns decimal = new Returns(barSeries, record, ReturnRepresentation.DECIMAL);
            Returns logarithmic = new Returns(barSeries, record, ReturnRepresentation.LOG);

            assertTrue(decimal.getValue(0).isNaN());
            assertNumEquals(-10.0, decimal.getValue(1));
            assertTrue(decimal.getValue(2).isNaN());
            assertTrue(logarithmic.getValue(1).isNaN());
            assertTrue(logarithmic.getValue(2).isNaN());

            CumulativePnL pnl = new CumulativePnL(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertNumEquals(0.0, pnl.getValue(0));
            assertNumEquals(-5_000.0, pnl.getValue(1));
            assertNumEquals(-4_000.0, pnl.getValue(2));
        }
    }

    @Test
    void futuresAccountAnalysisRequiresExplicitCapital() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = markToMarketSeries(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();
            record.operate(fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));

            assertThrows(IllegalStateException.class, () -> new CashFlow(barSeries, record,
                    EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
            assertThrows(IllegalStateException.class,
                    () -> new Returns(barSeries, record, ReturnRepresentation.DECIMAL));
            assertThrows(IllegalStateException.class,
                    () -> new CashFlow(barSeries, BaseTradingRecord.builder().futuresContract(contract).build(),
                            EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));

            CumulativePnL pnl = new CumulativePnL(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertNumEquals(0.0, pnl.getValue(0));
            assertNumEquals(20.0, pnl.getValue(1));
            assertNumEquals(50.0, pnl.getValue(2));
            assertNumEquals(30.0, pnl.getValue(3));
            assertNumEquals(100.0, pnl.getValue(4));

            BaseTradingRecord spot = BaseTradingRecord.builder().build();
            spot.operate(0, numFactory.numOf(100), numFactory.one());
            spot.operate(2, numFactory.numOf(110), numFactory.one());

            assertDoesNotThrow(() -> new CashFlow(barSeries, spot, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET));
            assertDoesNotThrow(() -> new Returns(barSeries, spot, ReturnRepresentation.DECIMAL));
        }
    }

    @Test
    void singleFuturesPositionIsNormalizedByEntrySettlementNotional() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = markToMarketSeries(numFactory);
            Position position = openPosition(contract, 0, 1_000, 100);

            assertNumEquals(1_000.0, contract.settlementNotional(numFactory.numOf(1_000), numFactory.numOf(100)));

            CashFlow cashFlow = new CashFlow(barSeries, position, EquityCurveMode.MARK_TO_MARKET);

            assertNumEquals(1.0, cashFlow.getValue(0));
            assertNumEquals(1.02, cashFlow.getValue(1));
            assertNumEquals(1.05, cashFlow.getValue(2));
            assertNumEquals(1.03, cashFlow.getValue(3));
            assertNumEquals(1.1, cashFlow.getValue(4));

            Returns returns = new Returns(barSeries, position, ReturnRepresentation.DECIMAL);

            assertTrue(returns.getValue(0).isNaN());
            assertNumEquals(0.02, returns.getValue(1));
            assertNumEquals(0.02941176470588236, returns.getValue(2));
            assertNumEquals(-0.0190476190476191, returns.getValue(3));
            assertNumEquals(0.06796116504854367, returns.getValue(4));

            CumulativePnL pnl = new CumulativePnL(barSeries, position);

            assertNumEquals(0.0, pnl.getValue(0));
            assertNumEquals(20.0, pnl.getValue(1));
            assertNumEquals(100.0, pnl.getValue(4));
        }
    }

    @Test
    void partiallyClosedFuturesPositionsNormalizeByTheirOwnEntryNotional() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = series(numFactory, 10_000, 11_000);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 1_000_000);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4))));
            record.operate(fill(contract, 1, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1))));

            Position closedSlice = record.getPositions().getFirst();
            Position openRemainder = record.getOpenPositions().getFirst();

            assertNumEquals(100.0, contract.settlementNotional(closedSlice.getEntry().getAmount(),
                    closedSlice.getEntry().getPricePerAsset()));
            assertNumEquals(300.0, contract.settlementNotional(openRemainder.getEntry().getAmount(),
                    openRemainder.getEntry().getPricePerAsset()));

            CashFlow closedCashFlow = new CashFlow(barSeries, closedSlice, EquityCurveMode.MARK_TO_MARKET);

            assertNumEquals(0.99, closedCashFlow.getValue(0));
            assertNumEquals(1.08, closedCashFlow.getValue(1));

            CashFlow openCashFlow = new CashFlow(barSeries, openRemainder, EquityCurveMode.MARK_TO_MARKET);

            assertNumEquals(0.99, openCashFlow.getValue(0));
            assertNumEquals(1.09, openCashFlow.getValue(1));

            Returns closedReturns = new Returns(barSeries, closedSlice, ReturnRepresentation.DECIMAL);

            assertTrue(closedReturns.getValue(0).isNaN());
            // The bar-0 entry fee is inside the capitalization base, so the single
            // reported return is the unlevered +8 (gross 10 − fees 2) on 100 notional.
            assertNumEquals(0.08, closedReturns.getValue(1));

            TradingRecord publicRecord = new BaseTradingRecord(List.of(closedSlice));
            assertThrows(IllegalStateException.class, () -> new CashFlow(barSeries, publicRecord,
                    EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
        }
    }

    @Test
    void markPriceIndicatorMustBelongToTheAnalysedSeries() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = markToMarketSeries(numFactory);
            BarSeries otherSeries = markToMarketSeries(numFactory);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 500);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
            record.operate(fill(contract, 4, ExecutionSide.SELL, 1_000, 110, List.of()));

            assertThrows(IllegalArgumentException.class,
                    () -> new CashFlow(barSeries, record, new ClosePriceIndicator(otherSeries), 4,
                            EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
            assertThrows(IllegalArgumentException.class,
                    () -> new CumulativePnL(barSeries, record, new ClosePriceIndicator(otherSeries), 4,
                            EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
            assertThrows(IllegalArgumentException.class,
                    () -> new Returns(barSeries, record, new ClosePriceIndicator(otherSeries), 4,
                            ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET,
                            OpenPositionHandling.MARK_TO_MARKET));

            CashFlow constantMark = new CashFlow(barSeries, record,
                    new ConstantIndicator<>(barSeries, numFactory.numOf(108)), 4, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertNumEquals(1.16, constantMark.getValue(0));
            assertNumEquals(1.16, constantMark.getValue(3));
            assertNumEquals(1.2, constantMark.getValue(4));
        }
    }

    @Test
    void emptyFundedFuturesRecordHasFlatCurves() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = markToMarketSeries(numFactory);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 500);

            CashFlow cashFlow = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            Returns returns = new Returns(barSeries, record, ReturnRepresentation.DECIMAL);
            CumulativePnL pnl = new CumulativePnL(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            for (int index = 0; index <= barSeries.getEndIndex(); index++) {
                assertNumEquals(1.0, cashFlow.getValue(index));
                assertNumEquals(0.0, pnl.getValue(index));
            }
            assertTrue(returns.getValue(0).isNaN());
            for (int index = 1; index <= barSeries.getEndIndex(); index++) {
                assertNumEquals(0.0, returns.getValue(index));
            }
        }
    }

    @Test
    void windowProjectionKeepsOnlyThePositionsSelectedByThePolicy() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = series(numFactory, 100, 100, 110, 110, 115, 120);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 1_000);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of(commission(numFactory, 1))));
            record.operate(fill(contract, 2, ExecutionSide.SELL, 100, 110, List.of(commission(numFactory, 1))));
            record.operate(fill(contract, 3, ExecutionSide.BUY, 100, 110, List.of(commission(numFactory, 1))));
            record.operate(fill(contract, 5, ExecutionSide.SELL, 100, 120, List.of(commission(numFactory, 1))));

            NetReturnCriterion criterion = new NetReturnCriterion();
            AnalysisContext exitInWindow = AnalysisContext.defaults();

            // Every slice is unrerecorded: the full record realizes 8 + 8 on 1_000.
            assertNumEquals(1.016, criterion.calculate(barSeries, record));
            // Only the slice that exits inside the window is projected, so the
            // early slice's execution basis and self-contained P&L are never split
            // across the boundary.
            assertNumEquals(1.008, criterion.calculate(barSeries, record, AnalysisWindow.barRange(3, 5), exitInWindow));
            assertNumEquals(1.008, criterion.calculate(barSeries, record, AnalysisWindow.barRange(0, 2), exitInWindow));
            assertNumEquals(1.008,
                    criterion.calculate(barSeries, record, AnalysisWindow.barRange(3, 5), AnalysisContext.defaults()
                            .withPositionInclusionPolicy(AnalysisContext.PositionInclusionPolicy.FULLY_CONTAINED)));
            // A window without a matching exit keeps the funded record flat.
            assertNumEquals(1.0, criterion.calculate(barSeries, record, AnalysisWindow.barRange(3, 4), exitInWindow));
        }
    }

    @Test
    void windowProjectionClosesOpenFuturesPositionsAtTheMark() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries barSeries = series(numFactory, 100, 105, 110, 110, 115, 120);
            BaseTradingRecord record = fundedRecord(contract, numFactory, 1_000);
            record.operate(fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of(commission(numFactory, 1))));
            record.recordCashFlow(variationMargin(contract, 1, 3));

            NetReturnCriterion criterion = new NetReturnCriterion();
            AnalysisWindow window = AnalysisWindow.barRange(0, 2);
            AnalysisContext marked = AnalysisContext.defaults()
                    .withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET);

            // The synthetic exit converts the settled variation margin into the
            // marked payoff of 10 minus the executed entry fee.
            assertNumEquals(1.009, criterion.calculate(barSeries, record, window, marked));
            // The same economics as the source record's mark-to-market equity of
            // realized (2) plus remaining unrealized (7) profit, where the settled
            // margin cancels out. The realized curve keeps only the paid cash.
            CashFlow equity = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            CashFlow realized = new CashFlow(barSeries, record, EquityCurveMode.REALIZED,
                    OpenPositionHandling.MARK_TO_MARKET);
            assertNumEquals(1.009, equity.getValue(2));
            assertNumEquals(1.002, realized.getValue(2));
            // Ignored open exposure leaves only the paid cash behind.
            assertNumEquals(1.0, criterion.calculate(barSeries, record, window, AnalysisContext.defaults()));
        }
    }
}
