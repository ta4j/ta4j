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
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * Satisfied when the first rule is satisfied before the second (i.e. both rules
 * must be satisfied). Between these two conditions, the specified reset rule is
 * not allowed to hold.
 */
public class BeforeRule extends AbstractRule {

    private final BarSeries series;
    private final Rule firstRule;
    private final Rule secondRule;
    private final Rule resetRule;

    public BeforeRule(BarSeries series, Rule firstRule, Rule secondRule, Rule resetRule) {
        super();
        this.series = series;
        this.firstRule = firstRule;
        this.secondRule = secondRule;
        this.resetRule = resetRule;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (!secondRule.isSatisfied(index, tradingRecord)) {
            traceIsSatisfied(index, false);
            return false;
        }

        for (int i = index; i >= series.getBeginIndex(); i--) {
            if (resetRule.isSatisfied(i, tradingRecord)) {
                traceIsSatisfied(index, false);
                return false;
            }
            if (firstRule.isSatisfied(i, tradingRecord)) {
                traceIsSatisfied(index, true);
                return true;
            }
        }

        traceIsSatisfied(index, false);
        return false;
    }
}
