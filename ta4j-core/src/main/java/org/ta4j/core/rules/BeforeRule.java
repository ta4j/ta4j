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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * Before rule.
 * <p>
 * The rule is satisfied if the first given rule was satisfied before the second
 * rule. In between those two conditions, the given reset rule is not allowed to
 * hold.
 */
public class BeforeRule extends AbstractRule {

    private final BarSeries series;
    private final Rule firstCondition;
    private final Rule secondCondition;
    private final Rule resetCondition;

    public BeforeRule(BarSeries series, Rule firstCondition, Rule secondCondition, Rule resetCondition) {
        super();
        this.series = series;
        this.firstCondition = firstCondition;
        this.secondCondition = secondCondition;
        this.resetCondition = resetCondition;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (!secondCondition.isSatisfied(index, tradingRecord)) {
            traceIsSatisfied(index, false);
            return false;
        }

        for (int i = index; i >= series.getBeginIndex(); i--) {
            if (resetCondition.isSatisfied(i, tradingRecord)) {
                traceIsSatisfied(index, false);
                return false;
            }
            if (firstCondition.isSatisfied(i, tradingRecord)) {
                traceIsSatisfied(index, true);
                return true;
            }
        }

        traceIsSatisfied(index, false);
        return false;
    }
}
