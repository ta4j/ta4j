/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static org.ta4j.core.TestUtils.assertNumEquals;

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

        assertSame(sampleBarSeries, returns.getBarSeries());
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
}
