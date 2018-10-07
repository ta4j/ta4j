package org.ta4j.core;

import org.ta4j.core.trading.rules.AndRule;
import org.ta4j.core.trading.rules.NotRule;
import org.ta4j.core.trading.rules.OrRule;
import org.ta4j.core.trading.rules.XorRule;

/**
 * A rule for strategy building.
 * </p>
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
