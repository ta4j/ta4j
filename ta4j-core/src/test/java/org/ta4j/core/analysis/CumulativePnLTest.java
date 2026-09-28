/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.ta4j.core.TestUtils.assertNumEquals;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConcurrentBarSeries;
import org.junit.Test;
import static org.junit.Assert.assertThrows;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CumulativePnLTest extends AbstractIndicatorTest<org.ta4j.core.Indicator<Num>, Num> {

    public CumulativePnLTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void capturesRollingWindowUnderOneReadLease() {
        AtomicBoolean appendBeforeLock = new AtomicBoolean();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.rollingSeriesWithAppendBeforeReadLock(numFactory,
                appendBeforeLock, 1.5d, 2.5d, 3.5d);
        BaseTradingRecord record = new BaseTradingRecord(Trade.buyAt(0, series));
        appendBeforeLock.set(true);

        CumulativePnL pnl = new CumulativePnL(series, record, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        // The append inside the lease evicts the entry bar: the window is [1, 2]
        // and the entry is valued at the window's first close (2.5), so P&L is 0
        // at index 1 and 3.5 - 2.5 at index 2.
        assertEquals(1, pnl.getBeginIndex());
        assertNumEquals(numFactory.zero(), pnl.getValue(1));
        assertNumEquals(numFactory.numOf(3.5d).minus(numFactory.numOf(2.5d)), pnl.getValue(2));
        List<Num> materialized = pnl.stream().toList();
        series.barBuilder().closePrice(4.5d).add();
        // A rebased or recomputed curve over [2, 3] would read 0 and 4.5 - 3.5;
        // the materialized one keeps its values.
        assertEquals(materialized, pnl.stream().toList());
        assertEquals(List.of(numFactory.zero(), numFactory.numOf(3.5d).minus(numFactory.numOf(2.5d))),
                pnl.stream().toList());
    }

    @Test
    public void sizeRemainsBoundToMaterializedValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        CumulativePnL pnl = new CumulativePnL(series, new BaseTradingRecord());

        series.barBuilder().closePrice(4d).add();
        assertEquals(3, pnl.getSize());
        series.setMaximumBarCount(1);
        assertEquals(3, pnl.getSize());
        assertEquals(3L, pnl.stream().count());
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
    public void getBarSeriesReturnsBorrowedInstance() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        CumulativePnL pnl = new CumulativePnL(series, new BaseTradingRecord());

        assertSame(series, pnl.getBarSeries());
    }

    @Test
    public void valuesPreWindowEntryAtFirstRetainedClose() {
        // A rolling window capped at two bars evicts the entry bar (close 30):
        // the window is credited only with the move from its first close (40),
        // so its first level is zero and the exit adds 50 - 40, not 50 - 30.
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(2);
        rolling.barBuilder().closePrice(30d).add();
        Trade entry = Trade.buyAt(0, rolling);
        rolling.barBuilder().closePrice(40d).add();
        rolling.barBuilder().closePrice(50d).add();
        var record = new BaseTradingRecord(entry, Trade.sellAt(2, rolling));

        CumulativePnL pnl = new CumulativePnL(rolling, record, EquityCurveMode.MARK_TO_MARKET);

        assertEquals(1, rolling.getBeginIndex());
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(10, pnl.getValue(2));
    }

    @Test
    public void retainedHeadNetsPreWindowHoldingPeriodsOutOfTheValuationBasis() {
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(2);
        rolling.barBuilder().closePrice(100d).add();
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(),
                new FixedTransactionCostModel(4d));
        record.enter(0, rolling.getBar(0).getClosePrice(), numFactory.one());
        rolling.barBuilder().closePrice(110d).add();
        rolling.barBuilder().closePrice(120d).add();
        rolling.barBuilder().closePrice(130d).add();
        assertEquals(2, rolling.getBeginIndex());

        CumulativePnL pnl = new CumulativePnL(rolling, record, EquityCurveMode.MARK_TO_MARKET);

        // 4 of holding cost over three held bars accrues 4/3 per bar. The window
        // [2, 3] values the position at 120 net of the two periods accrued by
        // index 2 (120 - 8/3), so its first level is zero; the mark at 3 is
        // 130 - 4, adding the 10 price move less one period of carry (4/3). A
        // basis that ignored pre-window carry would charge all 4 at index 3.
        Num averageCost = numFactory.numOf(4d).dividedBy(numFactory.numOf(3));
        assertNumEquals(numFactory.zero(), pnl.getValue(2), 1e-12);
        Num expectedNext = numFactory.numOf(10d).minus(averageCost);
        assertNumEquals(expectedNext, pnl.getValue(3), 1e-12);
    }

    @Test
    public void markToMarketHoldingCostAccruesPerPeriodAndEndsAtRealizedValue() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 100d, 100d, 100d)
                .build();
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(),
                new FixedTransactionCostModel(1.5d));
        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        record.exit(3, series.getBar(3).getClosePrice(), numFactory.one());

        CumulativePnL markToMarket = new CumulativePnL(series, record, EquityCurveMode.MARK_TO_MARKET);
        CumulativePnL realized = new CumulativePnL(series, record, EquityCurveMode.REALIZED);

        // A 1.5 fee per trade makes 3.0 of holding cost over three bars; it
        // accrues one unit per held bar instead of one average period per mark.
        assertNumEquals(-1, markToMarket.getValue(1));
        assertNumEquals(-2, markToMarket.getValue(2));
        assertNumEquals(-3, markToMarket.getValue(3));
        assertNumEquals(realized.getValue(3), markToMarket.getValue(3));
    }

    @Test
    public void flatPriceHoldingCostRemainsCumulativeInsideRetainedWindow() {
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(3);
        rolling.barBuilder().closePrice(100d).add();
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(),
                new FixedTransactionCostModel(4d));
        record.enter(0, rolling.getBar(0).getClosePrice(), numFactory.one());
        rolling.barBuilder().closePrice(100d).add();
        rolling.barBuilder().closePrice(100d).add();
        rolling.barBuilder().closePrice(100d).add();
        rolling.barBuilder().closePrice(100d).add();

        CumulativePnL pnl = new CumulativePnL(rolling, record, EquityCurveMode.MARK_TO_MARKET);

        // 4 of holding cost over four held bars is 1 per bar. The window [2, 4]
        // starts at 100 - 2; the marks at 3 and 4 are 97 and 96, so P&L keeps
        // accumulating carry (-1, then -2) instead of resetting per mark.
        assertEquals(2, rolling.getBeginIndex());
        assertNumEquals(numFactory.zero(), pnl.getValue(2), 1e-12);
        assertNumEquals(numFactory.numOf(-1d), pnl.getValue(3), 1e-12);
        assertNumEquals(numFactory.numOf(-2d), pnl.getValue(4), 1e-12);
    }

    @Test
    public void exitAtFirstRetainedIndexIsNotDoubleCounted() {
        // endIndex == seriesBegin: the pre-window entry is valued at that bar's
        // close (40) and the exit fills at 45, so the level is 45 - 40, added
        // once at the exit and carried, not once as a mark and again as the
        // exit delta.
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(2);
        rolling.barBuilder().closePrice(30d).add();
        Trade entry = Trade.buyAt(0, rolling);
        rolling.barBuilder().closePrice(40d).add();
        Trade exitTrade = Trade.sellAt(1, numFactory.numOf(45d), numFactory.one());
        rolling.barBuilder().closePrice(50d).add();
        var record = new BaseTradingRecord(entry, exitTrade);

        CumulativePnL pnl = new CumulativePnL(rolling, record, EquityCurveMode.MARK_TO_MARKET);
        assertNumEquals(5, pnl.getValue(1));
        assertNumEquals(5, pnl.getValue(2));
    }

    @Test
    public void markToMarketPreWindowEntryMatchesEntryAtTheRetainedWindowStart() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 99d, 121d, 110d, 132d)
                .build();
        // Oracle: with no holding cost, a long entered before the window earns
        // the same P&L in it as one bought at the window's first close (121).
        TradingRecord predating = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(4, series));
        TradingRecord atStart = new BaseTradingRecord(Trade.buyAt(3, series), Trade.sellAt(4, series));
        series.setMaximumBarCount(3);

        CumulativePnL pnl = new CumulativePnL(series, predating, EquityCurveMode.MARK_TO_MARKET);
        CumulativePnL oracle = new CumulativePnL(series, atStart, EquityCurveMode.MARK_TO_MARKET);

        assertEquals(3, series.getBeginIndex());
        assertNumEquals(-11, pnl.getValue(5));
        for (int index = 3; index <= 5; index++) {
            assertNumEquals(oracle.getValue(index), pnl.getValue(index));
        }
    }

    @Test
    public void preWindowShortCarriesOnlyBorrowingAccruedInsideTheWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 100d, 100d, 100d, 100d, 100d)
                .build();
        BaseTradingRecord record = new BaseTradingRecord(TradeType.SELL, new ZeroCostModel(),
                new LinearBorrowingCostModel(0.01d));
        record.enter(1, series.getBar(1).getClosePrice(), numFactory.one());
        series.setMaximumBarCount(3);

        CumulativePnL pnl = new CumulativePnL(series, record, EquityCurveMode.MARK_TO_MARKET);

        // Borrowing 1% of 100 per bar is 1 per bar. The window [3, 5] values the
        // short at 100 + 2 (carry accrued by index 3); the marks owe 103 and 104,
        // so P&L is 0, -1, -2: only the in-window carry, not the 4 since entry.
        assertEquals(3, series.getBeginIndex());
        assertNumEquals(numFactory.zero(), pnl.getValue(3), 1e-12);
        assertNumEquals(numFactory.numOf(-1d), pnl.getValue(4), 1e-12);
        assertNumEquals(numFactory.numOf(-2d), pnl.getValue(5), 1e-12);
    }

    @Test
    public void realizedKeepsEntryPriceCostBasisForPreWindowEntry() {
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(30d, 40d, 50d).build();
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(0, rolling), Trade.sellAt(2, rolling));
        rolling.setMaximumBarCount(2);

        CumulativePnL realized = new CumulativePnL(rolling, record, EquityCurveMode.REALIZED);
        CumulativePnL markToMarket = new CumulativePnL(rolling, record, EquityCurveMode.MARK_TO_MARKET);

        // Realized P&L is proceeds less the 30 cost basis, booked at the exit;
        // mark-to-market credits the window only with 50 - 40.
        assertEquals(1, rolling.getBeginIndex());
        assertNumEquals(0, realized.getValue(1));
        assertNumEquals(20, realized.getValue(2));
        assertNumEquals(10, markToMarket.getValue(2));
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

    @Test
    public void preservesLogicalOffsetForTradeAtNonzeroIndex() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();
        BarSeries offset = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(10)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(10, offset), Trade.sellAt(12, offset));

        CumulativePnL pnl = new CumulativePnL(offset, record, EquityCurveMode.REALIZED);

        assertEquals(10, pnl.getBarSeries().getBeginIndex());
        assertEquals(12, pnl.getBarSeries().getEndIndex());
        assertEquals(10, pnl.getBarSeries().getRemovedBarsCount());
        assertNumEquals(20, pnl.getValue(12));
    }

    @Test
    public void valuesAreAddressableAtTerminalOffsetWithoutAbsoluteSizing() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d).build();
        BarSeries terminal = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(Integer.MAX_VALUE)
                .build();
        CumulativePnL pnl = new CumulativePnL(terminal, new BaseTradingRecord());

        assertEquals(Integer.MAX_VALUE, pnl.getBarSeries().getEndIndex());
        assertEquals(1, pnl.getSize());
        assertNumEquals(0, pnl.getValue(Integer.MAX_VALUE));
    }

    @Test
    public void openTerminalPositionDoesNotWrapLoopIndexes() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d).build();
        BarSeries terminal = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(Integer.MAX_VALUE)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(Integer.MAX_VALUE, terminal));

        CumulativePnL pnl = new CumulativePnL(terminal, record);

        assertNumEquals(0, pnl.getValue(Integer.MAX_VALUE));
    }

    @Test
    public void outOfWindowReadsReturnNeutralZero() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();
        BarSeries offset = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(10)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(10, offset), Trade.sellAt(12, offset));
        CumulativePnL pnl = new CumulativePnL(offset, record, EquityCurveMode.REALIZED);

        assertNumEquals(0, pnl.getValue(9));
        assertNumEquals(0, pnl.getValue(13));
        assertNumEquals(20, pnl.getValue(12));
    }

    @Test
    public void ignoresTradesOutsideAnEmptyLogicalWindow() {
        BarSeries series = ConstrainedSeriesSupport.emptyLogicalSeries("empty-window", numFactory, 100d);
        Num one = numFactory.one();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, numFactory.numOf(100d), one),
                Trade.sellAt(0, numFactory.numOf(50d), one));

        CumulativePnL pnl = new CumulativePnL(series, tradingRecord);

        assertNumEquals(0, pnl.getValue(0));
        assertEquals(0, pnl.getSize());
    }

    @Test
    public void marksPositionExitingAfterTheWindowAtTheWindowClose() {
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("trailing-exit", numFactory, 1, 10d, 20d,
                30d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, 0, 1, null, null);
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(2, series.getBar(2).getClosePrice(), numFactory.one());

        CumulativePnL pnl = new CumulativePnL(series, tradingRecord);

        // Marked at the last window close (20 - 10), not at the later exit (30 - 10).
        assertNumEquals(10, pnl.getValue(1));
        assertEquals(List.of(numFactory.zero(), numFactory.numOf(10)), pnl.stream().toList());
        assertEquals(1, pnl.getEndIndex());
    }

    @Test
    public void neverPricesHoldingCostOfPositionsOutsideTheWindow() {
        BarSeries series = OutOfWindowPositions.series(numFactory);
        List<Num> flat = OutOfWindowPositions.values(new CumulativePnL(series, new BaseTradingRecord()));

        CumulativePnL curve = new CumulativePnL(series, OutOfWindowPositions.closedBeforeTheWindow(numFactory));
        assertEquals(flat, OutOfWindowPositions.values(curve));

        OutOfWindowPositions.calculateAll(curve);
        assertEquals(flat, OutOfWindowPositions.values(curve));
    }

    @Test
    public void calculatePositionRejectsBarsChangedSinceMaterialization() {
        BaseBarSeries series = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d)
                .build();
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series));
        CumulativePnL curve = new CumulativePnL(series, record);
        Bar captured = series.getBar(1);
        series.replaceBar(1,
                series.barBuilder()
                        .timePeriod(captured.getTimePeriod())
                        .endTime(captured.getEndTime())
                        .closePrice(200d)
                        .build());
        Position later = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series));

        // The curve holds values from the captured bars; pricing a new position
        // from the replaced bar would mix two bar histories.
        assertThrows(IllegalStateException.class, () -> curve.calculatePosition(later, 2));
    }
}
