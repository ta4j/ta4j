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

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Criterion indicator.
 * 
 * <p>
 * Transforms any AnalysisCriterion into an Indicator. Returns <code>true</code>
 * if the calculated criterion value on a bar index is better than the given
 * {@link #criterionValue}, otherwise returns <code>false</code>.
 */
public final class CriterionIndicator extends CachedIndicator<Boolean> {

    private final AnalysisCriterion criterion;
    private final Num requiredCriterionValue;
    private final TradingRecord tradingRecord;
    private final Position position;

    /**
     * Constructor.
     * 
     * @param series                 the bar series
     * @param tradingRecord          the trading record
     * @param criterion              the criterion to get the calculated criterion
     *                               value on a bar index
     * @param requiredCriterionValue the required criterion value to test against
     *                               the calculated criterion value
     */
    public CriterionIndicator(BarSeries series, TradingRecord tradingRecord, AnalysisCriterion criterion,
            Num requiredCriterionValue) {
        super(series);
        this.tradingRecord = tradingRecord;
        this.criterion = criterion;
        this.requiredCriterionValue = requiredCriterionValue;
        this.position = null;
    }

    /**
     * Constructor.
     * 
     * @param series                 the bar series
     * @param position               the position
     * @param criterion              the criterion to get the calculated criterion
     *                               value on a bar index
     * @param requiredCriterionValue the required criterion value to test against
     *                               the calculated criterion value
     */
    public CriterionIndicator(BarSeries series, Position position, AnalysisCriterion criterion,
            Num requiredCriterionValue) {
        super(series);
        this.tradingRecord = null;
        this.criterion = criterion;
        this.requiredCriterionValue = requiredCriterionValue;
        this.position = position;
    }

    @Override
    protected Boolean calculate(int index) {
        Num calculatedCriterionValue = tradingRecord != null ? criterion.calculate(getBarSeries(), tradingRecord)
                : criterion.calculate(getBarSeries(), position);
        return criterion.betterThan(calculatedCriterionValue, requiredCriterionValue);
    }
}