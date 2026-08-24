/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

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
    public void getBarSeriesReturnsDefensiveSnapshot() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        CumulativePnL pnl = new CumulativePnL(series, new BaseTradingRecord());
        int originalSize = pnl.getSize();

        appendOneBar(series, 115);

        assertEquals(originalSize, pnl.getSize());
        assertEquals(originalSize, pnl.getBarSeries().getBarCount());
        assertNotSame(series, pnl.getBarSeries());
        assertSame(pnl.getBarSeries(), pnl.getBarSeries());
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
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();
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
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();
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
}
