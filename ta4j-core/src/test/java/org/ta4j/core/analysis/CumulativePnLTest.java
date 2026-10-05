/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import org.ta4j.core.num.NaN;
import java.util.ArrayList;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
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
import org.ta4j.core.TradingRecord;
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
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

public class CumulativePnLTest extends AbstractIndicatorTest<org.ta4j.core.Indicator<Num>, Num> {
    private static final int BEGIN = 2;
    private static final double[] CLOSES = { 100d, 102d, 105d, 103d, 110d };
    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

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
    public void baselineValueIsTheCarriedPnLOfPositionsClosedBeforeTheWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 121d, 131d)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series));
        CumulativePnL fullHistory = new CumulativePnL(series, record);
        series.setMaximumBarCount(2);

        CumulativePnL retained = new CumulativePnL(series, record);

        assertEquals(2, retained.getBeginIndex());
        // The first trade closed before the window: its 10 enters the window.
        assertNumEquals(10, retained.getBaselineValue());
        assertNumEquals(10, retained.getValue(2));
        assertNumEquals(fullHistory.getValue(3), retained.getValue(3));
        assertNumEquals(0, fullHistory.getBaselineValue());
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
    public void cumulativePnLTwoPositionsPinsExitDeltaOnExitBar() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series));

        var pnl = new CumulativePnL(series, record);

        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(1, pnl.getValue(1));
        assertNumEquals(2, pnl.getValue(2));
        assertNumEquals(3, pnl.getValue(3));
    }

    @Test
    public void cumulativePnLRealizedTwoPositionsWithAdjacentExits() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series));

        var pnl = new CumulativePnL(series, record, EquityCurveMode.REALIZED);

        assertNumEquals(0, pnl.getValue(0));
        assertNumEquals(0, pnl.getValue(1));
        assertNumEquals(2, pnl.getValue(2));
        assertNumEquals(3, pnl.getValue(3));
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

    @Test
    public void cumulativePnLHandlesDecreasingExitIndices() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 11d, 12d, 13d, 14d, 15d)
                .build();
        // LIFO matching closes the newest lot first: exit at 5 precedes exit
        // at 3 in the positions list even though 3 < 5.
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.LIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(new BaseTrade(0, Instant.EPOCH, series.getBar(0).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(2, Instant.EPOCH, series.getBar(2).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(5, Instant.EPOCH, series.getBar(5).getClosePrice(), numFactory.one(), null,
                ExecutionSide.SELL, null, null));
        record.operate(new BaseTrade(3, Instant.EPOCH, series.getBar(3).getClosePrice(), numFactory.one(), null,
                ExecutionSide.SELL, null, null));

        for (EquityCurveMode mode : EquityCurveMode.values()) {
            CumulativePnL actual = new CumulativePnL(series, record, mode);
            CumulativePnL reference = new CumulativePnL(series, new BaseTradingRecord(), mode);
            for (Position position : record.getPositions()) {
                reference.calculatePosition(position, series.getEndIndex());
            }

            assertSameValues(reference, actual);
        }
    }

    @Test
    public void repeatedCalculateComposesOntoPriorCurveData() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 11d, 12d, 13d, 14d, 15d)
                .build();
        TradingRecord recordA = closedPositionRecord(series, 0, 2);
        TradingRecord recordB = closedPositionRecord(series, 3, 5);

        for (EquityCurveMode mode : EquityCurveMode.values()) {
            // Reference: every position composed through the per-position
            // recipe onto one shared curve.
            CumulativePnL reference = new CumulativePnL(series, new BaseTradingRecord(), mode,
                    OpenPositionHandling.IGNORE);
            for (Position position : recordA.getPositions()) {
                reference.calculatePosition(position, series.getEndIndex());
            }
            for (Position position : recordB.getPositions()) {
                reference.calculatePosition(position, series.getEndIndex());
            }

            CumulativePnL reused = new CumulativePnL(series, new BaseTradingRecord(), mode,
                    OpenPositionHandling.IGNORE);
            reused.calculate(recordA, series.getEndIndex(), OpenPositionHandling.IGNORE);
            Num valueAfterFirst = reused.getValue(4);

            // Calculating an empty record must not reset prior curve data.
            reused.calculate(new BaseTradingRecord(), series.getEndIndex(), OpenPositionHandling.IGNORE);
            assertNumEquals(valueAfterFirst, reused.getValue(4));

            reused.calculate(recordB, series.getEndIndex(), OpenPositionHandling.IGNORE);
            for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
                assertNumEquals(reference.getValue(i), reused.getValue(i));
            }
        }
    }

    @Test
    public void repeatedCalculatePreservesPerPositionArithmeticOrder() {
        // These prices produce deltas whose running sums exceed the decimal
        // precision, which exposes addition-order differences: composing the
        // combined exit delta of a multi-position record onto an already-
        // materialized curve in one step can round to a different last digit than
        // applying each position successively.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(31.12345678901234d, 37.98765432109876d, 41.13579111357911d, 43.2468101224681d,
                        47.36912151836912d, 53.4851620485162d, 59.61723429617234d, 61.73935654739356d)
                .build();
        TradingRecord recordA = closedPositionRecord(series, 0, 2);
        TradingRecord recordB = multiPositionRecord(series, 3, 5, 6, 7);

        for (EquityCurveMode mode : EquityCurveMode.values()) {
            CumulativePnL reference = new CumulativePnL(series, new BaseTradingRecord(), mode,
                    OpenPositionHandling.IGNORE);
            for (Position position : recordA.getPositions()) {
                reference.calculatePosition(position, series.getEndIndex());
            }
            for (Position position : recordB.getPositions()) {
                reference.calculatePosition(position, series.getEndIndex());
            }

            CumulativePnL reused = new CumulativePnL(series, new BaseTradingRecord(), mode,
                    OpenPositionHandling.IGNORE);
            reused.calculate(recordA, series.getEndIndex(), OpenPositionHandling.IGNORE);
            reused.calculate(recordB, series.getEndIndex(), OpenPositionHandling.IGNORE);
            for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
                assertNumEquals(reference.getValue(i), reused.getValue(i));
            }
        }
    }

    @Test
    public void cumulativePnLPreservesPrunedSeriesBeginIndex() {
        BarSeries full = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d).build();
        TradingRecord fullRecord = closedPositionRecord(full, 3, 4);
        CumulativePnL reference = new CumulativePnL(full, fullRecord);

        BarSeries pruned = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d).build();
        TradingRecord prunedRecord = closedPositionRecord(pruned, 3, 4);
        pruned.setMaximumBarCount(3);
        assertEquals(2, pruned.getBeginIndex());

        CumulativePnL cumulativePnL = new CumulativePnL(pruned, prunedRecord);
        for (int i = pruned.getBeginIndex(); i <= pruned.getEndIndex(); i++) {
            assertNumEquals(reference.getValue(i), cumulativePnL.getValue(i));
        }
    }

    private static TradingRecord closedPositionRecord(BarSeries series, int entryIndex, int exitIndex) {
        NumFactory numFactory = series.numFactory();
        BaseTradingRecord record = new BaseTradingRecord();
        record.operate(new BaseTrade(entryIndex, Instant.EPOCH, series.getBar(entryIndex).getClosePrice(),
                numFactory.one(), null, ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(exitIndex, Instant.EPOCH, series.getBar(exitIndex).getClosePrice(),
                numFactory.one(), null, ExecutionSide.SELL, null, null));
        return record;
    }

    private static TradingRecord multiPositionRecord(BarSeries series, int... entryExitIndexes) {
        if (entryExitIndexes.length % 2 != 0) {
            throw new IllegalArgumentException("entryExitIndexes must contain complete (entry, exit) pairs");
        }
        NumFactory numFactory = series.numFactory();
        BaseTradingRecord record = new BaseTradingRecord();
        for (int i = 0; i < entryExitIndexes.length; i += 2) {
            int entryIndex = entryExitIndexes[i];
            int exitIndex = entryExitIndexes[i + 1];
            record.operate(new BaseTrade(entryIndex, Instant.EPOCH, series.getBar(entryIndex).getClosePrice(),
                    numFactory.one(), null, ExecutionSide.BUY, null, null));
            record.operate(new BaseTrade(exitIndex, Instant.EPOCH, series.getBar(exitIndex).getClosePrice(),
                    numFactory.one(), null, ExecutionSide.SELL, null, null));
        }
        return record;
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

    @Test
    public void carriesRealizedPnlAcrossPrunedBegin() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d, 130d)
                .build();
        BaseTradingRecord record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series));
        series.setMaximumBarCount(2);

        CumulativePnL pnl = new CumulativePnL(series, record);

        assertEquals(2, pnl.getBeginIndex());
        assertNumEquals(10d, pnl.getValue(2));
        assertNumEquals(10d, pnl.getValue(3));
    }

    @Test
    public void matchesEquivalentLogicalWindowAcrossModesHandlingAndIncrementalPricing() {
        for (EquityCurveMode mode : EquityCurveMode.values()) {
            for (OpenPositionHandling handling : OpenPositionHandling.values()) {
                BarSeries series = ConstrainedSeriesSupport.offsetSeries("bounded-pnl", numFactory, 2, 4, 0, 100d, 80d,
                        120d, 90d, 110d, 55d);
                BarSeries freshSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                        .withData(120d, 90d, 110d)
                        .build();

                BaseTradingRecord record = boundedRecord(2, 5);
                record.enter(0, numFactory.numOf(100d), numFactory.one());
                record.exit(1, numFactory.numOf(80d), numFactory.one());
                record.enter(1, numFactory.numOf(80d), numFactory.one());
                record.exit(3, numFactory.numOf(90d), numFactory.one());
                record.enter(4, numFactory.numOf(110d), numFactory.one());
                record.exit(4, numFactory.numOf(110d), numFactory.one());

                BaseTradingRecord equivalentRecord = boundedRecord(0, 2);
                Num entryPrice = mode == EquityCurveMode.MARK_TO_MARKET ? numFactory.numOf(120d)
                        : numFactory.numOf(80d);
                equivalentRecord.enter(0, entryPrice, numFactory.one());
                equivalentRecord.exit(1, numFactory.numOf(90d), numFactory.one());
                equivalentRecord.enter(2, numFactory.numOf(110d), numFactory.one());
                equivalentRecord.exit(2, numFactory.numOf(110d), numFactory.one());

                CumulativePnL actual = new CumulativePnL(series, record, 4, mode, handling);
                CumulativePnL expected = new CumulativePnL(freshSeries, equivalentRecord, 2, mode, handling);
                String context = "mode=" + mode + ", handling=" + handling;
                assertEquals(context, expected.stream().toList(), actual.stream().toList());

                CumulativePnL incremental = new CumulativePnL(series, boundedRecord(2, 5), 4, mode, handling);
                incremental.calculatePosition(record.getPositions().get(1), 4);
                incremental.calculatePosition(record.getPositions().get(2), 4);
                assertEquals(context + " incremental", actual.stream().toList(), incremental.stream().toList());
            }
        }
    }

    @Test
    public void treatsAnExitAfterTheLogicalEndAsOpenAtTheWindowClose() {
        for (EquityCurveMode mode : EquityCurveMode.values()) {
            for (OpenPositionHandling handling : OpenPositionHandling.values()) {
                BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("late-exit-pnl", numFactory, 4,
                        100d, 80d, 120d, 90d, 110d, 55d);
                BarSeries freshSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                        .withData(120d, 90d, 110d)
                        .build();
                BaseTradingRecord record = boundedRecord(2, 5);
                record.enter(3, numFactory.numOf(90d), numFactory.one());
                record.exit(5, numFactory.numOf(55d), numFactory.one());
                BaseTradingRecord equivalentRecord = boundedRecord(0, 3);
                equivalentRecord.enter(1, numFactory.numOf(90d), numFactory.one());

                CumulativePnL actual = new CumulativePnL(series, record, 4, mode, handling);
                CumulativePnL expected = new CumulativePnL(freshSeries, equivalentRecord, 2, mode, handling);
                String context = "mode=" + mode + ", handling=" + handling;
                assertEquals(context, expected.stream().toList(), actual.stream().toList());

                CumulativePnL incremental = new CumulativePnL(series, boundedRecord(2, 5), 4, mode, handling);
                if (handling != OpenPositionHandling.IGNORE) {
                    incremental.calculatePosition(record.getPositions().get(0), 4);
                }
                assertEquals(context + " incremental", actual.stream().toList(), incremental.stream().toList());
            }
        }
    }

    private BaseTradingRecord boundedRecord(int startIndex, int endIndex) {
        return new BaseTradingRecord(TradeType.BUY, startIndex, endIndex, new ZeroCostModel(), new ZeroCostModel());
    }

    @Test
    public void recapturesWhenABarBeforeTheWindowChangesDuringHoldingCostEvaluation() {
        PreWindowCostRace race = new PreWindowCostRace(numFactory);

        CumulativePnL raced = new CumulativePnL(race.series(), race.recordWithPosition());
        CumulativePnL settled = new CumulativePnL(race.series(), race.recordWithPosition());

        assertNumEquals(150d, race.entryClose());
        assertEquals(settled.stream().toList(), raced.stream().toList());
    }

    @Test
    public void rejectsAnIncrementalPositionWhoseHoldingCostReadsABarThatChangedBeforeTheWindow() {
        PreWindowCostRace race = new PreWindowCostRace(numFactory);
        CumulativePnL curve = new CumulativePnL(race.series(), race.emptyRecord());
        List<Num> before = curve.stream().toList();

        assertThrows(IllegalStateException.class,
                () -> curve.calculatePosition(race.position(), PreWindowCostRace.WINDOW_END));

        assertEquals(before, curve.stream().toList());
    }

    @Test
    public void borrowsSeriesWhileKeepingCapturedValuesAndBounds() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        CumulativePnL pnl = new CumulativePnL(series, new BaseTradingRecord());
        int originalSize = pnl.getSize();
        int originalEnd = pnl.getEndIndex();
        List<Num> capturedValues = pnl.stream().toList();
        BarSeries firstReturnedSeries = pnl.getBarSeries();

        appendOneBar(series, 115);
        appendOneBar(firstReturnedSeries, 120);

        assertEquals(originalSize, pnl.getSize());
        assertEquals(originalEnd, pnl.getEndIndex());
        assertEquals(capturedValues, pnl.stream().toList());
        assertSame(series, pnl.getBarSeries());
        assertSame(firstReturnedSeries, pnl.getBarSeries());
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

    @Test
    public void futuresCursorKeepsResidualExposureAfterPartialExit() {
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BarSeries barSeries = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 110, 120);
        RecordedTradeCostModel costModel = RecordedTradeCostModel.INSTANCE;
        Trade entry = Trade.fromFills(TradeType.BUY,
                List.of(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()),
                        FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1_000, 100, List.of())),
                costModel);
        Trade exit = Trade.fromFill(
                FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.SELL, 1_000, 110, List.of()), costModel);
        Position partial = new Position(entry, exit, costModel, new ZeroCostModel());
        BaseTradingRecord record = new BaseTradingRecord() {
            @Override
            public FuturesContract getFuturesContract() {
                return contract;
            }

            @Override
            public List<Position> getPositions() {
                return List.of(partial);
            }
        };

        CumulativePnL pnl = new CumulativePnL(barSeries, record, barSeries.getEndIndex(),
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(200.0, pnl.getValue(2));
        assertNumEquals(300.0, pnl.getValue(3));
    }

    @Test
    public void futuresCumulativePnlMarksCutoffBeforeRetainedSeriesAsUnavailable() {
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(numFactory);
        barSeries.setMaximumBarCount(3);
        BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();
        record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));

        CumulativePnL markToMarket = new CumulativePnL(barSeries, record, 0, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CumulativePnL realizedOnly = new CumulativePnL(barSeries, record, 0, EquityCurveMode.REALIZED,
                OpenPositionHandling.IGNORE);

        for (int index = barSeries.getBeginIndex(); index <= barSeries.getEndIndex(); index++) {
            assertNumEquals(NaN.NaN, markToMarket.getValue(index));
            assertNumEquals(0, realizedOnly.getValue(index));
        }
    }
}
