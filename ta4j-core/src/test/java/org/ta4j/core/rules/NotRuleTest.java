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

public class NotRuleTest {

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
        assertFalse(satisfiedRule.negation().isSatisfied(0));
        assertTrue(unsatisfiedRule.negation().isSatisfied(0));

        assertFalse(satisfiedRule.negation().isSatisfied(10));
        assertTrue(unsatisfiedRule.negation().isSatisfied(10));
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, satisfiedRule.negation());
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, satisfiedRule.negation());
    }
}
