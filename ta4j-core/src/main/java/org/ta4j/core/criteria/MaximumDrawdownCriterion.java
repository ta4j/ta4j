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
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.num.Num;

/**
 * Maximum drawdown criterion, returned in decimal format.
 *
 * <p>
 * The maximum drawdown measures the largest loss. Its value can be within the
 * range of [0,1], e.g. a maximum drawdown of {@code +1} (= +100%) means a total
 * loss, a maximum drawdown of {@code 0} (= 0%) means no loss at all.
 *
 * @see <a href=
 *      "http://en.wikipedia.org/wiki/Drawdown_%28economics%29">https://en.wikipedia.org/wiki/Drawdown_(economics)</a>
 */
public class MaximumDrawdownCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null || position.getExit() == null) {
            return series.zero();
        }
        CashFlow cashFlow = new CashFlow(series, position);
        return calculateMaximumDrawdown(series, null, cashFlow);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        CashFlow cashFlow = new CashFlow(series, tradingRecord);
        return calculateMaximumDrawdown(series, tradingRecord, cashFlow);
    }

    /** The lower the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }

    /**
     * Calculates the maximum drawdown from a cash flow over a series.
     *
     * The formula is as follows:
     *
     * <pre>
     * MDD = (LP - PV) / PV
     * with MDD: Maximum drawdown, in percent.
     * with LP: Lowest point (lowest value after peak value).
     * with PV: Peak value (highest value within the observation).
     * </pre>
     *
     * @param series        the bar series
     * @param tradingRecord the trading record (optional)
     * @param cashFlow      the cash flow
     * @return the maximum drawdown from a cash flow over a series
     */
    private Num calculateMaximumDrawdown(BarSeries series, TradingRecord tradingRecord, CashFlow cashFlow) {

        Num zero = series.zero();
        Num maxPeak = zero;
        Num maximumDrawdown = zero;

        int beginIndex = tradingRecord == null ? series.getBeginIndex() : tradingRecord.getStartIndex(series);
        int endIndex = tradingRecord == null ? series.getEndIndex() : tradingRecord.getEndIndex(series);

        if (!series.isEmpty()) {
            for (int i = beginIndex; i <= endIndex; i++) {

                Num value = cashFlow.getValue(i);
                if (value.isGreaterThan(maxPeak)) {
                    maxPeak = value;
                }

                Num drawdown = maxPeak.minus(value).dividedBy(maxPeak);
                if (drawdown.isGreaterThan(maximumDrawdown)) {
                    maximumDrawdown = drawdown;
                }
            }
        }

        return maximumDrawdown;
    }
}
