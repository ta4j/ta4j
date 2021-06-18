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
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.num.Num;

/**
 * Crossed-down indicator rule.
 *
 * Satisfied when the value of the first {@link Indicator indicator}
 * crosses-down the value of the second one.
 */
public class CrossedDownIndicatorRule extends AbstractRule {

    /** The cross indicator */
    private final CrossIndicator cross;

    /**
     * Constructor.
     * 
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public CrossedDownIndicatorRule(Indicator<Num> indicator, Number threshold) {
        this(indicator, indicator.numOf(threshold));
    }

    /**
     * Constructor.
     * 
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public CrossedDownIndicatorRule(Indicator<Num> indicator, Num threshold) {
        this(indicator, new ConstantIndicator<>(indicator.getBarSeries(), threshold));
    }

    /**
     * Constructor.
     * 
     * @param first  the first indicator
     * @param second the second indicator
     */
    public CrossedDownIndicatorRule(Indicator<Num> first, Indicator<Num> second) {
        this.cross = new CrossIndicator(first, second);
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = cross.getValue(index);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    /**
     * @return the initial lower indicator
     */
    public Indicator<Num> getLow() {
        return cross.getLow();
    }

    /**
     * @return the initial upper indicator
     */
    public Indicator<Num> getUp() {
        return cross.getUp();
    }
}
