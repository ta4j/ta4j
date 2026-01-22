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

public class CrossedUpIndicatorRuleTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
    }

    @Test
    public void isSatisfied() {
        var evaluatedIndicator = new FixedNumIndicator(series, 8, 9, 10, 12, 9, 11, 12, 13);
        var rule = new CrossedUpIndicatorRule(evaluatedIndicator, 10);

        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertTrue(rule.isSatisfied(3));
        assertFalse(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
        assertFalse(rule.isSatisfied(7));
    }

    @Test
    public void onlyThresholdBetweenFirstBarAndLastBar() {
        var evaluatedIndicator = new FixedNumIndicator(series, 9, 10, 10, 10, 11);
        var rule = new CrossedUpIndicatorRule(evaluatedIndicator, 10);

        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertTrue(rule.isSatisfied(4));
    }

    @Test
    public void repeatedlyHittingThresholdAfterCrossUp() {
        var evaluatedIndicator = new FixedNumIndicator(series, 9, 10, 11, 10, 11, 10, 11);
        var rule = new CrossedUpIndicatorRule(evaluatedIndicator, 10);

        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertTrue("first cross up", rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertFalse(rule.isSatisfied(4));
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
    }

    @Test
    public void serializeAndDeserialize() {
        var evaluatedIndicator = new FixedNumIndicator(series, 3, 4, 6);
        var rule = new CrossedUpIndicatorRule(evaluatedIndicator, series.numFactory().numOf(5));
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
