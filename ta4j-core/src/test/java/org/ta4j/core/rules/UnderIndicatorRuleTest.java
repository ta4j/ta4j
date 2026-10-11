/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class UnderIndicatorRuleTest {

    private UnderIndicatorRule rule;
    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        var indicator = new FixedNumIndicator(series, 0, 5, 8, 5, 1, 10, 20, 30);
        rule = new UnderIndicatorRule(indicator, series.numFactory().numOf(5));
    }

    @Test
    public void isSatisfied() {
        assertTrue(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertTrue(rule.isSatisfied(4));
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
        assertFalse(rule.isSatisfied(7));
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
