/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class FixedRuleTest {

    @Test
    public void isSatisfied() {
        FixedRule fixedRule = new FixedRule();
        assertFalse(fixedRule.isSatisfied(0));
        assertFalse(fixedRule.isSatisfied(1));
        assertFalse(fixedRule.isSatisfied(2));
        assertFalse(fixedRule.isSatisfied(9));

        fixedRule = new FixedRule(1, 2, 3);
        assertFalse(fixedRule.isSatisfied(0));
        assertTrue(fixedRule.isSatisfied(1));
        assertTrue(fixedRule.isSatisfied(2));
        assertTrue(fixedRule.isSatisfied(3));
        assertFalse(fixedRule.isSatisfied(4));
        assertFalse(fixedRule.isSatisfied(5));
        assertFalse(fixedRule.isSatisfied(6));
        assertFalse(fixedRule.isSatisfied(7));
        assertFalse(fixedRule.isSatisfied(8));
        assertFalse(fixedRule.isSatisfied(9));
        assertFalse(fixedRule.isSatisfied(10));
    }

    @Test
    public void serializeAndDeserialize() {
        BarSeries series = new MockBarSeriesBuilder().withData(1).build();
        FixedRule rule = new FixedRule(1, 4, 5);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
