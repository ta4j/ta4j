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

public class OrWithThresholdRuleTest {

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
        Rule rule = new OrWithThresholdRule(satisfiedRule, satisfiedRule, 1);
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWhenBothRulesNeverSatisfied() {
        Rule rule = new OrWithThresholdRule(unsatisfiedRule, unsatisfiedRule, 1);
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWhenFirstRuleSatisfiedSecondNot() {
        Rule rule = new OrWithThresholdRule(satisfiedRule, unsatisfiedRule, 1);
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWhenSecondRuleSatisfiedFirstNot() {
        Rule rule = new OrWithThresholdRule(unsatisfiedRule, satisfiedRule, 1);
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWithThresholdOnlyFirstRuleSatisfied() {
        Rule rule1At5 = new FixedRule(5);
        Rule rule2Never = new BooleanRule(false);

        Rule rule = new OrWithThresholdRule(rule1At5, rule2Never, 4);
        assertFalse(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWithThresholdOnlySecondRuleSatisfied() {
        Rule rule1Never = new BooleanRule(false);
        Rule rule2At7 = new FixedRule(7);

        Rule rule = new OrWithThresholdRule(rule1Never, rule2At7, 4);
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
        assertTrue(rule.isSatisfied(10));
    }

    @Test
    public void isSatisfiedWithThresholdEitherRuleSatisfiedAtDifferentTimes() {
        Rule rule1At5 = new FixedRule(5);
        Rule rule2At9 = new FixedRule(9);

        Rule rule = new OrWithThresholdRule(rule1At5, rule2At9, 4);
        assertFalse(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
        assertTrue(rule.isSatisfied(10));
    }

    @Test
    public void isSatisfiedWithThresholdBothRulesOutsideWindow() {
        Rule rule1At2 = new FixedRule(2);
        Rule rule2At3 = new FixedRule(3);

        Rule rule = new OrWithThresholdRule(rule1At2, rule2At3, 3);
        assertFalse(rule.isSatisfied(7));
    }

    @Test
    public void isSatisfiedWhenIndexLessThanThreshold() {
        Rule rule = new OrWithThresholdRule(satisfiedRule, satisfiedRule, 4);
        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
    }

    @Test
    public void isSatisfiedWhenIndexEqualsThresholdMinus1() {
        Rule rule1At4 = new FixedRule(4);
        Rule rule2Never = new BooleanRule(false);

        Rule rule = new OrWithThresholdRule(rule1At4, rule2Never, 4);
        assertTrue(rule.isSatisfied(4));
    }

    @Test
    public void isSatisfiedWithThreshold1() {
        Rule rule1At7 = new FixedRule(7);
        Rule rule2Never = new BooleanRule(false);

        Rule rule = new OrWithThresholdRule(rule1At7, rule2Never, 1);
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertFalse(rule.isSatisfied(8));
    }

    @Test
    public void isSatisfiedWithLargeThreshold() {
        Rule rule1At2 = new FixedRule(2);
        Rule rule2Never = new BooleanRule(false);

        Rule rule = new OrWithThresholdRule(rule1At2, rule2Never, 7);
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWithBothRulesSatisfiedAtSameIndex() {
        Rule rule1At7 = new FixedRule(7);
        Rule rule2At7 = new FixedRule(7);

        Rule rule = new OrWithThresholdRule(rule1At7, rule2At7, 4);
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
        assertTrue(rule.isSatisfied(10));
    }

    @Test
    public void isSatisfiedEarlyExitOptimization() {
        Rule rule1At5 = new FixedRule(5, 6, 7, 8, 9);
        Rule rule2Never = new BooleanRule(false);

        Rule rule = new OrWithThresholdRule(rule1At5, rule2Never, 4);
        assertTrue(rule.isSatisfied(7));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorThrowsExceptionWhenThresholdIsZero() {
        new OrWithThresholdRule(satisfiedRule, satisfiedRule, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorThrowsExceptionWhenThresholdIsNegative() {
        new OrWithThresholdRule(satisfiedRule, satisfiedRule, -1);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsExceptionWhenRule1IsNull() {
        new OrWithThresholdRule(null, satisfiedRule, 1);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsExceptionWhenRule2IsNull() {
        new OrWithThresholdRule(satisfiedRule, null, 1);
    }

    @Test
    public void getRule1ReturnsFirstRule() {
        OrWithThresholdRule rule = new OrWithThresholdRule(satisfiedRule, unsatisfiedRule, 1);
        assertTrue(rule.getRule1() == satisfiedRule);
    }

    @Test
    public void getRule2ReturnsSecondRule() {
        OrWithThresholdRule rule = new OrWithThresholdRule(satisfiedRule, unsatisfiedRule, 1);
        assertTrue(rule.getRule2() == unsatisfiedRule);
    }

    @Test
    public void serializeAndDeserialize() {
        Rule composite = new OrWithThresholdRule(satisfiedRule, BooleanRule.TRUE, 3);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, composite);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, composite);
    }
}
