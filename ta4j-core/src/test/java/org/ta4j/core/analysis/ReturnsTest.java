/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.math.MathContext;
import java.math.RoundingMode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;
import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.Trade;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.*;

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
    public void returnsPrecision() {
        assumeTrue(numFactory instanceof DoubleNumFactory);

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
        assertNumEquals(0, returns.getValue(1));
        assertNumEquals(0, returns.getValue(2));
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

}
