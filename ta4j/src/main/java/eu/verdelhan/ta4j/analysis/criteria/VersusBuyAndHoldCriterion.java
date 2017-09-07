/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.BaseTradingRecord;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.TradingRecord;

/**
 * Versus "buy and hold" criterion.
 * <p>
 * Compares the value of a provided {@link AnalysisCriterion criterion} with the value of a {@link BuyAndHoldCriterion "buy and hold" criterion}.
 */
public class VersusBuyAndHoldCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion criterion;

    /**
     * Constructor.
     * @param criterion an analysis criterion to be compared
     */
    public VersusBuyAndHoldCriterion(AnalysisCriterion criterion) {
        this.criterion = criterion;
    }

    @Override
    public double calculate(TimeSeries series, TradingRecord tradingRecord) {
        TradingRecord fakeRecord = new BaseTradingRecord();
        fakeRecord.enter(series.getBeginIndex());
        fakeRecord.exit(series.getEndIndex());

        return criterion.calculate(series, tradingRecord) / criterion.calculate(series, fakeRecord);
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        TradingRecord fakeRecord = new BaseTradingRecord();
        fakeRecord.enter(series.getBeginIndex());
        fakeRecord.exit(series.getEndIndex());

        return criterion.calculate(series, trade) / criterion.calculate(series, fakeRecord);
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return criterionValue1 > criterionValue2;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append(" (").append(criterion).append(')');
        return sb.toString();
    }
}
