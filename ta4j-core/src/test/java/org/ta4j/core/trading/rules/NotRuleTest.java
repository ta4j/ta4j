package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Rule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    
    @Before
    public void setUp() {
        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
    }
    
    @Test
    public void isSatisfied() {
        assertFalse(satisfiedRule.negation().isSatisfied(0));
        assertTrue(unsatisfiedRule.negation().isSatisfied(0));
        
        assertFalse(satisfiedRule.negation().isSatisfied(10));
        assertTrue(unsatisfiedRule.negation().isSatisfied(10));
    }
}
        