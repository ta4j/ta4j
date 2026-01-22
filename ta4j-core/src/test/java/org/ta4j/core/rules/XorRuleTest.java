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

public class XorRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    private BarSeries series;

    @Before
    public void setUp() {
        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
        series = new MockBarSeriesBuilder().withData(1).build();
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

    @Test
    public void serializeAndDeserialize() {
        Rule xor = satisfiedRule.xor(unsatisfiedRule);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, xor);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, xor);
    }
}
