/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import org.ta4j.core.trading.rules.AndRule;
import org.ta4j.core.trading.rules.NotRule;
import org.ta4j.core.trading.rules.OrRule;
import org.ta4j.core.trading.rules.XorRule;

/**
 * A rule for strategy building.
 * <p></p>
 * A trading rule may be composed of a combination of other rules.
 *
 * A {@link Strategy trading strategy} is a pair of complementary (entry and exit) rules.
 */
public interface Rule {

    /**
     * @param rule another trading rule
     * @return a rule which is the AND combination of this rule with the provided one
     */
    default Rule and(Rule rule) {
    	return new AndRule(this, rule);
    }

    /**
     * @param rule another trading rule
     * @return a rule which is the OR combination of this rule with the provided one
     */
    default Rule or(Rule rule) {
    	return new OrRule(this, rule);
    }

    /**
     * @param rule another trading rule
     * @return a rule which is the XOR combination of this rule with the provided one
     */
    default Rule xor(Rule rule) {
    	return new XorRule(this, rule);
    }

    /**
     * @return a rule which is the logical negation of this rule
     */
    default Rule negation() {
    	return new NotRule(this);
    }

    /**
     * @param index the bar index
     * @return true if this rule is satisfied for the provided index, false otherwise
     */
    default boolean isSatisfied(int index) {
    	return isSatisfied(index, null);
    }

    /**
     * @param index the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true if this rule is satisfied for the provided index, false otherwise
     */
    boolean isSatisfied(int index, TradingRecord tradingRecord);
}
