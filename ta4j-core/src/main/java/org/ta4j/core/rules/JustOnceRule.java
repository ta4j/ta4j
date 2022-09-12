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

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * A one-shot rule.
 * 一次性规则。
 *
 * Satisfied the first time it's checked then never again.
 * 第一次检查满意，以后再也不满意了。
 */
public class JustOnceRule extends AbstractRule {

    private final Rule rule;
    private boolean satisfied = false;

    /**
     * Constructor.
     *
     * Satisfied the first time the inner rule is satisfied then never again.
     * * 第一次满足内在规则，然后再也不会满足。
     *
     * @param rule the rule that should be satisfied only the first time
     *             * @param rule 应该只在第一次满足的规则
     */
    public JustOnceRule(Rule rule) {
        this.rule = rule;
    }

    /**
     * Constructor.
     *
     * Satisfied the first time it's checked then never again.
     * *第一次检查时满意，以后再也不满意了。
     */
    public JustOnceRule() {
        this.rule = null;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (satisfied) {
            return false;
        } else if (rule == null) {
            satisfied = true;
            traceIsSatisfied(index, true);
            return true;
        }
        this.satisfied = this.rule.isSatisfied(index, tradingRecord);
        return this.satisfied;
    }
}
