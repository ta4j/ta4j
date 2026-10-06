/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import org.ta4j.core.ConcurrentBarSeries;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicBoolean;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import java.util.ArrayList;
import java.util.List;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.mocks.MockIndicator;
import static org.junit.Assert.assertTrue;

public class ReturnsTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ReturnsTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void returnSize() {
        // Test with both LOG and DECIMAL representations
        ReturnRepresentation[] representations = { ReturnRepresentation.LOG, ReturnRepresentation.DECIMAL };
        for (var representation : representations) {
            // No return at index 0
            var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                    .withData(1d, 2d, 3d, 4d, 5d)
                    .build();
            var returns = new Returns(sampleBarSeries, new BaseTradingRecord(), representation);
            assertEquals(4, returns.getSize());
        }
    }

    @Test
    public void getBarSeriesReturnsBorrowedInstance() {
        BarSeries sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        Returns returns = new Returns(sampleBarSeries, new BaseTradingRecord(), ReturnRepresentation.DECIMAL);

        int size = returns.getSize();
        int end = returns.getEndIndex();
        List<Num> capturedValues = returns.stream().toList();
        assertSame(sampleBarSeries, returns.getBarSeries());
        sampleBarSeries.barBuilder()
                .timePeriod(sampleBarSeries.getLastBar().getTimePeriod())
                .endTime(sampleBarSeries.getLastBar().getEndTime().plus(sampleBarSeries.getLastBar().getTimePeriod()))
                .closePrice(4)
                .add();
        returns.getBarSeries()
                .barBuilder()
                .timePeriod(sampleBarSeries.getLastBar().getTimePeriod())
                .endTime(sampleBarSeries.getLastBar().getEndTime().plus(sampleBarSeries.getLastBar().getTimePeriod()))
                .closePrice(5)
                .add();
        assertEquals(size, returns.getSize());
        assertEquals(end, returns.getEndIndex());
        assertEquals(capturedValues, returns.stream().toList());
    }

    @Test
    public void valuesPreWindowEntryAtFirstRetainedClose() {
        // A rolling window capped at two bars evicts the entry bar (close 30):
        // the position is valued at the first retained close (40), so that slot
        // stays the no-prior-close placeholder and the window only earns
        // 50 / 40 - 1, not the 40 / 30 - 1 made before it.
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(2);
        rolling.barBuilder().closePrice(30d).add();
        Trade entry = Trade.buyAt(0, rolling);
        rolling.barBuilder().closePrice(40d).add();
        rolling.barBuilder().closePrice(50d).add();
        var tradingRecord = new BaseTradingRecord(entry, Trade.sellAt(2, rolling));

        Returns returns = new Returns(rolling, tradingRecord, ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET);

        assertEquals(1, rolling.getBeginIndex());
        assertTrue(returns.getValue(1).isNaN());
        assertNumEquals(50d / 40d - 1d, returns.getValue(2));
        assertEquals(1, returns.getSize());
    }

    @Test
    public void markToMarketPreWindowEntryMatchesEntryAtTheRetainedWindowStart() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 99d, 121d, 110d, 132d)
                .build();
        // Oracle: with no holding cost, a long entered before the window has
        // the same in-window returns as one bought at the window's first close.
        TradingRecord predating = new BaseTradingRecord(Trade.buyAt(1, series));
        TradingRecord atStart = new BaseTradingRecord(Trade.buyAt(3, series));
        series.setMaximumBarCount(3);

        Returns returns = new Returns(series, predating, ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        Returns oracle = new Returns(series, atStart, ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        // 110 / 121 - 1, then 132 / 110 - 1; index 3 is the placeholder.
        assertEquals(3, series.getBeginIndex());
        assertEquals(oracle.getSize(), returns.getSize());
        assertTrue(returns.getValue(3).isNaN());
        assertTrue(oracle.getValue(3).isNaN());
        assertNumEquals(numFactory.numOf(110d).dividedBy(numFactory.numOf(121d)).minus(numFactory.one()),
                returns.getValue(4));
        for (int index = 4; index <= 5; index++) {
            assertNumEquals(oracle.getValue(index), returns.getValue(index));
        }
    }

    @Test
    public void markToMarketHoldingCostCompoundsToTheRealizedReturn() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 100d, 100d, 100d)
                .build();
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(),
                new FixedTransactionCostModel(1.5d));
        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        record.exit(3, series.getBar(3).getClosePrice(), numFactory.one());

        Returns returns = new Returns(series, record, ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        // Net marks fall 100 -> 99 -> 98 -> 97 as one cost unit accrues per bar.
        assertNumEquals(99d / 100d - 1d, returns.getValue(1));
        assertNumEquals(98d / 99d - 1d, returns.getValue(2));
        assertNumEquals(97d / 98d - 1d, returns.getValue(3));
        Num compounded = numFactory.one();
        for (int index = 1; index <= 3; index++) {
            compounded = compounded.multipliedBy(returns.getValue(index).plus(numFactory.one()));
        }
        assertNumEquals(numFactory.numOf(0.97d), compounded, 1e-12);
    }

    @Test
    public void retainedHeadHoldingCostRemainsCumulativeAcrossMarks() {
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

        Returns returns = new Returns(rolling, record, ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        // 4 of holding cost over four held bars is 1 per bar. The window [2, 4]
        // values the position at 100 - 2, leaving index 2 as the placeholder; the
        // marks at 3 and 4 are 97 and 96, so each return carries one more period
        // of cumulative carry: 97/98 - 1, then 96/97 - 1 (a per-mark reset would
        // leave the second return at zero).
        assertEquals(2, rolling.getBeginIndex());
        assertTrue(returns.getValue(2).isNaN());
        assertNumEquals(numFactory.numOf(97d).dividedBy(numFactory.numOf(98d)).minus(numFactory.one()),
                returns.getValue(3), 1e-12);
        assertNumEquals(numFactory.numOf(96d).dividedBy(numFactory.numOf(97d)).minus(numFactory.one()),
                returns.getValue(4), 1e-12);
        assertTrue(returns.getValue(4).isNegative());
    }

    @Test
    public void singleReturnPositionArith() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(1, sampleBarSeries));
        var returns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.DECIMAL);
        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(1.0, returns.getValue(1));
    }

    @Test
    public void returnsWithSellAndBuyTrades() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(2, 1, 3, 5, 6, 3, 20)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(1, sampleBarSeries),
                Trade.buyAt(3, sampleBarSeries), Trade.sellAt(4, sampleBarSeries), Trade.sellAt(5, sampleBarSeries),
                Trade.buyAt(6, sampleBarSeries));

        var returns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.DECIMAL);

        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(-0.5, returns.getValue(1));
        assertNumEquals(0, returns.getValue(2));
        assertNumEquals(0, returns.getValue(3));
        assertNumEquals(1d / 5, returns.getValue(4));
        assertNumEquals(0, returns.getValue(5));
        assertNumEquals(1 - (20d / 3), returns.getValue(6));
    }

    @Test
    public void returnsRealizedModeUsesExitOnly() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 12d, 11d, 13d)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(3, sampleBarSeries));

        var returns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.DECIMAL,
                EquityCurveMode.REALIZED);

        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(0, returns.getValue(1));
        assertNumEquals(0, returns.getValue(2));
        assertNumEquals(0.3, returns.getValue(3));
    }

    @Test
    public void returnsMarkToMarketIncludesOpenPosition() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d, 105d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries));

        var returns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.DECIMAL);

        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(0.1, returns.getValue(1));
        assertNumEquals((105d / 110d) - 1d, returns.getValue(2));
    }

    @Test
    public void returnsCanIgnoreOpenPosition() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d, 105d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries));

        var returns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.DECIMAL,
                OpenPositionHandling.IGNORE);

        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(0, returns.getValue(1));
        assertNumEquals(0, returns.getValue(2));
    }

    @Test
    public void returnsWithGaps() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d, 11d, 12d)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.sellAt(2, sampleBarSeries), Trade.buyAt(5, sampleBarSeries),
                Trade.buyAt(8, sampleBarSeries), Trade.sellAt(10, sampleBarSeries));

        var returns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.LOG);

        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(0, returns.getValue(1));
        assertNumEquals(0, returns.getValue(2));
        assertNumEquals(-0.28768207245178085, returns.getValue(3));
        assertNumEquals(-0.22314355131420976, returns.getValue(4));
        assertNumEquals(-0.1823215567939546, returns.getValue(5));
        assertNumEquals(0, returns.getValue(6));
        assertNumEquals(0, returns.getValue(7));
        assertNumEquals(0, returns.getValue(8));
        assertNumEquals(0.10536051565782635, returns.getValue(9));
        assertNumEquals(0.09531017980432493, returns.getValue(10));
        assertNumEquals(0, returns.getValue(11));
    }

    @Test
    public void returnsWithNoPositions() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(3d, 2d, 5d, 4d, 7d, 6d, 7d, 8d, 5d, 6d)
                .build();
        var returns = new Returns(sampleBarSeries, new BaseTradingRecord(), ReturnRepresentation.LOG);
        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(0, returns.getValue(4));
        assertNumEquals(0, returns.getValue(7));
        assertNumEquals(0, returns.getValue(9));
    }

    @Test
    public void returnedValueListsAreImmutable() {
        BarSeries sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        BaseTradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries),
                Trade.sellAt(1, sampleBarSeries));
        Returns returns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.DECIMAL);

        assertThrows(UnsupportedOperationException.class, () -> returns.getValues().add(numFactory.one()));
        assertThrows(UnsupportedOperationException.class, () -> returns.getRawValues().add(numFactory.one()));
    }

    @Test
    public void returnsPrecision() {
        var doubleNumSeries = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(1.2d, 1.1d)
                .build();

        var highPrecisionContext = new MathContext(32, RoundingMode.HALF_UP);
        var precisionFactory = DecimalNumFactory.getInstance(highPrecisionContext);
        var precisionSeries = new MockBarSeriesBuilder().withNumFactory(precisionFactory).withData(1.2d, 1.1d).build();

        var fullRecordDouble = new BaseTradingRecord();
        fullRecordDouble.enter(doubleNumSeries.getBeginIndex(), doubleNumSeries.getBar(0).getClosePrice(),
                doubleNumSeries.numFactory().one());
        fullRecordDouble.exit(doubleNumSeries.getEndIndex(), doubleNumSeries.getBar(1).getClosePrice(),
                doubleNumSeries.numFactory().one());

        var fullRecordPrecision = new BaseTradingRecord();
        fullRecordPrecision.enter(precisionSeries.getBeginIndex(), precisionSeries.getBar(0).getClosePrice(),
                precisionSeries.numFactory().one());
        fullRecordPrecision.exit(precisionSeries.getEndIndex(), precisionSeries.getBar(1).getClosePrice(),
                precisionSeries.numFactory().one());

        var arithDouble = new Returns(doubleNumSeries, fullRecordDouble, ReturnRepresentation.DECIMAL).getValue(1);
        var arithPrecision = new Returns(precisionSeries, fullRecordPrecision, ReturnRepresentation.DECIMAL)
                .getValue(1);
        var logDouble = new Returns(doubleNumSeries, fullRecordDouble, ReturnRepresentation.LOG).getValue(1);
        var logPrecision = new Returns(precisionSeries, fullRecordPrecision, ReturnRepresentation.LOG).getValue(1);

        assertFalse(arithDouble.isNaN());
        assertFalse(arithPrecision.isNaN());
        assertFalse(logDouble.isNaN());
        assertFalse(logPrecision.isNaN());

        assertNumEquals(DoubleNum.valueOf(-0.08333333333333326), arithDouble);

        var expectedArithmetic = DecimalNum.valueOf("1.1", highPrecisionContext)
                .dividedBy(DecimalNum.valueOf("1.2", highPrecisionContext))
                .minus(DecimalNum.valueOf(1, highPrecisionContext));
        assertNumEquals(expectedArithmetic, arithPrecision);

        assertNumEquals(DoubleNum.valueOf(-0.08701137698962969), logDouble);
        assertNumEquals(DecimalNum.valueOf("-0.087011376989629766167765901873746", highPrecisionContext), logPrecision);
    }

    @Test
    public void returnsRealizedModeUsesRepresentationForFlatPeriods() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 12d, 11d, 13d)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(3, sampleBarSeries));

        var returns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.MULTIPLICATIVE,
                EquityCurveMode.REALIZED);

        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(1, returns.getValue(1));
        assertNumEquals(1, returns.getValue(2));
        assertNumEquals(1.3, returns.getValue(3));
    }

    @Test
    public void realizedModeIgnoresOpenPositionEvenWithMarkToMarketHandling() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d, 105d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries));

        var returns = new Returns(sampleBarSeries, tradingRecord, sampleBarSeries.getEndIndex(),
                ReturnRepresentation.DECIMAL, EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(0, returns.getValue(1));
        assertNumEquals(0, returns.getValue(2));
    }

    @Test
    public void returnsRespectFinalIndexForOpenPositions() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d, 120d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries));

        var returns = new Returns(sampleBarSeries, tradingRecord, 1, ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(0.1, returns.getValue(1));
        assertNumEquals(0, returns.getValue(2));
    }

    @Test
    public void returnsMarkToMarketIncludesOpenPositionMultiplicative() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d, 105d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries));

        var returns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.MULTIPLICATIVE);

        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(1.1, returns.getValue(1));
        assertNumEquals(105d / 110d, returns.getValue(2));
    }

    @Test
    public void returnsCanIgnoreOpenPositionMultiplicative() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d, 105d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries));

        var returns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.MULTIPLICATIVE,
                OpenPositionHandling.IGNORE);

        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(1, returns.getValue(1));
        assertNumEquals(1, returns.getValue(2));
    }

    @Test
    public void returnsFromPositionDefaultRepresentationMatchesTradingRecord() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        var position = new Position(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(1, sampleBarSeries));
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(1, sampleBarSeries));

        var positionReturns = new Returns(sampleBarSeries, position);
        var tradingRecordReturns = new Returns(sampleBarSeries, tradingRecord);

        assertNumEquals(tradingRecordReturns.getValue(0), positionReturns.getValue(0));
        assertNumEquals(tradingRecordReturns.getValue(1), positionReturns.getValue(1));
    }

    @Test
    public void returnsFromPositionDecimalMatchesTradingRecord() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        var position = new Position(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(1, sampleBarSeries));
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(1, sampleBarSeries));

        var positionReturns = new Returns(sampleBarSeries, position, ReturnRepresentation.DECIMAL);
        var tradingRecordReturns = new Returns(sampleBarSeries, tradingRecord, ReturnRepresentation.DECIMAL);

        assertNumEquals(NaN.NaN, positionReturns.getValue(0));
        assertNumEquals(1.0, positionReturns.getValue(1));
        assertNumEquals(tradingRecordReturns.getValue(1), positionReturns.getValue(1));
    }

    @Test
    public void openPositionOpenedOnFinalBarYieldsZeroReturn() {
        var barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 1d).build();
        var tradingRecord = new BaseTradingRecord();

        var endIndex = barSeries.getEndIndex();
        tradingRecord.enter(endIndex, barSeries.getBar(endIndex).getClosePrice(), barSeries.numFactory().one());

        var returns = new Returns(barSeries, tradingRecord, endIndex, ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        var lastReturn = returns.getValue(endIndex);
        assertFalse(lastReturn.isNaN());
        assertNumEquals(0, lastReturn);
    }

    @Test
    public void returns_markToMarket_doesNotUseFutureExitPriceWhenExitAfterFinalIndex() {
        var series = new MockBarSeriesBuilder().withData(10d, 11d, 12d, 13d, 100d).build();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        tradingRecord.exit(4, series.getBar(4).getClosePrice(), series.numFactory().one());

        var returns = new Returns(series, tradingRecord, 2, ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        var one = series.numFactory().one();
        var expectedAt2 = series.getBar(2).getClosePrice().dividedBy(series.getBar(1).getClosePrice()).minus(one);

        assertNumEquals(returns.getRawValues().get(2), expectedAt2);
    }

    @Test
    public void returns_ignore_skipsPositionsThatAreOpenAtFinalIndex() {
        var series = new MockBarSeriesBuilder().withData(10d, 11d, 12d, 13d, 100d).build();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        tradingRecord.exit(4, series.getBar(4).getClosePrice(), series.numFactory().one());

        var returns = new Returns(series, tradingRecord, 2, ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);

        var zero = series.numFactory().zero();
        assertNumEquals(returns.getRawValues().get(1), zero);
        assertNumEquals(returns.getRawValues().get(2), zero);
    }

    @Test
    public void returnsIncludeMultipleOpenLotsFromBaseTradingRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 12d, 14d).build();
        var record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.EPOCH, series.getBar(0).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(1, Instant.EPOCH, series.getBar(1).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, null, null));

        var returns = new Returns(series, record, ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        var expectedAt1 = (12d / 10d) - 1d;
        var stepFactor = 14d / 12d;
        var expectedAt2 = (stepFactor * stepFactor) - 1d;

        assertNumEquals(expectedAt1, returns.getValue(1));
        assertNumEquals(expectedAt2, returns.getValue(2));
    }

    @Test
    public void preservesLogicalOffsetForTradeAtNonzeroIndex() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();
        BarSeries offset = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(10)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(10, offset), Trade.sellAt(12, offset));

        Returns returns = new Returns(offset, record, ReturnRepresentation.DECIMAL, EquityCurveMode.REALIZED);

        assertEquals(10, returns.getBarSeries().getBeginIndex());
        assertEquals(12, returns.getBarSeries().getEndIndex());
        assertEquals(10, returns.getBarSeries().getRemovedBarsCount());
        assertNumEquals(2.0, returns.getValue(12));
    }

    @Test
    public void valuesAreAddressableAtTerminalOffsetWithoutAbsoluteSizing() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d).build();
        BarSeries terminal = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(Integer.MAX_VALUE)
                .build();
        Returns returns = new Returns(terminal, new BaseTradingRecord(), ReturnRepresentation.DECIMAL);

        assertEquals(0, returns.getSize());
        assertTrue(returns.getValue(Integer.MAX_VALUE).isNaN());
    }

    @Test
    public void outOfWindowReadsReturnNaN() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();
        BarSeries offset = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(10)
                .build();
        Returns returns = new Returns(offset, new BaseTradingRecord(), ReturnRepresentation.DECIMAL);

        assertTrue(returns.getValue(9).isNaN());
        assertTrue(returns.getValue(13).isNaN());
    }

    @Test
    public void marksPositionExitingAfterTheWindowAtTheWindowClose() {
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("trailing-exit", numFactory, 1, 10d, 20d,
                30d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, 0, 1, null, null);
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(2, series.getBar(2).getClosePrice(), numFactory.one());

        Returns returns = new Returns(series, tradingRecord, ReturnRepresentation.DECIMAL);

        // One window return (10 -> 20); the move to the later exit at 30 is not seen.
        assertNumEquals(1, returns.getValue(1));
        assertTrue(returns.getValue(2).isNaN());
        assertEquals(1, returns.getSize());
    }

    @Test
    public void getValueStaysAnchoredToMaterializedWindow() {
        // The buffer is materialized against the window at construction time;
        // later rolling advances of the borrowed series must not rebase the
        // lookup, or old returns leak onto never-calculated bars.
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(2);
        rolling.barBuilder().closePrice(100d).add();
        Trade entry = Trade.buyAt(0, rolling);
        rolling.barBuilder().closePrice(110d).add();
        var record = new BaseTradingRecord(entry, Trade.sellAt(1, rolling));
        Returns returns = new Returns(rolling, record, rolling.getEndIndex(), ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        Num anchoredReturn = returns.getValue(1);

        rolling.barBuilder().closePrice(120d).add();

        assertEquals(1, rolling.getBeginIndex());
        assertNumEquals(anchoredReturn, returns.getValue(1));
        assertTrue(returns.getValue(2).isNaN());
        assertEquals(returns.getValues(), returns.stream().toList());
    }

    @Test
    public void sizeEndsAtTheWindowEvenWhenAnExitLandsAfterIt() {
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("tail", numFactory, 1, 100d, 110d, 55d);
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));

        Returns returns = new Returns(series, record, ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        assertEquals(1, returns.getSize());
        assertNumEquals(0.1, returns.getValue(1));
    }

    @Test
    public void sameBarReturnSurvivesAtOffsetWindowStart() {
        // A position entering and exiting on the first retained bar writes a
        // real return into the first buffer slot; the placeholder overwrite
        // must not erase it just because no entry predates the window.
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(1);
        for (int i = 0; i < 10; i++) {
            rolling.barBuilder().closePrice(100d).add();
        }
        rolling.barBuilder().closePrice(110d).add();
        var record = new BaseTradingRecord(Trade.buyAt(10, rolling), Trade.sellAt(10, rolling));

        Returns returns = new Returns(rolling, record, rolling.getEndIndex(), ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        assertEquals(10, rolling.getBeginIndex());
        assertNumEquals(numFactory.numOf(0d), returns.getValue(10));
    }

    @Test
    public void sizeCountsFirstRetainedExitReturn() {
        // The pre-window entry exits on the first retained bar at 25, half its
        // 50 close: the first slot carries that real -50% instead of a
        // placeholder, and the reported size must include it or tail-risk
        // criteria silently drop the loss.
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(2);
        rolling.barBuilder().closePrice(100d).add();
        Trade entry = Trade.buyAt(0, rolling);
        rolling.barBuilder().closePrice(50d).add();
        rolling.barBuilder().closePrice(120d).add();
        var record = new BaseTradingRecord(entry, Trade.sellAt(1, numFactory.numOf(25d), numFactory.one()));

        Returns returns = new Returns(rolling, record, rolling.getEndIndex(), ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(numFactory.numOf(-0.5d), returns.getValue(1));
        assertNumEquals(numFactory.zero(), returns.getValue(2));
        assertEquals(2, returns.getSize());
    }

    @Test
    public void ignoresTradesOutsideAnEmptyLogicalWindow() {
        BarSeries series = ConstrainedSeriesSupport.emptyLogicalSeries("empty-window", numFactory, 100d);
        Num one = numFactory.one();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, numFactory.numOf(100d), one),
                Trade.sellAt(0, numFactory.numOf(50d), one));

        Returns returns = new Returns(series, tradingRecord, ReturnRepresentation.DECIMAL);

        assertTrue(returns.getValue(0).isNaN());
        assertEquals(0, returns.getSize());
    }

    @Test
    public void retainsUndefinedFirstRetainedExitReturnInMaterializedSize() {
        // The pre-window entry is valued at the first retained close, 0, and
        // exits there at 0: the 0/0 return is undefined but real (measured from
        // the 10 entry price it would be -100%), so it stays in the materialized
        // size next to the flat return at index 2.
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(2);
        rolling.barBuilder().closePrice(10d).add();
        Trade entry = Trade.buyAt(0, rolling);
        rolling.barBuilder().closePrice(0d).add();
        Trade exit = Trade.sellAt(1, rolling);
        rolling.barBuilder().closePrice(30d).add();
        TradingRecord tradingRecord = new BaseTradingRecord(entry, exit);

        Returns returns = new Returns(rolling, tradingRecord, ReturnRepresentation.DECIMAL);

        assertEquals(1, rolling.getBeginIndex());
        assertTrue(returns.getRawValues().get(0).isNaN());
        assertEquals(2, returns.getSize());
    }

    @Test
    public void neverPricesHoldingCostOfPositionsOutsideTheWindow() {
        BarSeries series = OutOfWindowPositions.series(numFactory);
        List<Num> flat = OutOfWindowPositions.values(new Returns(series, new BaseTradingRecord()));

        Returns curve = new Returns(series, OutOfWindowPositions.closedBeforeTheWindow(numFactory));
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
        Returns curve = new Returns(series, record, ReturnRepresentation.DECIMAL);
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
    public void calculatePositionRefreshesTheReturnViews() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d, 99d).build();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series));
        Returns expected = new Returns(series, position, ReturnRepresentation.DECIMAL);
        Returns curve = new Returns(series, new BaseTradingRecord(), ReturnRepresentation.DECIMAL);

        curve.calculatePosition(position, 2);

        assertEquals(expected.getValues(), curve.getValues());
        assertEquals(expected.getRawValues(), curve.getRawValues());
        assertEquals(expected.stream().toList(), curve.stream().toList());
        assertNumEquals(0.1d, curve.getValue(1));
        assertEquals(expected.getSize(), curve.getSize());
    }

    @Test
    public void recordStartExcludesACompletedEarlierTrade() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 80d, 90d, 100d, 110d)
                .build();
        TradingRecord record = new BaseTradingRecord(TradeType.BUY, 2, 4, null, null);
        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        record.exit(1, series.getBar(1).getClosePrice(), numFactory.one());

        Returns returns = new Returns(series, record, ReturnRepresentation.DECIMAL);

        assertEquals(2, returns.getBeginIndex());
        assertTrue(returns.getValue(1).isNaN());
        assertTrue(returns.getValue(2).isNaN());
        assertNumEquals(numFactory.zero(), returns.getValue(3));
    }

    @Test
    public void recapturesWhenABarBeforeTheWindowChangesDuringHoldingCostEvaluation() {
        PreWindowCostRace race = new PreWindowCostRace(numFactory);

        Returns raced = new Returns(race.series(), race.recordWithPosition(), ReturnRepresentation.DECIMAL);
        Returns settled = new Returns(race.series(), race.recordWithPosition(), ReturnRepresentation.DECIMAL);

        assertNumEquals(150d, race.entryClose());
        assertEquals(settled.getValues(), raced.getValues());
    }

    @Test
    public void rejectsAnIncrementalPositionWhoseHoldingCostReadsABarThatChangedBeforeTheWindow() {
        PreWindowCostRace race = new PreWindowCostRace(numFactory);
        Returns curve = new Returns(race.series(), race.emptyRecord(), ReturnRepresentation.DECIMAL);
        List<Num> before = curve.getValues();

        assertThrows(IllegalStateException.class,
                () -> curve.calculatePosition(race.position(), PreWindowCostRace.WINDOW_END));

        assertEquals(before, curve.getValues());
    }

    @Test
    public void spotReturnsWindowedSeriesMatchesUnwindowedInsideWindow() {
        BarSeries full = series(numFactory, 0);
        BarSeries windowed = series(numFactory, BEGIN);
        Position fullPosition = spotPosition(numFactory, 0);
        Position windowedPosition = spotPosition(numFactory, BEGIN);

        Returns fullReturns = new Returns(full, fullPosition, ReturnRepresentation.DECIMAL);
        Returns windowedReturns = new Returns(windowed, windowedPosition, ReturnRepresentation.DECIMAL);

        assertEquals(CLOSES.length, windowedReturns.getValues().size());
        assertTrue("first retained slot without a return is undefined", windowedReturns.getRawValues().get(0).isNaN());
        assertTrue("returns before the captured window are undefined", windowedReturns.getValue(BEGIN - 1).isNaN());
        for (int index = 1; index < CLOSES.length; index++) {
            assertNumEquals(fullReturns.getRawValues().get(index), windowedReturns.getRawValues().get(index));
            assertNumEquals(windowedReturns.getValues().get(index), windowedReturns.getValue(BEGIN + index));
        }
    }

    @Test
    public void futuresReturnsWindowedSeriesMatchesUnwindowedInsideWindow() {
        FuturesContract contract = linearPerpetual(numFactory);
        BarSeries full = series(numFactory, 0);
        BarSeries windowed = series(numFactory, BEGIN);
        BaseTradingRecord fullRecord = futuresRecord(contract, 0);
        BaseTradingRecord windowedRecord = futuresRecord(contract, BEGIN);

        Returns fullReturns = new Returns(full, fullRecord, ReturnRepresentation.DECIMAL);
        Returns windowedReturns = new Returns(windowed, windowedRecord, ReturnRepresentation.DECIMAL);

        assertEquals(CLOSES.length, windowedReturns.getValues().size());
        assertTrue(windowedReturns.hasFirstBarReturn());
        assertNumEquals(0, windowedReturns.getRawValues().get(0));
        assertTrue("returns before the captured window are undefined", windowedReturns.getValue(BEGIN - 1).isNaN());
        for (int index = 1; index < CLOSES.length; index++) {
            assertNumEquals(fullReturns.getRawValues().get(index), windowedReturns.getRawValues().get(index));
            assertNumEquals(windowedReturns.getValues().get(index), windowedReturns.getValue(BEGIN + index));
        }
    }

    @Test
    public void realizedReturnsKeepZeroActivityAtRetainedHead() {
        FuturesContract contract = linearPerpetual(numFactory);
        BarSeries windowed = series(numFactory, BEGIN);
        BaseTradingRecord record = futuresRecord(contract, 0);

        Returns returns = new Returns(windowed, record, ReturnRepresentation.DECIMAL, EquityCurveMode.REALIZED,
                OpenPositionHandling.IGNORE);

        assertFalse(returns.hasSeededFirstBarReturn());
        assertTrue(returns.getRawValues().get(0).isNaN());
    }

    @Test
    public void markToMarketReturnsKeepNeutralRetainedHeadWithoutActivity() {
        FuturesContract contract = linearPerpetual(numFactory);
        BarSeries windowed = series(numFactory, BEGIN);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(500))
                .build();
        record.operate(fill(contract, 0, ExecutionSide.BUY, 1_000, 90));
        record.operate(fill(contract, 4, ExecutionSide.SELL, 1_000, 110));

        Returns returns = new Returns(windowed, record, ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        assertFalse(returns.hasFirstBarReturn());
        assertTrue(returns.getRawValues().get(0).isNaN());
        assertNumEquals(1.0 / 30.0, returns.getRawValues().get(1));
    }

    private static Position spotPosition(NumFactory numFactory, int indexOffset) {
        Num one = numFactory.one();
        Trade entry = Trade.buyAt(1 + indexOffset, numFactory.numOf(CLOSES[1]), one, RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.sellAt(4 + indexOffset, numFactory.numOf(CLOSES[4]), one, RecordedTradeCostModel.INSTANCE);
        return new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
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
    public void futuresReturnsUseConsecutiveEquityRatios() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 4, ExecutionSide.SELL, 1_000, 110, List.of()));

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

            Num growth = testFactory.one();
            for (int index = 1; index <= 4; index++) {
                growth = growth.multipliedBy(testFactory.one().plus(decimal.getValue(index)));
            }
            assertNumEquals(1.2, growth);
        }
    }

    @Test
    public void futuresReturnsWithNonpositiveEquityAreUndefined() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 100, 95, 96);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100_000, 100, List.of()));

            Returns decimal = new Returns(barSeries, record, ReturnRepresentation.DECIMAL);
            Returns logarithmic = new Returns(barSeries, record, ReturnRepresentation.LOG);

            assertTrue(decimal.getValue(0).isNaN());
            assertNumEquals(-10.0, decimal.getValue(1));
            assertTrue(decimal.getValue(2).isNaN());
            assertTrue(logarithmic.getValue(1).isNaN());
            assertTrue(logarithmic.getValue(2).isNaN());
        }
    }

    @Test
    public void futuresReturnsRequireExplicitAccountCapital() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));

            assertThrows(IllegalStateException.class,
                    () -> new Returns(barSeries, record, ReturnRepresentation.DECIMAL));
        }
    }

    @Test
    public void futuresPerformanceRejectsUnrepresentablePnlConversion() {
        Num decimalPnl = DecimalNumFactory.getInstance().numOf("1e-400");

        assertThrows(IllegalArgumentException.class,
                () -> FuturesPerformanceSupport.toFactory(DoubleNumFactory.getInstance(), decimalPnl));
    }

    @Test
    public void singleFuturesPositionReturnsUseEntrySettlementNotional() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            Position position = FuturesAnalysisTestSupport.openPosition(contract, 0, 1_000, 100);
            Returns returns = new Returns(barSeries, position, ReturnRepresentation.DECIMAL);

            assertTrue(returns.getValue(0).isNaN());
            assertNumEquals(0.02, returns.getValue(1));
            assertNumEquals(0.02941176470588236, returns.getValue(2));
            assertNumEquals(-0.0190476190476191, returns.getValue(3));
            assertNumEquals(0.06796116504854367, returns.getValue(4));
        }
    }

    @Test
    public void futuresReturnsPropagateUnavailableMarkAsNaN() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 100, 110);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
            Indicator<Num> markPrice = new MockIndicator(barSeries, List.of(testFactory.numOf(100), NaN.NaN));

            Returns returns = new Returns(barSeries, record, markPrice, 1, ReturnRepresentation.DECIMAL,
                    EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

            assertTrue(returns.getValue(1).isNaN());
        }
    }

    @Test
    public void allExecutedInverseEntryUsesAggregateTradeBasis() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.inverseBtcPerpetual(testFactory);
            Trade entry = Trade.fromFills(TradeType.BUY,
                    List.of(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 20_000, List.of()),
                            FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 100, 25_000, List.of())),
                    RecordedTradeCostModel.INSTANCE);
            Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            Num expected = contract.settlementNotional(entry.getAmount(), entry.getPricePerAsset());

            assertNumEquals(expected, FuturesPerformanceSupport.entryNotional(position));
        }
    }

    @Test
    public void partiallyClosedFuturesReturnsUseEachSliceEntryNotional() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 10_000, 11_000);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 1_000_000);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 4, 10_000,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 4))));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, 11_000,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 1))));

            Position closedSlice = record.getPositions().getFirst();
            Returns closedReturns = new Returns(barSeries, closedSlice, ReturnRepresentation.DECIMAL);
            assertTrue(closedReturns.getValue(0).isNaN());
            assertNumEquals(0.08000000000000007, closedReturns.getValue(1));
        }
    }

    @Test
    public void futuresReturnsRejectMarkPriceFromAnotherSeries() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BarSeries otherSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 4, ExecutionSide.SELL, 1_000, 110, List.of()));

            assertThrows(IllegalArgumentException.class, () -> new Returns(barSeries, record,
                    new org.ta4j.core.indicators.helpers.ClosePriceIndicator(otherSeries), 4,
                    ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
        }
    }

    @Test
    public void emptyFundedFuturesReturnsAreFlat() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            Returns returns = new Returns(barSeries, record, ReturnRepresentation.DECIMAL);

            assertTrue(returns.getValue(0).isNaN());
            for (int index = 1; index <= barSeries.getEndIndex(); index++) {
                assertNumEquals(0.0, returns.getValue(index));
            }
        }
    }

    @Test
    public void deferredEntryFillsDoNotInflateSinglePositionCapital() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            TradeFill executed = FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of());
            TradeFill deferred = FuturesAnalysisTestSupport.fill(contract, -1, ExecutionSide.BUY, 1_000, 100,
                    List.of());
            Trade entry = Trade.fromFills(TradeType.BUY, List.of(executed, deferred), RecordedTradeCostModel.INSTANCE);
            Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

            Returns returns = new Returns(barSeries, position, ReturnRepresentation.DECIMAL);

            assertNumEquals(0.02, returns.getValue(1));
            assertNumEquals(0.02941176470588236, returns.getValue(2));
        }
    }

    @Test
    public void scalarFuturesReturnsUseFallbackExecutionFills() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 100, 102);
            Trade scalarFutures = new BaseTrade(0, Instant.EPOCH, testFactory.numOf(100), testFactory.numOf(1_000),
                    null, ExecutionSide.BUY, null, null) {
                @Override
                public List<TradeFill> getFills() {
                    return List.of();
                }

                @Override
                public FuturesContract getFuturesContract() {
                    return contract;
                }
            };
            Position position = new Position(scalarFutures, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            TradingRecord record = new AlternateFuturesRecord(contract, testFactory.numOf(1_000), List.of(), position);

            Returns returns = new Returns(barSeries, record, ReturnRepresentation.DECIMAL);

            assertNumEquals(0.02, returns.getValue(1));
        }
    }

    @Test
    public void rejectsFallbackCapitalThatUnderflowsAnalysisFactory() {
        NumFactory sourceFactory = DecimalNumFactory.getInstance();
        NumFactory analysisFactory = DoubleNumFactory.getInstance();
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(sourceFactory)
                .toBuilder()
                .contractSize(sourceFactory.numOf("1e-400"))
                .build();
        BarSeries barSeries = FuturesAnalysisTestSupport.series(analysisFactory, 100, 102);
        Trade scalarFutures = new BaseTrade(0, Instant.EPOCH, sourceFactory.numOf(100), sourceFactory.one(), null,
                ExecutionSide.BUY, null, null) {
            @Override
            public List<TradeFill> getFills() {
                return List.of();
            }

            @Override
            public FuturesContract getFuturesContract() {
                return contract;
            }
        };
        Position position = new Position(scalarFutures, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        assertThrows(IllegalStateException.class, () -> new Returns(barSeries, position, ReturnRepresentation.DECIMAL));
    }

    @Test
    public void retainedFuturesReturnsKeepNeutralHeadWithoutActivity() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries retained = FuturesAnalysisTestSupport.series(testFactory, 10_000, 10_100, 10_100, 10_100, 10_100);
            retained.setMaximumBarCount(3);
            assertEquals(2, retained.getBeginIndex());
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 100);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 10_000, List.of()));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, 10_100, List.of()));

            Returns returns = new Returns(retained, record, ReturnRepresentation.DECIMAL);

            assertFalse(returns.hasFirstBarReturn());
            assertTrue(returns.getValue(2).isNaN());
            assertNumEquals(0, returns.getValue(3));
        }
    }

    @Test
    public void whollyDeferredFuturesPositionReturnsNeutralCurve() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            Trade entry = Trade.fromFill(
                    FuturesAnalysisTestSupport.fill(contract, -1, ExecutionSide.BUY, 1_000, 100, List.of()),
                    RecordedTradeCostModel.INSTANCE);
            Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

            Returns returns = new Returns(barSeries, position, ReturnRepresentation.DECIMAL);

            assertTrue(returns.getValue(0).isNaN());
            for (int index = 1; index <= barSeries.getEndIndex(); index++) {
                assertNumEquals(0, returns.getValue(index));
            }
        }
    }

    @Test
    public void deferredOnlyAndHeadFuturesActivityDoNotSeedRetainedFirstBar() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries retained = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            retained.setMaximumBarCount(4);
            assertEquals(1, retained.getBeginIndex());

            TradeFill deferred = FuturesAnalysisTestSupport.fill(contract, -1, ExecutionSide.BUY, 1_000, 100,
                    List.of());
            TradeFill head = FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1_000, 100, List.of());
            Position deferredOnly = new Position(Trade.fromFill(deferred, RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            Position headPosition = new Position(
                    Trade.fromFills(TradeType.BUY, List.of(deferred, head), RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            TradingRecord record = new AlternateFuturesRecord(contract, testFactory.numOf(500), List.of(deferredOnly),
                    headPosition);

            Returns returns = new Returns(retained, record, ReturnRepresentation.DECIMAL);

            assertFalse(returns.hasSeededFirstBarReturn());
            assertNumEquals(0.04, returns.getValue(1));

            TradeFill preWindow = FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100,
                    List.of());
            Position genuinelyPreWindow = new Position(
                    Trade.fromFills(TradeType.BUY, List.of(deferred, preWindow, head), RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            TradingRecord seededRecord = new AlternateFuturesRecord(contract, testFactory.numOf(500),
                    List.of(deferredOnly), genuinelyPreWindow);

            assertTrue(new Returns(retained, seededRecord, ReturnRepresentation.DECIMAL).hasSeededFirstBarReturn());
        }
    }

    private static final class AlternateFuturesRecord extends BaseTradingRecord {

        private final FuturesContract contract;
        private final Num initialCapital;
        private final List<Position> positions;
        private final Position currentPosition;

        private AlternateFuturesRecord(FuturesContract contract, Num initialCapital, List<Position> positions,
                Position currentPosition) {
            super();
            this.contract = contract;
            this.initialCapital = initialCapital;
            this.positions = positions;
            this.currentPosition = currentPosition;
        }

        @Override
        public FuturesContract getFuturesContract() {
            return contract;
        }

        @Override
        public Num getInitialCapital() {
            return initialCapital;
        }

        @Override
        public List<Position> getPositions() {
            return positions;
        }

        @Override
        public Position getCurrentPosition() {
            return currentPosition;
        }

        @Override
        public List<Position> getOpenPositions() {
            return List.of(currentPosition);
        }
    }

    @Test
    public void emptySeriesHasNoReturns() {
        BarSeries emptySeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        Returns returns = new Returns(emptySeries, new BaseTradingRecord(), ReturnRepresentation.DECIMAL);

        assertEquals(0, returns.getSize());
    }

    @Test
    public void flatExecutedFuturesExposureDoesNotRequireMarkPrice() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            TradeFill executedEntry = FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100,
                    List.of());
            TradeFill laterEntry = FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1_000, 100,
                    List.of());
            Trade entry = Trade.fromFills(TradeType.BUY, List.of(executedEntry, laterEntry),
                    RecordedTradeCostModel.INSTANCE);
            Trade exit = Trade.fromFill(
                    FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 110, List.of()),
                    RecordedTradeCostModel.INSTANCE);
            Position position = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            TradingRecord record = new AlternateFuturesRecord(contract, testFactory.numOf(1_000), List.of(position),
                    position);
            Indicator<Num> unavailableMark = new MockIndicator(barSeries, List.of(testFactory.numOf(100), NaN.NaN,
                    testFactory.numOf(105), testFactory.numOf(103), testFactory.numOf(110)));
            Returns returns = new Returns(barSeries, record, unavailableMark, 1, ReturnRepresentation.DECIMAL,
                    EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

            assertNumEquals(0.1, returns.getValue(1));
        }
    }

    @Test
    public void incrementalFuturesPositionSharesConstructorSettlementEconomics() {
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110);
        BaseTradingRecord complete = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 110, List.of()));
        BaseTradingRecord empty = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        Returns curve = new Returns(series, empty, org.ta4j.core.criteria.ReturnRepresentation.DECIMAL);
        curve.calculatePosition(complete.getPositions().getFirst(), 1);
        assertNumEquals(0.2, curve.getValue(1));
    }

    @Test
    public void spotPeriodFactorsSurviveNativeRebuildForEveryConstructorOrigin() {
        for (int origin = 0; origin < 3; origin++) {
            for (ReturnRepresentation representation : ReturnRepresentation.values()) {
                BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100);
                FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                        .toBuilder()
                        .contractSize(numFactory.one())
                        .build();
                Position spot = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.one()),
                        Trade.sellAt(1, numFactory.numOf(110), numFactory.one()), new ZeroCostModel(),
                        new ZeroCostModel());
                TradingRecord initial = origin == 2 ? FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500)
                        : origin == 1 ? new BaseTradingRecord(spot) : new BaseTradingRecord();
                Returns curve = new Returns(series, initial, 2, representation, EquityCurveMode.REALIZED,
                        OpenPositionHandling.MARK_TO_MARKET);
                List<Position> lots = FuturesAnalysisTestSupport.crossLotFeeRecord(contract, TradeType.BUY, false)
                        .getOpenPositions();
                curve.calculatePosition(lots.getFirst(), 2);
                curve.calculatePosition(spot, 2);
                curve.calculatePosition(lots.getLast(), 2);
                // Spot period factors combine multiplicatively with the final native
                // factor, independently of update order or an existing spot seed.
                double total = (origin == 1 ? 1.21 : 1.1) * (1 - 1d / (origin == 2 ? 500 : 100));
                double expected = switch (representation) {
                case DECIMAL -> total - 1;
                case PERCENTAGE -> 100 * (total - 1);
                case MULTIPLICATIVE -> total;
                case LOG -> Math.log(total);
                };
                assertNumEquals(expected, curve.getValue(1));
                BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
                zero.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1, 100, List.of()));
                zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.SELL, 1, 100, List.of()));
                curve.calculatePosition(zero.getPositions().getFirst(), 2);
                assertNumEquals(expected, curve.getValue(1));
            }
        }
    }

    @Test
    public void spotDefinedHeadPeriodSurvivesZeroNativeHeadActivity() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100, 100);
        series.setMaximumBarCount(2);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        BaseTradingRecord historical = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        historical.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 100,
                List.of(FuturesAnalysisTestSupport.commission(numFactory, 1))));
        historical.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, 100, List.of()));
        Returns curve = new Returns(series, historical, 3, ReturnRepresentation.DECIMAL, EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        Position spot = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.one()),
                Trade.sellAt(2, numFactory.numOf(110), numFactory.one()), new ZeroCostModel(), new ZeroCostModel());
        curve.calculatePosition(spot, 3);
        assertNumEquals(0.1, curve.getValue(2));
        BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100, List.of()));
        zero.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1, 100, List.of()));
        curve.calculatePosition(zero.getPositions().getFirst(), 3);
        assertNumEquals(0.1, curve.getValue(2));
        assertTrue(curve.hasFirstBarReturn());
        assertFalse(curve.hasSeededFirstBarReturn());
    }

    @Test
    public void incrementalCrossLotFeeComponentsShareConstructorEconomics() {
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100, 100);
        // CumulativePnL owns the full constructor-state/closed-state matrix.
        for (boolean seedFirst : new boolean[] { false, true }) {
            boolean closed = seedFirst;
            BaseTradingRecord complete = FuturesAnalysisTestSupport.crossLotFeeRecord(contract, Trade.TradeType.BUY,
                    closed);
            List<Position> positions = closed ? complete.getPositions() : complete.getOpenPositions();
            BaseTradingRecord initial = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
            if (seedFirst) {
                Position first = positions.getFirst();
                initial.operate(first.getEntry().getFills().getFirst());
                if (closed) {
                    initial.operate(first.getExit().getFills().getFirst());
                }
            }
            Returns constructor = new Returns(series, complete, 3, ReturnRepresentation.DECIMAL,
                    EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
            Returns incremental = new Returns(series, initial, 3, ReturnRepresentation.DECIMAL,
                    EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
            assertEquals(2, positions.size());
            for (int lot = seedFirst ? 1 : 0; lot < positions.size(); lot++) {
                incremental.calculatePosition(positions.get(lot), 3);
            }
            assertNumEquals(-0.002, constructor.getValue(1));
            assertNumEquals(-0.002, incremental.getValue(1));
            assertNumEquals(0, incremental.getValue(2));
            assertNumEquals(0, incremental.getValue(3));
        }
    }

    @Test
    public void incrementalFuturesPositionsShareAccountCapitalAndHonorEachCutoff() {
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110, 100, 120);
        BaseTradingRecord complete = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 110, List.of()));
        BaseTradingRecord initial = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        initial.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
        initial.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 110, List.of()));
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1_000, 100, List.of()));
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1_000, 120, List.of()));
        Position second = complete.getPositions().get(1);
        for (ReturnRepresentation representation : List.of(ReturnRepresentation.DECIMAL,
                ReturnRepresentation.PERCENTAGE, ReturnRepresentation.LOG)) {
            Returns curve = new Returns(series, initial, representation);
            curve.calculatePosition(second, 3);
            Num factor = numFactory.numOf(800).dividedBy(numFactory.numOf(600));
            Num expected = representation == ReturnRepresentation.LOG ? factor.log()
                    : representation.toRepresentationFromTotalReturn(factor);
            assertNumEquals(expected, curve.getValue(3));
            assertEquals(new Returns(series, complete, representation).stream().toList(), curve.stream().toList());
            Returns truncated = new Returns(series, initial, representation);
            truncated.calculatePosition(second, 2);
            assertNumEquals(0, truncated.getValue(3));
        }
    }

    @Test
    public void incrementalFuturesMarkRunsUnlockedAndRejectsChangedWindowAtomically() {
        BaseBarSeries source = (BaseBarSeries) FuturesAnalysisTestSupport.series(numFactory, 100, 110, 130);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, lock);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BaseTradingRecord empty = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 100);
        Position incoming = FuturesAnalysisTestSupport.openPosition(contract, 0, 100, 100);
        AtomicBoolean changed = new AtomicBoolean();
        Indicator<Num> mark = new Indicator<Num>() {
            @Override
            public Num getValue(int index) {
                assertEquals("incremental mark must run outside the read lock", 0, lock.getReadHoldCount());
                Num value = series.getBar(index).getClosePrice();
                if (index == 1 && changed.compareAndSet(false, true)) {
                    Bar original = series.getBar(index);
                    series.replaceBar(index,
                            series.barBuilder()
                                    .timePeriod(original.getTimePeriod())
                                    .endTime(original.getEndTime())
                                    .closePrice(120)
                                    .build());
                }
                return value;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }
        };
        Returns curve = new Returns(series, empty, mark, 2, ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        List<Num> before = curve.stream().toList();
        assertThrows(IllegalStateException.class, () -> curve.calculatePosition(incoming, 2));
        assertTrue(changed.get());
        assertEquals(before, curve.stream().toList());
    }

    @Test
    public void incrementalFuturesCarryExposureAndSettlementPastPreWindowPartialExit() {
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 110, 120);
        for (int caseIndex = 0; caseIndex < 3; caseIndex++) {
            Trade entry = Trade.fromFill(
                    FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 2_000, 100, List.of()),
                    RecordedTradeCostModel.INSTANCE);
            List<TradeFill> exits = caseIndex == 2
                    ? List.of(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 2_000, 110, List.of()))
                    : caseIndex == 1 ? List.of(
                            FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 100, List.of()),
                            FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1_000, 120, List.of()))
                            : List.of(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 100,
                                    List.of()));
            Position incoming = new Position(entry,
                    Trade.fromFills(Trade.TradeType.SELL, exits, RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            BaseTradingRecord full = new BaseTradingRecord() {
                @Override
                public FuturesContract getFuturesContract() {
                    return contract;
                }

                @Override
                public Num getInitialCapital() {
                    return numFactory.numOf(500);
                }

                @Override
                public Integer getStartIndex() {
                    return 2;
                }

                @Override
                public Integer getEndIndex() {
                    return 3;
                }

                @Override
                public List<Position> getPositions() {
                    return List.of(incoming);
                }
            };
            BaseTradingRecord empty = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(500))
                    .startIndex(2)
                    .endIndex(3)
                    .build();
            Returns direct = new Returns(series, full, ReturnRepresentation.DECIMAL);
            Returns incremental = new Returns(series, empty, ReturnRepresentation.DECIMAL);
            incremental.calculatePosition(incoming, 3);
            assertNumEquals(caseIndex == 2 ? 0 : 1d / 6d, direct.getValue(3));
            assertNumEquals(direct.getValue(3), incremental.getValue(3), 1e-10);
        }
    }

    @Test
    public void futuresMaterializationRetriesAfterAMarkReadClearsTheWindow() {
        BarSeries source = FuturesAnalysisTestSupport.series(numFactory, 100, 110);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, lock);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of()));
        AtomicBoolean cleared = new AtomicBoolean();
        Indicator<Num> mark = FuturesAnalysisTestSupport.markWithReadAction(series, index -> {
            assertEquals(0, lock.getReadHoldCount());
            if (cleared.compareAndSet(false, true))
                series.clear();
        });
        Returns curve = new Returns(series, record, mark, 2, ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        assertTrue(cleared.get());
        assertEquals(0, curve.getSize());
    }

    @Test
    public void incrementalFuturesMarkFailureReportsChangedWindowWithoutPublishing() {
        BarSeries source = FuturesAnalysisTestSupport.series(numFactory, 100, 110);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, lock);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        AtomicBoolean cleared = new AtomicBoolean();
        Indicator<Num> mark = FuturesAnalysisTestSupport.markWithReadAction(series, index -> {
            assertEquals(0, lock.getReadHoldCount());
            if (cleared.compareAndSet(false, true))
                series.clear();
        });
        Returns curve = new Returns(series, record, mark, 2, ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        List<Num> before = curve.stream().toList();
        Position position = FuturesAnalysisTestSupport.openPosition(contract, 0, 100, 100);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> curve.calculatePosition(position, 1));
        assertTrue(failure.getMessage().contains("changed inside this curve's window"));
        assertTrue(cleared.get());
        assertEquals(before, curve.stream().toList());
    }

    @Test
    public void futuresMarkFailuresKeepTheirIdentityWhenTheWindowIsUnchanged() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        RuntimeException original = new IllegalArgumentException("mark source failed");
        Indicator<Num> mark = FuturesAnalysisTestSupport.markWithReadAction(series, index -> {
            throw original;
        });
        Returns curve = new Returns(series, record, mark, 2, ReturnRepresentation.DECIMAL,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        List<Num> before = curve.stream().toList();
        Position position = FuturesAnalysisTestSupport.openPosition(contract, 0, 100, 100);
        assertSame(original, assertThrows(RuntimeException.class, () -> curve.calculatePosition(position, 1)));
        assertEquals(before, curve.stream().toList());
        record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of()));
        assertSame(original, assertThrows(RuntimeException.class, () -> new Returns(series, record, mark, 2,
                ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET)));
    }

    @Test
    public void crossLotFeeResidualProducesOneExecutedLoss() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100, 100);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        BaseTradingRecord record = FuturesAnalysisTestSupport.crossLotFeeRecord(contract, TradeType.BUY, true);
        Returns returns = new Returns(series, record, ReturnRepresentation.DECIMAL);
        assertTrue(returns.getValue(0).isNaN());
        assertNumEquals(-0.002, returns.getValue(1));
        assertNumEquals(0, returns.getValue(2));
        assertNumEquals(0, returns.getValue(3));
    }

    @Test
    public void realizedHistoricalComponentLossIsExcludedFromNewReturnSamples() {
        BarSeries retained = FuturesAnalysisTestSupport.series(numFactory, 100, 1e16 + 100, 100, 100);
        retained.setMaximumBarCount(2);
        BaseTradingRecord record = FuturesAnalysisTestSupport.roundedPreWindowProfitRecord(numFactory);
        Returns returns = new Returns(retained, record, ReturnRepresentation.DECIMAL, EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(-0.002, returns.getValue(2));
        assertTrue(returns.hasSeededFirstBarReturn());
        assertNumEquals(0, returns.getValue(3));
    }

    @Test
    public void mixedFactoryComponentsPreserveCapitalSampleInEitherOrder() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100);
        for (boolean reverse : new boolean[] { false, true }) {
            Returns returns = new Returns(series,
                    FuturesAnalysisTestSupport.mixedFactoryRecord(numFactory, reverse, true),
                    ReturnRepresentation.DECIMAL);
            assertTrue(returns.getValue(0).isNaN());
            // Absolute index zero retains the existing capital-based next sample.
            assertNumEquals(0.002, returns.getValue(1));
        }
    }

    @Test
    public void fixedCapitalRetainedReturnIgnoresZeroContribution() {
        NumFactory factory = DecimalNumFactory.getInstance(new MathContext(2, RoundingMode.HALF_UP));
        BarSeries series = FuturesAnalysisTestSupport.series(factory, 100, 100, 100, 100);
        series.setMaximumBarCount(2);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(factory)
                .toBuilder()
                .contractSize(factory.one())
                .build();
        BaseTradingRecord historical = FuturesAnalysisTestSupport.fundedRecord(contract, factory, 150);
        historical.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 100,
                List.of(FuturesAnalysisTestSupport.commission(factory, -7.2))));
        historical.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, 100, List.of()));
        Returns curve = new Returns(series, historical, 3, ReturnRepresentation.DECIMAL, EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        Position spot = new Position(Trade.buyAt(0, factory.hundred(), factory.one()),
                Trade.sellAt(2, factory.numOf(110), factory.one()), new ZeroCostModel(), new ZeroCostModel());
        curve.calculatePosition(spot, 3);
        assertNumEquals(0.1, curve.getValue(2));
        List<Num> beforeZero = curve.stream().toList();
        BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, factory, 150);
        zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100, List.of()));
        zero.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1, 100, List.of()));
        curve.calculatePosition(zero.getPositions().getFirst(), 3);
        // Entering and current normalized native equity are identical: native factor 1.
        assertNumEquals(0.1, curve.getValue(2));
        assertEquals(beforeZero, curve.stream().toList());
    }

    @Test
    public void normalizedEquityPeriodsStayInvariantAcrossOriginsOrdersAndRepresentations() {
        // This owner covers the complete head/period/representation cross-product.
        // CashFlow has two representative cash orderings with its separate additive
        // equation.
        List<NumFactory> precisions = numFactory instanceof DecimalNumFactory
                ? List.of(DecimalNumFactory.getInstance(new MathContext(2, RoundingMode.HALF_UP)),
                        DecimalNumFactory.getInstance(new MathContext(3, RoundingMode.HALF_UP)), numFactory)
                : List.of(numFactory);
        for (NumFactory factory : precisions) {
            for (boolean retained : new boolean[] { false, true }) {
                for (EquityCurveMode mode : EquityCurveMode.values()) {
                    for (ReturnRepresentation representation : ReturnRepresentation.values()) {
                        for (int origin = 0; origin < 4; origin++) {
                            for (int[] order : List.of(new int[] { 0, 1, 2 }, new int[] { 0, 2, 1 },
                                    new int[] { 1, 0, 2 }, new int[] { 1, 2, 0 }, new int[] { 2, 0, 1 },
                                    new int[] { 2, 1, 0 })) {
                                BarSeries series = FuturesAnalysisTestSupport.series(factory, 100, 100, 100, 100);
                                if (retained)
                                    series.setMaximumBarCount(2);
                                FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(factory)
                                        .toBuilder()
                                        .contractSize(factory.one())
                                        .build();
                                BaseTradingRecord historical = FuturesAnalysisTestSupport.fundedRecord(contract,
                                        factory, 150);
                                historical.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1,
                                        100, List.of(FuturesAnalysisTestSupport.commission(factory, -7.2))));
                                historical.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1,
                                        100, List.of()));
                                Position nativeLot = historical.getPositions().getFirst();
                                Position spot = new Position(Trade.buyAt(0, factory.hundred(), factory.one()),
                                        Trade.sellAt(2, factory.numOf(110), factory.one()), new ZeroCostModel(),
                                        new ZeroCostModel());
                                BaseTradingRecord initial = origin == 0 ? new BaseTradingRecord()
                                        : origin == 1 ? new BaseTradingRecord(spot)
                                                : origin == 2
                                                        ? FuturesAnalysisTestSupport.fundedRecord(contract, factory,
                                                                150)
                                                        : historical;
                                Returns curve = new Returns(series, initial, 3, representation, mode,
                                        OpenPositionHandling.MARK_TO_MARKET);
                                BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, factory,
                                        150);
                                // Fallback notional 200 differs from the historical lot's
                                // 100. The configured capital remains 150 when supplied.
                                zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 2, 100,
                                        List.of()));
                                zero.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 2, 100,
                                        List.of()));
                                List<Position> updates = List.of(nativeLot, spot, zero.getPositions().getFirst());
                                java.math.BigDecimal equity = java.math.BigDecimal.ONE
                                        .add(new java.math.BigDecimal("7.2")
                                                .divide(java.math.BigDecimal.valueOf(origin < 2 ? 100 : 150)));
                                boolean nativeSeen = origin == 3;
                                boolean spotSeen = origin == 1;
                                boolean zeroSeen = false;
                                boolean reportedHead = retained && spotSeen;
                                boolean spotDefinesPeriod = reportedHead;
                                assertNoExecutedNativeUpdatePreservesState(curve, contract);
                                for (int next : order) {
                                    if ((next == 0 && origin == 3) || (next == 1 && origin == 1))
                                        continue;
                                    List<Num> before = curve.getValues();
                                    curve.calculatePosition(updates.get(next), 3);
                                    if (next == 0) {
                                        nativeSeen = true;
                                    } else if (next == 1) {
                                        if (retained && !reportedHead)
                                            spotDefinesPeriod = true;
                                        spotSeen = true;
                                        reportedHead |= retained;
                                    } else {
                                        zeroSeen = true;
                                        reportedHead |= retained;
                                        if (spotSeen)
                                            assertEquals(before, curve.getValues());
                                    }
                                    Num nativeFactor = retained && !spotDefinesPeriod && zeroSeen && nativeSeen
                                            ? factory.numOf(equity)
                                            : factory.one();
                                    Num spotFactor = spotSeen ? factory.numOf(1.1) : factory.one();
                                    // Equal entering/current native equity has factor 1.
                                    // A reported historical capital seed is deliberately
                                    // cumulative; it stays distinct from a spot period.
                                    Num expected = representation == ReturnRepresentation.LOG
                                            ? nativeFactor.log().plus(spotFactor.log())
                                            : representation.toRepresentationFromTotalReturn(
                                                    nativeFactor.multipliedBy(spotFactor));
                                    if (!retained || reportedHead)
                                        assertNumEquals(expected, curve.getValue(2));
                                    assertNumEquals(
                                            representation == ReturnRepresentation.MULTIPLICATIVE ? factory.one()
                                                    : factory.zero(),
                                            curve.getValue(3));
                                    assertNoExecutedNativeUpdatePreservesState(curve, contract);
                                }
                                // Extend every original prefix with a second native lot
                                // and a distinct second spot lot in both interleavings.
                                BaseTradingRecord secondNative = FuturesAnalysisTestSupport.fundedRecord(contract,
                                        factory, 150);
                                secondNative.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1,
                                        100, List.of(FuturesAnalysisTestSupport.commission(factory, -45))));
                                Position secondSpot = new Position(Trade.buyAt(0, factory.hundred(), factory.one()),
                                        Trade.sellAt(2, factory.numOf(110), factory.one()), new ZeroCostModel(),
                                        new ZeroCostModel());
                                java.math.BigDecimal secondContribution = new java.math.BigDecimal("45")
                                        .divide(java.math.BigDecimal.valueOf(origin < 2 ? 100 : 150));
                                java.math.BigDecimal acceptedSecond = java.math.BigDecimal.ZERO;
                                Num retainedSpot = factory.numOf(1.1);
                                Num retainedLogSpot = retainedSpot.log();
                                List<Position> subsequent = order[0] == 1
                                        ? List.of(secondSpot, secondNative.getOpenPositions().getFirst())
                                        : List.of(secondNative.getOpenPositions().getFirst(), secondSpot);
                                for (Position next : subsequent) {
                                    curve.calculatePosition(next, 3);
                                    if (next == secondSpot) {
                                        retainedSpot = retainedSpot.multipliedBy(factory.numOf(1.1));
                                        retainedLogSpot = retainedLogSpot.plus(factory.numOf(1.1).log());
                                    } else {
                                        acceptedSecond = secondContribution;
                                    }
                                    // Economic equity is 1 + first rebate/capital +
                                    // second rebate/capital. A period compares it with
                                    // entering equity; a retained capital seed compares
                                    // it with 1. Compose the legacy rounded spot group once.
                                    java.math.BigDecimal previous = retained && !spotDefinesPeriod
                                            ? java.math.BigDecimal.ONE
                                            : equity;
                                    MathContext context = factory.one() instanceof DecimalNum decimal
                                            ? decimal.getMathContext()
                                            : MathContext.DECIMAL128;
                                    Num nativeFactor = factory
                                            .numOf(equity.add(acceptedSecond).divide(previous, context));
                                    Num expected = representation == ReturnRepresentation.LOG
                                            ? nativeFactor.log().plus(retainedLogSpot)
                                            : representation.toRepresentationFromTotalReturn(
                                                    nativeFactor.multipliedBy(retainedSpot));
                                    assertNumEquals(expected, curve.getValue(2));
                                    List<Num> published = curve.getValues();
                                    List<Num> raw = curve.getRawValues();
                                    int size = curve.getSize();
                                    boolean capitalSeed = curve.hasSeededFirstBarReturn();
                                    curve.calculatePosition(zero.getPositions().getFirst(), 3);
                                    assertEquals(published, curve.getValues());
                                    assertEquals(raw, curve.getRawValues());
                                    assertEquals(size, curve.getSize());
                                    assertEquals(capitalSeed, curve.hasSeededFirstBarReturn());
                                    assertNoExecutedNativeUpdatePreservesState(curve, contract);
                                }
                                List<Num> frozen = curve.getValues();
                                curve.calculatePosition(zero.getPositions().getFirst(), 3);
                                assertEquals(frozen, curve.getValues());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void multipleSpotUpdatesPublishRetainedCompositionImmediately() {
        assertMultipleSpotPublication(true);
    }

    @Test
    public void multipleSpotUpdatesRemainUnchangedAfterZeroNativeUpdate() {
        assertMultipleSpotPublication(false);
    }

    private void assertMultipleSpotPublication(boolean immediate) {
        // This fixed MC2 fixture repeats under the existing runner labels.
        NumFactory factory = DecimalNumFactory.getInstance(new MathContext(2, RoundingMode.HALF_UP));
        BarSeries series = FuturesAnalysisTestSupport.series(factory, 100, 100, 100, 100);
        series.setMaximumBarCount(2);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(factory)
                .toBuilder()
                .contractSize(factory.one())
                .build();
        BaseTradingRecord empty = FuturesAnalysisTestSupport.fundedRecord(contract, factory, 150);
        Returns curve = new Returns(series, empty, 3, ReturnRepresentation.MULTIPLICATIVE, EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        BaseTradingRecord nativeLot = FuturesAnalysisTestSupport.fundedRecord(contract, factory, 150);
        nativeLot.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100,
                List.of(FuturesAnalysisTestSupport.commission(factory, -45))));
        curve.calculatePosition(nativeLot.getOpenPositions().getFirst(), 3);
        assertNumEquals(1.3, curve.getValue(2));
        for (int lot = 0; lot < 2; lot++) {
            Position spot = new Position(Trade.buyAt(0, factory.hundred(), factory.one()),
                    Trade.sellAt(2, factory.numOf(110), factory.one()), new ZeroCostModel(), new ZeroCostModel());
            curve.calculatePosition(spot, 3);
        }
        if (immediate) {
            // Native equity 1+45/150, composed once with the retained spot
            // product round(1.1*1.1)=1.2: round(1.3*1.2)=1.6.
            assertNumEquals(1.6, curve.getValue(2));
        } else {
            List<Num> before = curve.getValues();
            BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, factory, 150);
            zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100, List.of()));
            zero.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1, 100, List.of()));
            curve.calculatePosition(zero.getPositions().getFirst(), 3);
            assertEquals(before, curve.getValues());
        }
    }

    @Test
    public void spotOnlyPublicationKeepsLegacyCompositionAcrossNativeZeroUpdates() {
        List<NumFactory> precisions = numFactory instanceof DecimalNumFactory
                ? List.of(DecimalNumFactory.getInstance(new MathContext(2, RoundingMode.HALF_UP)),
                        DecimalNumFactory.getInstance(new MathContext(3, RoundingMode.HALF_UP)), numFactory)
                : List.of(numFactory);
        for (NumFactory factory : precisions) {
            for (boolean retained : new boolean[] { false, true }) {
                for (ReturnRepresentation representation : ReturnRepresentation.values()) {
                    BarSeries series = FuturesAnalysisTestSupport.series(factory, 100, 100, 100, 100);
                    if (retained)
                        series.setMaximumBarCount(2);
                    Returns incremental = new Returns(series, new BaseTradingRecord(), 3, representation,
                            EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);
                    List<Position> positions = new ArrayList<>();
                    Num factor = factory.one();
                    Num log = factory.zero();
                    for (int price : new int[] { 110, 110, 120 }) {
                        Position position = new Position(Trade.buyAt(0, factory.hundred(), factory.one()),
                                Trade.sellAt(2, factory.numOf(price), factory.one()), new ZeroCostModel(),
                                new ZeroCostModel());
                        positions.add(position);
                        incremental.calculatePosition(position, 3);
                        Num spot = factory.numOf(price).dividedBy(factory.hundred());
                        // Compatibility means retaining the established rounding at
                        // each spot-only factor multiplication, not exact compounding.
                        factor = factor.multipliedBy(spot);
                        log = log.plus(spot.log());
                        Num expected = representation == ReturnRepresentation.LOG ? log
                                : representation.toRepresentationFromTotalReturn(factor);
                        assertNumEquals(expected, incremental.getValue(2));
                        Returns direct = new Returns(series, new BaseTradingRecord(positions), 3, representation,
                                EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);
                        assertEquals(direct.getValues(), incremental.getValues());
                    }
                    FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(factory)
                            .toBuilder()
                            .contractSize(factory.one())
                            .build();
                    BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, factory, 150);
                    zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 2, 100, List.of()));
                    zero.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 2, 100, List.of()));
                    List<Num> before = incremental.getValues();
                    incremental.calculatePosition(zero.getPositions().getFirst(), 3);
                    assertEquals(before, incremental.getValues());
                }
            }
        }
    }

    @Test
    public void deferredNativeUpdatePreservesEstablishedHeadValues() {
        assertDeferredNativeHeadPreserved(0);
    }

    @Test
    public void deferredNativeUpdatePreservesEstablishedHeadEligibility() {
        assertDeferredNativeHeadPreserved(1);
    }

    @Test
    public void deferredNativeUpdatePreservesEstablishedHeadSize() {
        assertDeferredNativeHeadPreserved(2);
    }

    private void assertDeferredNativeHeadPreserved(int metric) {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100, 100);
        series.setMaximumBarCount(2);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        Returns curve = new Returns(series, new BaseTradingRecord(), 3, ReturnRepresentation.DECIMAL,
                EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);
        Trade entry = Trade.fromFill(
                FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100,
                        List.of(FuturesAnalysisTestSupport.commission(numFactory, -10))),
                RecordedTradeCostModel.INSTANCE);
        Position active = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        curve.calculatePosition(active, 3);
        assertNumEquals(0.1, curve.getValue(2));
        assertNumEquals(0, curve.getValue(3));
        assertTrue(curve.hasFirstBarReturn());
        assertEquals(2, curve.getSize());
        List<Num> before = curve.getValues();
        Position deferred = FuturesAnalysisTestSupport.openPosition(contract, -1, 1, 100);
        assertTrue(deferred.isOpened());
        assertEquals(-1, deferred.getEntry().getIndex());
        assertFalse(deferred.getEntry().getTime() == null);
        assertTrue(deferred.getEntry().getFills().getFirst().hasRecordedFees());
        assertTrue(deferred.getEntry().getFills().getFirst().fees().isEmpty());
        assertNumEquals(0,
                deferred.getProfitComponents(3, numFactory.hundred()).stream().reduce(numFactory.zero(), Num::plus));
        curve.calculatePosition(deferred, 3);
        switch (metric) {
        case 0 -> assertEquals(before, curve.getValues());
        case 1 -> assertTrue(curve.hasFirstBarReturn());
        case 2 -> assertEquals(2, curve.getSize());
        default -> throw new AssertionError("Unknown return-view getter");
        }
    }

    private void assertNoExecutedNativeUpdatePreservesState(Returns curve, FuturesContract contract) {
        List<Num> published = curve.getValues();
        List<Num> raw = curve.getRawValues();
        int size = curve.getSize();
        boolean firstBar = curve.hasFirstBarReturn();
        boolean seed = curve.hasSeededFirstBarReturn();
        // Deferred fills and executions beyond this curve's cutoff contribute
        // nothing. In particular, neither can erase an accepted head or seed.
        for (int entryIndex : new int[] { -1, curve.getEndIndex() + 1 }) {
            curve.calculatePosition(FuturesAnalysisTestSupport.openPosition(contract, entryIndex, 1, 100),
                    curve.getEndIndex());
            assertEquals(published, curve.getValues());
            assertEquals(raw, curve.getRawValues());
            assertEquals(size, curve.getSize());
            assertEquals(firstBar, curve.hasFirstBarReturn());
            assertEquals(seed, curve.hasSeededFirstBarReturn());
        }
    }

    @Test
    public void noExecutedNativeUpdatesPreserveEveryConstructorAndNextExecutedUpdate() {
        for (boolean retained : new boolean[] { false, true }) {
            for (ReturnRepresentation representation : ReturnRepresentation.values()) {
                for (EquityCurveMode mode : EquityCurveMode.values()) {
                    for (int initialState = 0; initialState < 3; initialState++) {
                        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100, 100);
                        if (retained)
                            series.setMaximumBarCount(2);
                        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                                .toBuilder()
                                .contractSize(numFactory.one())
                                .build();
                        // Empty, wholly deferred, and already executed construction
                        // are distinct capital states. Funded records always retain150.
                        Position position = initialState == 0 ? new Position()
                                : FuturesAnalysisTestSupport.openPosition(contract, -1, 1, 100);
                        BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 150);
                        if (initialState == 1) {
                            record = new AlternateFuturesRecord(contract, numFactory.numOf(150), List.of(), position);
                        } else if (initialState == 2) {
                            record.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100,
                                    List.of(FuturesAnalysisTestSupport.commission(numFactory, -10))));
                            position = record.getOpenPositions().getFirst();
                        }
                        for (int constructor = 0; constructor < 12; constructor++) {
                            if (initialState == 0 && constructor >= 1 && constructor <= 4) {
                                // Position overloads require an entry; empty records
                                // are the supported empty-construction state.
                                int emptyConstructor = constructor;
                                TradingRecord initialRecord = record;
                                Position empty = position;
                                assertThrows(IllegalArgumentException.class, () -> nativeReturnsConstructor(series,
                                        initialRecord, empty, representation, mode, emptyConstructor));
                                continue;
                            }
                            Returns curve = nativeReturnsConstructor(series, record, position, representation, mode,
                                    constructor);
                            Returns control = nativeReturnsConstructor(series, record, position, representation, mode,
                                    constructor);
                            assertNoExecutedNativeUpdatePreservesState(curve, contract);
                            assertRejectedNativeTransitionPreservesState(curve, contract);
                            for (double rebate : new double[] { 0, 20 }) {
                                Trade incoming = Trade
                                        .fromFill(
                                                FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY,
                                                        rebate == 0 ? 2 : 1, 100, List.of(FuturesAnalysisTestSupport
                                                                .commission(numFactory, -rebate))),
                                                RecordedTradeCostModel.INSTANCE);
                                Position next = new Position(incoming, RecordedTradeCostModel.INSTANCE,
                                        new ZeroCostModel());
                                // A zero-PnL first execution still has genuine positive
                                // notional; a later rebate must not inherit deferred0.
                                curve.calculatePosition(next, 3);
                                control.calculatePosition(next, 3);
                                assertEquals(control.getValues(), curve.getValues());
                                assertEquals(control.getRawValues(), curve.getRawValues());
                                assertEquals(control.getSize(), curve.getSize());
                                assertEquals(control.hasFirstBarReturn(), curve.hasFirstBarReturn());
                                assertEquals(control.hasSeededFirstBarReturn(), curve.hasSeededFirstBarReturn());
                                if (constructor == 4 || constructor == 5) {
                                    int capital = constructor == 4 ? 100 : 150;
                                    MathContext context = numFactory.one() instanceof DecimalNum decimal
                                            ? decimal.getMathContext()
                                            : MathContext.DECIMAL128;
                                    // Compare economic equity/capital before Num rounding,
                                    // as required by the retained publication contract.
                                    Num factor = numFactory.numOf(BigDecimal.valueOf(capital)
                                            .add(BigDecimal.valueOf((initialState == 2 ? 10 : 0) + rebate))
                                            .divide(BigDecimal.valueOf(capital), context));
                                    Num expected = representation == ReturnRepresentation.LOG ? factor.log()
                                            : representation.toRepresentationFromTotalReturn(factor);
                                    assertNumEquals(expected, curve.getValue(2));
                                }
                                assertNoExecutedNativeUpdatePreservesState(curve, contract);
                            }
                        }
                    }
                }
            }
        }
    }

    private void assertRejectedNativeTransitionPreservesState(Returns curve, FuturesContract contract) {
        List<Num> published = curve.getValues();
        List<Num> raw = curve.getRawValues();
        int size = curve.getSize();
        boolean head = curve.hasFirstBarReturn();
        boolean seed = curve.hasSeededFirstBarReturn();
        RuntimeException failure = new IllegalArgumentException("holding source failed during accumulation");
        AtomicBoolean stagedFailure = new AtomicBoolean();
        Trade entry = Trade.fromFill(
                FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100,
                        List.of(FuturesAnalysisTestSupport.commission(numFactory, -30))),
                RecordedTradeCostModel.INSTANCE);
        ZeroCostModel holding = new ZeroCostModel() {
            @Override
            public Num calculate(Position position, int index) {
                // Prepricing at finalIndex3 succeeds. Failure at2 belongs to
                // accumulation inside the staged native publication.
                if (index == 2) {
                    stagedFailure.set(true);
                    throw failure;
                }
                return numFactory.zero();
            }
        };
        Position rejected = new Position(entry, RecordedTradeCostModel.INSTANCE, holding);
        assertSame(failure, assertThrows(RuntimeException.class, () -> curve.calculatePosition(rejected, 3)));
        assertTrue(stagedFailure.get());
        assertEquals(published, curve.getValues());
        assertEquals(raw, curve.getRawValues());
        assertEquals(size, curve.getSize());
        assertEquals(head, curve.hasFirstBarReturn());
        assertEquals(seed, curve.hasSeededFirstBarReturn());
        assertNoExecutedNativeUpdatePreservesState(curve, contract);
    }

    private Returns nativeReturnsConstructor(BarSeries series, TradingRecord record, Position position,
            ReturnRepresentation representation, EquityCurveMode mode, int constructor) {
        return switch (constructor) {
        case 0 -> new Returns(series, record, 3, representation, mode, OpenPositionHandling.MARK_TO_MARKET);
        case 1 -> new Returns(series, position);
        case 2 -> new Returns(series, position, mode);
        case 3 -> new Returns(series, position, representation);
        case 4 -> new Returns(series, position, representation, mode);
        case 5 -> new Returns(series, record, representation, mode);
        case 6 -> new Returns(series, record);
        case 7 -> new Returns(series, record, mode);
        case 8 -> new Returns(series, record, representation);
        case 9 -> new Returns(series, record, representation, OpenPositionHandling.MARK_TO_MARKET);
        case 10 -> new Returns(series, record, representation, mode, OpenPositionHandling.MARK_TO_MARKET);
        case 11 -> new Returns(series, record,
                new MockIndicator(series, series.getBeginIndex(), numFactory.hundred(), numFactory.hundred(),
                        numFactory.hundred(), numFactory.hundred()),
                3, representation, mode, OpenPositionHandling.MARK_TO_MARKET);
        default -> throw new AssertionError("Unknown constructor");
        };
    }

    @Test
    public void deferredConstructorAcceptsFirstExecutedRebate() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        Position deferred = FuturesAnalysisTestSupport.openPosition(contract, -1, 1, 100);
        assertTrue(deferred.getEntry().getTime() != null);
        assertTrue(deferred.getEntry().getFills().getFirst().hasRecordedFees());
        assertTrue(deferred.getEntry().getFills().getFirst().fees().isEmpty());
        Returns curve = new Returns(series, deferred, ReturnRepresentation.DECIMAL, EquityCurveMode.REALIZED);
        assertFalse(curve.hasFirstBarReturn());
        assertFalse(curve.hasSeededFirstBarReturn());
        assertTrue(curve.getValue(0).isNaN());
        assertNumEquals(0, curve.getValue(1));
        assertNumEquals(0, curve.getValue(2));
        assertEquals(2, curve.getSize());
        Trade executed = Trade.fromFill(
                FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1, 100,
                        List.of(FuturesAnalysisTestSupport.commission(numFactory, -10))),
                RecordedTradeCostModel.INSTANCE);
        Position active = new Position(executed, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        assertNumEquals(100, FuturesPerformanceSupport.entryNotional(active));
        assertNumEquals(10,
                active.getProfitComponents(2, numFactory.hundred()).stream().reduce(numFactory.zero(), Num::plus));
        curve.calculatePosition(active, 2);
        assertTrue(curve.getValue(0).isNaN());
        assertNumEquals(0.1, curve.getValue(1));
        assertNumEquals(0, curve.getValue(2));
        assertEquals(2, curve.getSize());
        assertFalse(curve.hasFirstBarReturn());
        assertFalse(curve.hasSeededFirstBarReturn());
    }

}
