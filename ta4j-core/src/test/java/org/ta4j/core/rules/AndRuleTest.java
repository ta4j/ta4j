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

public class AndRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    private BarSeries series;

    @Before
    public void setUp() {
        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
        series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
    }

    @Test
    public void isSatisfied() {
        assertFalse(satisfiedRule.and(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.and(satisfiedRule).isSatisfied(0));
        assertFalse(unsatisfiedRule.and(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.and(unsatisfiedRule).isSatisfied(0));

        assertTrue(satisfiedRule.and(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.and(satisfiedRule).isSatisfied(10));
        assertFalse(unsatisfiedRule.and(BooleanRule.TRUE).isSatisfied(10));
        assertFalse(BooleanRule.TRUE.and(unsatisfiedRule).isSatisfied(10));
    }

    @Test
    public void serializeAndDeserialize() {
        Rule composite = satisfiedRule.and(BooleanRule.TRUE);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, composite);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, composite);
    }
}
