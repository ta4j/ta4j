/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade;
import org.ta4j.core.cost.LinearTransactionCostModel;
import org.ta4j.core.cost.ZeroCostModel;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.num.NaN.NaN;

public class BreakEvenIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries series;
    private BaseTradingRecord tradingRecord;
    private static final double TRADING_FEE = 0.0026;
    private BreakEvenIndicator breakEvenIndicator;

    public BreakEvenIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        series = new MockBarSeries(numFunction);

        tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, new LinearTransactionCostModel(TRADING_FEE),
                new ZeroCostModel());
        breakEvenIndicator = new BreakEvenIndicator(series, tradingRecord, TRADING_FEE);
    }

    @Test
    public void returnsNaNIfNoTradesAreMade() {
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            assertEquals(NaN, breakEvenIndicator.getValue(index));
        }
    }

    @Test
    public void calculateTheBreakEvenRegardingBuyPriceIncludingTransactionCost() {
        Num price = series.getBar(1).getClosePrice();
        tradingRecord.enter(1, price, NaN);
        Num calculatedBreakEven = calculateBreakEven(price);

        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            if (index == series.getBeginIndex()) {
                assertEquals(NaN, breakEvenIndicator.getValue(index));
            } else {
                assertEquals(calculatedBreakEven, breakEvenIndicator.getValue(index));
            }
        }
    }

    @Test
    public void calculatesBreakEvenOnlyForOpenTradesOnly() {
        Num price = series.getBar(1).getClosePrice();
        tradingRecord.enter(1, price, NaN);
        tradingRecord.exit(3);
        Num calculatedBreakEven = calculateBreakEven(price);

        for (int index = series.getBeginIndex(); index <= series.getBeginIndex() + 2; index++) {
            if (index == series.getBeginIndex()) {
                assertEquals(NaN, breakEvenIndicator.getValue(index));
            } else {
                assertEquals(calculatedBreakEven, breakEvenIndicator.getValue(index));
            }
        }

        for (int index = series.getBeginIndex() + 3; index <= series.getEndIndex(); index++) {
            assertEquals(NaN, breakEvenIndicator.getValue(index));
        }
    }

    @Test
    public void calculatesBreakEvenForTheLastOpenTrade() {
        Num price = series.getBar(1).getClosePrice();
        Num calculatedBreakEven1 = calculateBreakEven(price);
        tradingRecord.enter(1, price, NaN);
        tradingRecord.exit(3);

        Num price2 = series.getBar(6).getClosePrice();
        Num calculatedBreakEven2 = calculateBreakEven(price2);
        tradingRecord.enter(6, price, NaN);
        tradingRecord.exit(8);

        for (int index = series.getBeginIndex(); index <= series.getBeginIndex() + 2; index++) {
            if (index == series.getBeginIndex()) {
                assertEquals(NaN, breakEvenIndicator.getValue(index));
            } else {
                assertEquals(calculatedBreakEven1, breakEvenIndicator.getValue(index));
            }
        }

        for (int index = series.getBeginIndex() + 3; index <= series.getBeginIndex() + 5; index++) {
            assertEquals(NaN, breakEvenIndicator.getValue(index));
        }

        for (int index = series.getBeginIndex() + 6; index <= series.getBeginIndex() + 7; index++) {
            assertEquals(calculatedBreakEven2, breakEvenIndicator.getValue(index));
        }

        for (int index = series.getBeginIndex() + 8; index <= series.getEndIndex(); index++) {
            assertEquals(NaN, breakEvenIndicator.getValue(index));
        }

    }

    private Num calculateBreakEven(Num price) {
        // Wanted: p_buy + fee% <= p_sell - fee%
        // --> (p_buy * (1+fee)) <= p_sell * (1-fee)
        // --> (p_buy * (1+fee)) / (1-fee) <= p_sell == break_even
        Num buyFeeFactor = series.numOf(1).plus(series.numOf(TRADING_FEE));
        Num sellFeeFactor = series.numOf(1).minus(series.numOf(TRADING_FEE));
        return price.multipliedBy(buyFeeFactor).dividedBy(sellFeeFactor);
    }
}
