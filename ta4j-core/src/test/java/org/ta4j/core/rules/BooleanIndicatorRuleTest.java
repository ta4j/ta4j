/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class BooleanIndicatorRuleTest {

    private BooleanIndicatorRule rule;
    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        Indicator<Boolean> indicator = new FixedIndicator<>(series, true, true, false, false, true);
        rule = new BooleanIndicatorRule(indicator);
    }

    @Test
    public void isSatisfied() {
        assertTrue(rule.isSatisfied(0));
        assertTrue(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertTrue(rule.isSatisfied(4));
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
