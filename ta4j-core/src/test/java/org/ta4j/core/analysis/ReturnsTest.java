/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.analysis;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

public class ReturnsTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ReturnsTest(Function<Number, Num> numFunction) {
        super(DoubleNum::valueOf);
    }

    @Test
    public void returnSize() {
        for (Returns.ReturnType type : Returns.ReturnType.values()) {
            // No return at index 0
            BarSeries sampleBarSeries = new MockBarSeries(numFunction, 1d, 2d, 3d, 4d, 5d);
            Returns returns = new Returns(sampleBarSeries, new BaseTradingRecord(), type);
            assertEquals(4, returns.getSize());
        }
    }

    @Test
    public void singleReturnPositionArith() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 1d, 2d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries),
                Trade.sellAt(1, sampleBarSeries));
        Returns return1 = new Returns(sampleBarSeries, tradingRecord, Returns.ReturnType.ARITHMETIC);
        assertNumEquals(NaN.NaN, return1.getValue(0));
        assertNumEquals(1.0, return1.getValue(1));
    }

    @Test
    public void returnsWithSellAndBuyTrades() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 2, 1, 3, 5, 6, 3, 20);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries),
                Trade.sellAt(1, sampleBarSeries), Trade.buyAt(3, sampleBarSeries), Trade.sellAt(4, sampleBarSeries),
                Trade.sellAt(5, sampleBarSeries), Trade.buyAt(6, sampleBarSeries));

        Returns strategyReturns = new Returns(sampleBarSeries, tradingRecord, Returns.ReturnType.ARITHMETIC);

        assertNumEquals(NaN.NaN, strategyReturns.getValue(0));
        assertNumEquals(-0.5, strategyReturns.getValue(1));
        assertNumEquals(0, strategyReturns.getValue(2));
        assertNumEquals(0, strategyReturns.getValue(3));
        assertNumEquals(1d / 5, strategyReturns.getValue(4));
        assertNumEquals(0, strategyReturns.getValue(5));
        assertNumEquals(1 - (20d / 3), strategyReturns.getValue(6));
    }

    @Test
    public void returnsWithGaps() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d, 11d, 12d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(2, sampleBarSeries),
                Trade.buyAt(5, sampleBarSeries), Trade.buyAt(8, sampleBarSeries), Trade.sellAt(10, sampleBarSeries));

        Returns returns = new Returns(sampleBarSeries, tradingRecord, Returns.ReturnType.LOG);

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
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 3d, 2d, 5d, 4d, 7d, 6d, 7d, 8d, 5d, 6d);
        Returns returns = new Returns(sampleBarSeries, new BaseTradingRecord(), Returns.ReturnType.LOG);
        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(0, returns.getValue(4));
        assertNumEquals(0, returns.getValue(7));
        assertNumEquals(0, returns.getValue(9));
    }

    @Test
    public void returnsPrecision() {
        BarSeries doubleSeries = new MockBarSeries(numFunction, 1.2d, 1.1d);
        BarSeries precisionSeries = new MockBarSeries(DecimalNum::valueOf, 1.2d, 1.1d);

        TradingRecord fullRecordDouble = new BaseTradingRecord();
        fullRecordDouble.enter(doubleSeries.getBeginIndex(), doubleSeries.getBar(0).getClosePrice(),
                doubleSeries.one());
        fullRecordDouble.exit(doubleSeries.getEndIndex(), doubleSeries.getBar(1).getClosePrice(), doubleSeries.one());

        TradingRecord fullRecordPrecision = new BaseTradingRecord();
        fullRecordPrecision.enter(precisionSeries.getBeginIndex(), precisionSeries.getBar(0).getClosePrice(),
                precisionSeries.one());
        fullRecordPrecision.exit(precisionSeries.getEndIndex(), precisionSeries.getBar(1).getClosePrice(),
                precisionSeries.one());

        // Return calculation DoubleNum vs PrecisionNum
        Num arithDouble = new Returns(doubleSeries, fullRecordDouble, Returns.ReturnType.ARITHMETIC).getValue(1);
        Num arithPrecision = new Returns(precisionSeries, fullRecordPrecision, Returns.ReturnType.ARITHMETIC)
                .getValue(1);
        Num logDouble = new Returns(doubleSeries, fullRecordDouble, Returns.ReturnType.LOG).getValue(1);
        Num logPrecision = new Returns(precisionSeries, fullRecordPrecision, Returns.ReturnType.LOG).getValue(1);

        assertNumEquals(arithDouble, DoubleNum.valueOf(-0.08333333333333326));
        assertNumEquals(arithPrecision,
                DecimalNum.valueOf(1.1).dividedBy(DecimalNum.valueOf(1.2)).minus(DecimalNum.valueOf(1)));

        assertNumEquals(logDouble, DoubleNum.valueOf(-0.08701137698962969));
        assertNumEquals(logPrecision, DecimalNum.valueOf("-0.087011376989629766167765901873746"));
    }
}
