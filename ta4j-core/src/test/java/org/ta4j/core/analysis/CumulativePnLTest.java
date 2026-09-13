/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import java.util.ArrayList;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import java.util.List;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;

public class CumulativePnLTest extends AbstractIndicatorTest<org.ta4j.core.Indicator<Num>, Num> {

    public CumulativePnLTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void sizeWithoutTrades() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        var pnl = new CumulativePnL(series, new BaseTradingRecord());

        assertEquals(5, pnl.getSize());
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(4));
    }

    @Test
    public void getBarSeriesReturnsDefensiveSnapshots() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        CumulativePnL pnl = new CumulativePnL(series, new BaseTradingRecord());
        int originalSize = pnl.getSize();
        BarSeries firstReturnedSeries = pnl.getBarSeries();

        appendOneBar(series, 115);
        appendOneBar(firstReturnedSeries, 120);

        assertEquals(originalSize, pnl.getSize());
        assertEquals(originalSize, pnl.getBarSeries().getBarCount());
        assertNotSame(series, pnl.getBarSeries());
        assertNotSame(firstReturnedSeries, pnl.getBarSeries());
    }

    @Test
    public void longAndShortPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 95, 90).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series));

        var pnl = new CumulativePnL(series, record);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(5, pnl.getValue(1));
        assertNumEquals(5, pnl.getValue(2));
        assertNumEquals(10, pnl.getValue(3));
    }

    @Test
    public void openPositionUsesFinalPrice() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 102).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var pnl = new CumulativePnL(series, record);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(5, pnl.getValue(1));
        assertNumEquals(2, pnl.getValue(2));
    }

    @Test
    public void realizedModeUsesExitOnly() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));

        var pnl = new CumulativePnL(series, record, EquityCurveMode.REALIZED);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(5, pnl.getValue(2));
    }

    @Test
    public void realizedModeIgnoresOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 102).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var pnl = new CumulativePnL(series, record, EquityCurveMode.REALIZED);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(0, pnl.getValue(2));
    }

    @Test
    public void markToMarketCanIgnoreOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 102).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var pnl = new CumulativePnL(series, record, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(0, pnl.getValue(2));
    }

    @Test
    public void realizedModeIgnoresOpenPositionEvenWithMarkToMarketHandling() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 102).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var pnl = new CumulativePnL(series, record, EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(0, pnl.getValue(2));
    }

    @Test
    public void markToMarketRespectsFinalIndexForOpenPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var pnl = new CumulativePnL(series, record, 1, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(10, pnl.getValue(1));
    }

    @Test
    public void openShortPositionMarkToMarketAndRealized() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 90).build();
        var record = new BaseTradingRecord(Trade.sellAt(0, series));

        var markToMarket = new CumulativePnL(series, record);
        assertNumEquals(0, markToMarket.getValue(0));
        assertNumEquals(5, markToMarket.getValue(1));
        assertNumEquals(10, markToMarket.getValue(2));

        var realized = new CumulativePnL(series, record, EquityCurveMode.REALIZED);
        assertNumEquals(0, realized.getValue(0));
        assertNumEquals(0, realized.getValue(1));
        assertNumEquals(0, realized.getValue(2));
    }

    @Test
    public void positionConstructorUsesMarkToMarket() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series));

        var pnl = new CumulativePnL(series, position);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(10, pnl.getValue(1));
        assertNumEquals(5, pnl.getValue(2));
    }

    @Test
    public void positionConstructorUsesRealizedMode() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series));

        var pnl = new CumulativePnL(series, position, EquityCurveMode.REALIZED);
        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(5, pnl.getValue(2));
    }

    @Test
    public void cumulativePnL_markToMarket_doesNotUseFutureExitPriceWhenExitAfterFinalIndex() {
        var series = new MockBarSeriesBuilder().withData(10d, 11d, 12d, 13d, 100d).build();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        tradingRecord.exit(4, series.getBar(4).getClosePrice(), series.numFactory().one());

        var cumulativePnL = new CumulativePnL(series, tradingRecord, 2, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        var expected = series.getBar(2).getClosePrice().minus(series.getBar(0).getClosePrice());
        assertNumEquals(cumulativePnL.getValue(2), expected);
    }

    @Test
    public void cumulativePnL_ignore_skipsPositionsThatAreOpenAtFinalIndex() {
        var series = new MockBarSeriesBuilder().withData(10d, 11d, 12d, 13d, 100d).build();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        tradingRecord.exit(4, series.getBar(4).getClosePrice(), series.numFactory().one());

        var cumulativePnL = new CumulativePnL(series, tradingRecord, 2, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);

        assertNumEquals(cumulativePnL.getValue(2), series.numFactory().zero());
    }

    @Test
    public void cumulativePnLIncludesMultipleOpenLotsFromBaseTradingRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 12d, 14d).build();
        var record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.EPOCH, series.getBar(0).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(1, Instant.EPOCH, series.getBar(1).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, null, null));

        var pnl = new CumulativePnL(series, record, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(2, pnl.getValue(1));
        assertNumEquals(6, pnl.getValue(2));
    }

    @Test
    public void constructorWithFinalIndexDelegatesToMain() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));

        int typicalIndex = series.getEndIndex();
        int beyondEnd = series.getEndIndex() + 2;

        var expectedTypical = new CumulativePnL(series, record, typicalIndex, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        var actualTypical = new CumulativePnL(series, record, typicalIndex);
        assertSameValues(expectedTypical, actualTypical);

        var expectedBeyond = new CumulativePnL(series, record, beyondEnd, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        var actualBeyond = new CumulativePnL(series, record, beyondEnd);
        assertSameValues(expectedBeyond, actualBeyond);
    }

    @Test
    public void constructorWithFinalIndexAndModeDelegatesToMain() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));

        int typicalIndex = series.getEndIndex();
        int beyondEnd = series.getEndIndex() + 2;

        var expectedTypical = new CumulativePnL(series, record, typicalIndex, EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        var actualTypical = new CumulativePnL(series, record, typicalIndex, EquityCurveMode.REALIZED);
        assertSameValues(expectedTypical, actualTypical);

        var expectedBeyond = new CumulativePnL(series, record, beyondEnd, EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        var actualBeyond = new CumulativePnL(series, record, beyondEnd, EquityCurveMode.REALIZED);
        assertSameValues(expectedBeyond, actualBeyond);
    }

    @Test
    public void constructorWithOpenPositionHandlingDelegatesToMain() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var expected = new CumulativePnL(series, record, record.getEndIndex(series), EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);
        var actual = new CumulativePnL(series, record, OpenPositionHandling.IGNORE);

        assertSameValues(expected, actual);
    }

    private void assertSameValues(CumulativePnL expected, CumulativePnL actual) {
        assertEquals(expected.getSize(), actual.getSize());
        for (int i = 0; i < expected.getSize(); i++) {
            assertNumEquals(expected.getValue(i), actual.getValue(i));
        }
    }

    private static void appendOneBar(final BarSeries targetSeries, final Number closePrice) {
        Duration period = targetSeries.getLastBar().getTimePeriod();
        targetSeries.barBuilder()
                .timePeriod(period)
                .endTime(targetSeries.getLastBar().getEndTime().plus(period))
                .openPrice(closePrice)
                .highPrice(closePrice)
                .lowPrice(closePrice)
                .closePrice(closePrice)
                .volume(1)
                .add();
    }

    @Test
    public void cumulativePnLWindowedSeriesMatchesUnwindowedInsideWindow() {
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

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private static final double[] CLOSES = { 100d, 102d, 105d, 103d, 110d };

    private static final int BEGIN = 2;

    @Test
    public void futuresCumulativePnlWithNonpositiveEquityTracksActualLoss() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 100, 95, 96);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100_000, 100, List.of()));

            CumulativePnL pnl = new CumulativePnL(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            assertNumEquals(0.0, pnl.getValue(0));
            assertNumEquals(-5_000.0, pnl.getValue(1));
            assertNumEquals(-4_000.0, pnl.getValue(2));
        }
    }

    @Test
    public void futuresCumulativePnlRequiresExplicitAccountCapitalOnlyForAccountRecords() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));

            CumulativePnL pnl = new CumulativePnL(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            assertNumEquals(0.0, pnl.getValue(0));
            assertNumEquals(20.0, pnl.getValue(1));
            assertNumEquals(50.0, pnl.getValue(2));
            assertNumEquals(30.0, pnl.getValue(3));
            assertNumEquals(100.0, pnl.getValue(4));
        }
    }

    @Test
    public void singleFuturesPositionCumulativePnlUsesEntrySettlementNotional() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            Position position = FuturesAnalysisTestSupport.openPosition(contract, 0, 1_000, 100);
            CumulativePnL pnl = new CumulativePnL(barSeries, position);

            assertNumEquals(0.0, pnl.getValue(0));
            assertNumEquals(20.0, pnl.getValue(1));
            assertNumEquals(100.0, pnl.getValue(4));
        }
    }

    @Test
    public void futuresCumulativePnlRejectsMarkPriceFromAnotherSeries() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BarSeries otherSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 4, ExecutionSide.SELL, 1_000, 110, List.of()));

            assertThrows(IllegalArgumentException.class,
                    () -> new CumulativePnL(barSeries, record,
                            new org.ta4j.core.indicators.helpers.ClosePriceIndicator(otherSeries), 4,
                            EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
        }
    }

    @Test
    public void emptyFundedFuturesCumulativePnlIsFlat() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            CumulativePnL pnl = new CumulativePnL(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            for (int index = 0; index <= barSeries.getEndIndex(); index++) {
                assertNumEquals(0.0, pnl.getValue(index));
            }
        }
    }

    @Test
    public void futuresCurveDoesNotRecomputeSettledPositions() {
        FuturesContract contract = linearPerpetual(numFactory);
        CountingHoldingCostModel holdingCosts = new CountingHoldingCostModel();
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(1_000))
                .holdingCostModel(holdingCosts)
                .build();
        int periods = 10;
        for (int period = 0; period < periods; period++) {
            record.operate(fill(contract, 2 * period, ExecutionSide.BUY, 1, 100));
            record.operate(fill(contract, 2 * period + 1, ExecutionSide.SELL, 1, 101));
        }
        BarSeries curveSeries = flatRuntimeSeries(numFactory, 2 * periods + 1);
        holdingCosts.reset();

        CumulativePnL pnl = new CumulativePnL(curveSeries, record, curveSeries.getEndIndex(),
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        assertEquals(2 * periods + 1, pnl.getSize());
        // settled positions are folded once instead of being re-measured on every later
        // bar
        assertTrue("holding costs were recomputed " + holdingCosts.calls() + " times",
                holdingCosts.calls() <= 3 * periods);
    }

    private static BarSeries flatRuntimeSeries(NumFactory numFactory, int barCount) {
        List<Bar> bars = new ArrayList<>();
        Instant endTime = T0;
        for (int index = 0; index < barCount; index++) {
            Num price = numFactory.hundred();
            bars.add(new BaseBar(Duration.ofMinutes(1), endTime.minus(Duration.ofMinutes(1)), endTime, price, price,
                    price, price, numFactory.zero(), numFactory.zero(), 0));
            endTime = endTime.plus(Duration.ofMinutes(1));
        }
        return new BaseBarSeriesBuilder().withNumFactory(numFactory).withBars(bars).build();
    }

    private static final class CountingHoldingCostModel extends ZeroCostModel {

        private int calls;

        @Override
        public Num calculate(Position position) {
            calls++;
            return super.calculate(position);
        }

        @Override
        public Num calculate(Position position, int currentIndex) {
            calls++;
            return super.calculate(position, currentIndex);
        }

        private void reset() {
            calls = 0;
        }

        private int calls() {
            return calls;
        }
    }

}
