/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
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
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;

/**
 * Indicator-under-indicator rule.
 *
 * Satisfied when the value of the first {@link Indicator indicator} is strictly
 * lesser than the value of the second one.
 */
public class WithinPercentageRule extends AbstractRule {

    /**
     * The first indicator
     */
    private final Indicator<Num> baseline;
    /**
     * The second indicator
     */
    private final Indicator<Num> value;

    /**
     * The second indicator
     */
    private final Num percentage;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public WithinPercentageRule(Indicator<Num> indicator, Number threshold, Num percentage) {
        this(indicator, new ConstantIndicator<>(indicator.getBarSeries(),
            indicator.numOf(threshold)), percentage);
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public WithinPercentageRule(Indicator<Num> indicator, Num threshold , Num percentage) {
        this(indicator, new ConstantIndicator<>(indicator.getBarSeries(), threshold), percentage);
    }

    /**
     * Constructor.
     *
     * @param baseline  the first indicator
     * @param value the second indicator
     */
    public WithinPercentageRule(Indicator<Num> baseline, Indicator<Num> value, Num percentage) {
        this.baseline = baseline;
        this.value = value;
        this.percentage = percentage;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        DecimalNum hundred = DecimalNum.valueOf(100);
        Num minPercentage = hundred.minus(percentage).dividedBy(hundred);
        Num maxPercentage = hundred.plus(percentage).dividedBy(hundred);
        Num min = baseline.getValue(index).multipliedBy(minPercentage);
        Num max = baseline.getValue(index).multipliedBy(maxPercentage);
        Num actual = value.getValue(index);
        final boolean satisfied = max.isGreaterThan(actual) && min.isLessThan(actual);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
