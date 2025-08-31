/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.criteria.drawdown;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DrawdownTest extends AbstractIndicatorTest<org.ta4j.core.Indicator<Num>, Num> {

    public DrawdownTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void noDrawdown() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        var close = new ClosePriceIndicator(series);

        var amount = Drawdown.amount(series, null, close, true);
        var length = Drawdown.length(series, null, close, true);
        assertNumEquals(0, amount);
        assertNumEquals(0, length);
    }

    @Test
    public void relativeDrawdownAndLength() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 7, 9, 5, 11).build();
        var close = new ClosePriceIndicator(series);

        var amount = Drawdown.amount(series, null, close, true);
        var length = Drawdown.length(series, null, close, true);
        assertNumEquals(0.5, amount);
        assertNumEquals(3, length);
    }

    @Test
    public void absoluteDrawdown() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 80, 120, 90).build();
        var close = new ClosePriceIndicator(series);

        var amount = Drawdown.amount(series, null, close, false);
        var length = Drawdown.length(series, null, close, false);
        assertNumEquals(30, amount);
        assertNumEquals(1, length);
    }

    @Test
    public void limitsToTradingRecordRange() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 2, 9, 3, 8).build();
        var close = new ClosePriceIndicator(series);
        var record = new BaseTradingRecord(TradeType.BUY, 2, 4, new ZeroCostModel(), new ZeroCostModel());

        var amount = Drawdown.amount(series, record, close);
        var length = Drawdown.length(series, record, close);
        assertNumEquals(0.6667, amount);
        assertNumEquals(1, length);
    }
}