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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import static org.ta4j.core.num.NaN.NaN;

public class BreakEvenIndicator extends TradeBasedIndicator<Num> {
    private final TransformIndicator breakEvenCalculator;

    public BreakEvenIndicator(BarSeries series, TradingRecord tradingRecord, Number feePerPosition) {
        super(series, tradingRecord);
        double buyFeeFactor = 1 + feePerPosition.doubleValue();
        double sellFeeFactor = 1 - feePerPosition.doubleValue();
        breakEvenCalculator = TransformIndicator
                .divide(TransformIndicator.multiply(new ClosePriceIndicator(series), buyFeeFactor), sellFeeFactor);
    }

    @Override
    protected Num calculateNoLastTradeAvailable(int index) {
        return NaN;
    }

    @Override
    protected Num calculateLastTradeWasEntry(Trade entryTrade, int index) {
        return breakEvenCalculator.getValue(entryTrade.getIndex());
    }

    @Override
    protected Num calculateLastTradeWasExit(Trade exitTrade, int index) {
        return NaN;
    }
}
