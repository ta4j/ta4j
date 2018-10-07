package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Rule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XorRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    
    @Before
    public void setUp() {
        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
    }
    
    @Test
    public void isSatisfied() {
        assertTrue(satisfiedRule.xor(BooleanRule.FALSE).isSatisfied(0));
        assertTrue(BooleanRule.FALSE.xor(satisfiedRule).isSatisfied(0));
        assertFalse(unsatisfiedRule.xor(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.xor(unsatisfiedRule).isSatisfied(0));
        
        assertFalse(satisfiedRule.xor(BooleanRule.TRUE).isSatisfied(10));
        assertFalse(BooleanRule.TRUE.xor(satisfiedRule).isSatisfied(10));
        assertTrue(unsatisfiedRule.xor(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.xor(unsatisfiedRule).isSatisfied(10));
    }
}
        