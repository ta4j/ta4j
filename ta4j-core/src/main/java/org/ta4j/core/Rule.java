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
package org.ta4j.core;

import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.NotRule;
import org.ta4j.core.rules.OrRule;
import org.ta4j.core.rules.XorRule;

/**
 * A rule for strategy building.
 * * 建立策略的规则。
 *
 * A trading rule may be composed of a combination of other rules.
 * * 一条交易规则可能由其他规则组合而成。
 *
 * A {@link Strategy trading strategy} is a pair of complementary (entry and exit) rules.
 * * {@link Strategy 交易策略} 是一对互补的（进入和退出）规则。
 */
public interface Rule {

    /**
     * @param rule another trading rule
     *             另一个交易规则
     * @return a rule which is the AND combination of this rule with the provided   one
     * * @return 一个规则，它是该规则与提供的规则的 AND 组合
     */
    default Rule and(Rule rule) {
        return new AndRule(this, rule);
    }

    /**
     * @param rule another trading rule
     *             另一个交易规则
     *
     * @return a rule which is the OR combination of this rule with the provided one
     *          一条规则，它是该规则与提供的规则的 OR 组合
     */
    default Rule or(Rule rule) {
        return new OrRule(this, rule);
    }

    /**
     * @param rule another trading rule
     *             另一个交易规则
     *
     * @return a rule which is the XOR combination of this rule with the provided  one
     *      * @return 一个规则，它是该规则与提供的规则的 XOR 组合
     */
    default Rule xor(Rule rule) {
        return new XorRule(this, rule);
    }

    /**
     * @return a rule which is the logical negation of this rule
     * * @return 一个规则，它是这个规则的逻辑否定
     */
    default Rule negation() {
        return new NotRule(this);
    }

    /**
     * @param index the bar index
     *              条形索引
     *
     * @return true if this rule is satisfied for the provided index, false  otherwise
     * * @return 如果提供的索引满足此规则，则返回 true，否则返回 false
     */
    default boolean isSatisfied(int index) {
        return isSatisfied(index, null);
    }

    /**
     * @param index         the bar index
     *                      条形索引
     *
     * @param tradingRecord the potentially needed trading history
     *                      可能需要的交易历史
     *
     * @return true if this rule is satisfied for the provided index, false  otherwise
     * * @return 如果提供的索引满足此规则，则返回 true，否则返回 false
     */
    boolean isSatisfied(int index, TradingRecord tradingRecord);
}
