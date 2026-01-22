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

public class InPipeRuleTest {

    private InPipeRule rule;
    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withName("I am empty").build();
        var indicator = new FixedNumIndicator(series, 50d, 70d, 80d, 90d, 99d, 60d, 30d, 20d, 10d, 0d);
        rule = new InPipeRule(indicator, series.numFactory().numOf(80), series.numFactory().numOf(20));
    }

    @Test
    public void isSatisfied() {
        assertTrue(rule.isSatisfied(0));
        assertTrue(rule.isSatisfied(1));
        assertTrue(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertFalse(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertFalse(rule.isSatisfied(8));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
