package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JustOnceRuleTest {

    private JustOnceRule rule;
    
    @Before
    public void setUp() {
        rule = new JustOnceRule();
    }
    
    @Test
    public void isSatisfied() {
        assertTrue(rule.isSatisfied(10));
        assertFalse(rule.isSatisfied(11));
        assertFalse(rule.isSatisfied(12));
        assertFalse(rule.isSatisfied(13));
        assertFalse(rule.isSatisfied(14));
    }
    
    @Test
    public void isSatisfiedInReverseOrder() {
        assertTrue(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(0));
    }

    @Test
    public void isSatisfiedWithInnerSatisfiedRule() {
        JustOnceRule rule = new JustOnceRule(new BooleanRule(true));
        assertTrue(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(0));
    }

    @Test
    public void isSatisfiedWithInnerNonSatisfiedRule() {
        JustOnceRule rule = new JustOnceRule(new BooleanRule(false));
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(0));
    }

    @Test
    public void isSatisfiedWithInnerRule() {
        JustOnceRule rule = new JustOnceRule(new FixedRule(1, 3, 5));
        assertFalse(rule.isSatisfied(0));
        assertTrue(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertFalse(rule.isSatisfied(4));
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(1));
    }
}
        