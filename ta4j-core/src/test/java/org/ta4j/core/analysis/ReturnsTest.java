package org.ta4j.core.analysis;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class ReturnsTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ReturnsTest(Function<Number, Num> numFunction) {
        super(DoubleNum::valueOf);
    }

    @Test
    public void returnSize() {
        for (Returns.ReturnType type : Returns.ReturnType.values()) {
            // No return at index 0
            TimeSeries sampleTimeSeries = new MockTimeSeries(numFunction, 1d, 2d, 3d, 4d, 5d);
            Returns returns = new Returns(sampleTimeSeries, new BaseTradingRecord(), type);
            assertEquals(4, returns.getSize());
        }
    }

    @Test
    public void singleReturnTradeArith() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(numFunction,1d, 2d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, sampleTimeSeries), Order.sellAt(1, sampleTimeSeries));
        Returns return1 = new Returns(sampleTimeSeries, tradingRecord, Returns.ReturnType.ARITHMETIC);
        assertNumEquals(NaN.NaN, return1.getValue(0));
        assertNumEquals(1.0, return1.getValue(1));
    }

    @Test
    public void returnsWithSellAndBuyOrders() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(numFunction,2, 1, 3, 5, 6, 3, 20);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0, sampleTimeSeries), Order.sellAt(1, sampleTimeSeries),
                Order.buyAt(3, sampleTimeSeries), Order.sellAt(4, sampleTimeSeries),
                Order.sellAt(5, sampleTimeSeries), Order.buyAt(6, sampleTimeSeries));

        Returns strategyReturns = new Returns(sampleTimeSeries, tradingRecord, Returns.ReturnType.ARITHMETIC);

        assertNumEquals(NaN.NaN, strategyReturns.getValue(0));
        assertNumEquals(-0.5, strategyReturns.getValue(1));
        assertNumEquals(0, strategyReturns.getValue(2));
        assertNumEquals(0, strategyReturns.getValue(3));
        assertNumEquals(1d/5, strategyReturns.getValue(4));
        assertNumEquals(0, strategyReturns.getValue(5));
        assertNumEquals(1 - (20d/3), strategyReturns.getValue(6));
    }

    @Test
    public void returnsWithGaps() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(numFunction,1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d, 11d, 12d);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.sellAt(2, sampleTimeSeries), Order.buyAt(5, sampleTimeSeries),
                Order.buyAt(8, sampleTimeSeries), Order.sellAt(10, sampleTimeSeries));

        Returns returns = new Returns(sampleTimeSeries, tradingRecord, Returns.ReturnType.LOG);

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
    public void returnsWithNoTrades() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(numFunction,3d, 2d, 5d, 4d, 7d, 6d, 7d, 8d, 5d, 6d);
        Returns returns = new Returns(sampleTimeSeries, new BaseTradingRecord(), Returns.ReturnType.LOG);
        assertNumEquals(NaN.NaN, returns.getValue(0));
        assertNumEquals(0, returns.getValue(4));
        assertNumEquals(0, returns.getValue(7));
        assertNumEquals(0, returns.getValue(9));
    }
}
