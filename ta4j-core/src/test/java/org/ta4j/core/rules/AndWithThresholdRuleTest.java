/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class AndWithThresholdRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    private BarSeries series;

    @Before
    public void setUp() {
        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
        series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).build();
    }

    @Test
    public void isSatisfiedWhenBothRulesAlwaysSatisfied() {
        Rule rule = new AndWithThresholdRule(satisfiedRule, satisfiedRule, 1);
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWhenBothRulesNeverSatisfied() {
        Rule rule = new AndWithThresholdRule(unsatisfiedRule, unsatisfiedRule, 1);
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWhenFirstRuleSatisfiedSecondNot() {
        Rule rule = new AndWithThresholdRule(satisfiedRule, unsatisfiedRule, 1);
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWhenSecondRuleSatisfiedFirstNot() {
        Rule rule = new AndWithThresholdRule(unsatisfiedRule, satisfiedRule, 1);
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWithThresholdBothRulesSatisfiedAtDifferentTimes() {
        Rule rule1At5 = new FixedRule(5);
        Rule rule2At7 = new FixedRule(7);

        Rule rule = new AndWithThresholdRule(rule1At5, rule2At7, 4);
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
    }

    @Test
    public void isSatisfiedWithThresholdBothRulesSatisfiedWithinWindow() {
        Rule rule1At7 = new FixedRule(7);
        Rule rule2At9 = new FixedRule(9);

        Rule rule = new AndWithThresholdRule(rule1At7, rule2At9, 4);
        assertFalse(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
        assertTrue(rule.isSatisfied(10));
    }

    @Test
    public void isSatisfiedWithThresholdBothRulesOutsideWindow() {
        Rule rule1At5 = new FixedRule(5);
        Rule rule2At9 = new FixedRule(9);

        Rule rule = new AndWithThresholdRule(rule1At5, rule2At9, 3);
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWhenIndexLessThanThreshold() {
        Rule rule = new AndWithThresholdRule(satisfiedRule, satisfiedRule, 4);
        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
    }

    @Test
    public void isSatisfiedWhenIndexEqualsThresholdMinus1() {
        Rule rule1At4 = new FixedRule(4);
        Rule rule2At7 = new FixedRule(7);

        Rule rule = new AndWithThresholdRule(rule1At4, rule2At7, 4);
        assertTrue(rule.isSatisfied(7));
    }

    @Test
    public void isSatisfiedWithThreshold1() {
        Rule rule1At7 = new FixedRule(7);
        Rule rule2At7 = new FixedRule(7);

        Rule rule = new AndWithThresholdRule(rule1At7, rule2At7, 1);
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertFalse(rule.isSatisfied(8));
    }

    @Test
    public void isSatisfiedWithLargeThreshold() {
        Rule rule1At2 = new FixedRule(2);
        Rule rule2At8 = new FixedRule(8);

        Rule rule = new AndWithThresholdRule(rule1At2, rule2At8, 7);
        assertFalse(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWithBothRulesSatisfiedAtSameIndex() {
        Rule rule1At7 = new FixedRule(7);
        Rule rule2At7 = new FixedRule(7);

        Rule rule = new AndWithThresholdRule(rule1At7, rule2At7, 4);
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
        assertTrue(rule.isSatisfied(10));
    }

    @Test
    public void isSatisfiedWhenBothRulesSatisfiedWithinWindow() {
        Rule rule1At5 = new FixedRule(5, 6, 7, 8, 9);
        Rule rule2At7 = new FixedRule(7, 8, 9);

        Rule rule = new AndWithThresholdRule(rule1At5, rule2At7, 4);
        assertTrue(rule.isSatisfied(7));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorThrowsExceptionWhenThresholdIsZero() {
        new AndWithThresholdRule(satisfiedRule, satisfiedRule, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorThrowsExceptionWhenThresholdIsNegative() {
        new AndWithThresholdRule(satisfiedRule, satisfiedRule, -1);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsExceptionWhenRule1IsNull() {
        new AndWithThresholdRule(null, satisfiedRule, 1);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsExceptionWhenRule2IsNull() {
        new AndWithThresholdRule(satisfiedRule, null, 1);
    }

    @Test
    public void getRule1ReturnsFirstRule() {
        AndWithThresholdRule rule = new AndWithThresholdRule(satisfiedRule, unsatisfiedRule, 1);
        assertTrue(rule.getRule1() == satisfiedRule);
    }

    @Test
    public void getRule2ReturnsSecondRule() {
        AndWithThresholdRule rule = new AndWithThresholdRule(satisfiedRule, unsatisfiedRule, 1);
        assertTrue(rule.getRule2() == unsatisfiedRule);
    }

    @Test
    public void serializeAndDeserialize() {
        Rule composite = new AndWithThresholdRule(satisfiedRule, BooleanRule.TRUE, 3);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, composite);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, composite);
    }
}
