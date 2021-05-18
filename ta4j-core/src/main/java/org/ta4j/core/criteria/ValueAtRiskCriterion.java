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
package org.ta4j.core.criteria;

import java.util.Collections;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.Returns;
import org.ta4j.core.num.Num;

/**
 * Value at Risk criterion.
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Value_at_risk">https://en.wikipedia.org/wiki/Value_at_risk</a>
 */
public class ValueAtRiskCriterion extends AbstractAnalysisCriterion {
    /**
     * Confidence level as absolute value (e.g. 0.95)
     */
    private final Double confidence;

    /**
     * Constructor
     *
     * @param confidence the confidence level
     */
    public ValueAtRiskCriterion(Double confidence) {
        this.confidence = confidence;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position != null && position.isClosed()) {
            Returns returns = new Returns(series, position, Returns.ReturnType.LOG);
            return calculateVaR(returns, confidence);
        }
        return series.numOf(0);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Returns returns = new Returns(series, tradingRecord, Returns.ReturnType.LOG);
        return calculateVaR(returns, confidence);
    }

    /**
     * Calculates the VaR on the return series
     * 
     * @param returns    the corresponding returns
     * @param confidence the confidence level
     * @return the relative Value at Risk
     */
    private static Num calculateVaR(Returns returns, double confidence) {
        Num zero = returns.numOf(0);
        // select non-NaN returns
        List<Num> returnRates = returns.getValues().subList(1, returns.getSize() + 1);
        Num valueAtRisk = zero;
        if (!returnRates.isEmpty()) {
            // F(x_var) >= alpha (=1-confidence)
            int nInBody = (int) (returns.getSize() * confidence);
            int nInTail = returns.getSize() - nInBody;

            // The series is not empty, nInTail > 0
            Collections.sort(returnRates);
            valueAtRisk = returnRates.get(nInTail - 1);

            // VaR is non-positive
            if (valueAtRisk.isGreaterThan(zero)) {
                valueAtRisk = zero;
            }
        }
        return valueAtRisk;
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        // because it represents a loss, VaR is non-positive
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
