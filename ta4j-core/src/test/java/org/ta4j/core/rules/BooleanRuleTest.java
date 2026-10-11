/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class BooleanRuleTest {

    private BooleanRule satisfiedRule;
    private BooleanRule unsatisfiedRule;
    private BarSeries series;

    @Before
    public void setUp() {
        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
        series = new MockBarSeriesBuilder().withData(1).build();
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

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, satisfiedRule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, satisfiedRule);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, unsatisfiedRule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, unsatisfiedRule);
    }
}
