package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Rule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    
    @Before
    public void setUp() {
        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
    }
    
    @Test
    public void isSatisfied() {
        assertTrue(satisfiedRule.or(BooleanRule.FALSE).isSatisfied(0));
        assertTrue(BooleanRule.FALSE.or(satisfiedRule).isSatisfied(0));
        assertFalse(unsatisfiedRule.or(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.or(unsatisfiedRule).isSatisfied(0));
        
        assertTrue(satisfiedRule.or(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.or(satisfiedRule).isSatisfied(10));
        assertTrue(unsatisfiedRule.or(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.or(unsatisfiedRule).isSatisfied(10));
    }
}
        