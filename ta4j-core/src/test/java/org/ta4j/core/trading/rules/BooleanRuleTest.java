package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BooleanRuleTest {

    private BooleanRule satisfiedRule;
    private BooleanRule unsatisfiedRule;
    
    @Before
    public void setUp() {
        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
    }
    
    @Test
    public void isSatisfied() {
        assertTrue(satisfiedRule.isSatisfied(0));
        assertTrue(satisfiedRule.isSatisfied(1));
        assertTrue(satisfiedRule.isSatisfied(2));
        assertTrue(satisfiedRule.isSatisfied(10));
        
        assertFalse(unsatisfiedRule.isSatisfied(0));
        assertFalse(unsatisfiedRule.isSatisfied(1));
        assertFalse(unsatisfiedRule.isSatisfied(2));
        assertFalse(unsatisfiedRule.isSatisfied(10));
    }
}
        