/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;

public class IsRisingRuleTest {

    private IsRisingRule rule;
    private BarSeries series;

    @Before
    public void setUp() {
        series = new BaseBarSeriesBuilder().build();
        var indicator = new FixedNumIndicator(series, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3);
        rule = new IsRisingRule(indicator, 3);
    }

    @Test
    public void isSatisfied() {
        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        // First time to have at least 3 rising values.
        assertTrue(rule.isSatisfied(3));
        assertTrue(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
        assertFalse(rule.isSatisfied(7));
        assertFalse(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
