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

public class OrRuleTest {

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
        assertTrue(satisfiedRule.or(BooleanRule.FALSE).isSatisfied(0));
        assertTrue(BooleanRule.FALSE.or(satisfiedRule).isSatisfied(0));
        assertFalse(unsatisfiedRule.or(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.or(unsatisfiedRule).isSatisfied(0));

        assertTrue(satisfiedRule.or(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.or(satisfiedRule).isSatisfied(10));
        assertTrue(unsatisfiedRule.or(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.or(unsatisfiedRule).isSatisfied(10));
    }

    @Test
    public void serializeAndDeserialize() {
        Rule composite = unsatisfiedRule.or(BooleanRule.TRUE);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, composite);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, composite);
    }
}
