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

public class CrossedDownIndicatorRuleTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new BaseBarSeriesBuilder().build();
    }

    @Test
    public void isSatisfied() {
        var evaluatedIndicator = new FixedNumIndicator(series, 12, 11, 10, 9, 11, 8, 7, 6);
        var rule = new CrossedDownIndicatorRule(evaluatedIndicator, 10);

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
        var evaluatedIndicator = new FixedNumIndicator(series, 11, 10, 10, 9);
        var rule = new CrossedDownIndicatorRule(evaluatedIndicator, 10);

        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertTrue(rule.isSatisfied(3));
    }

    @Test
    public void repeatedlyHittingThresholdAfterCrossDown() {
        var evaluatedIndicator = new FixedNumIndicator(series, 11, 10, 9, 10, 9, 10, 9);
        var rule = new CrossedDownIndicatorRule(evaluatedIndicator, 10);

        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertTrue("first cross down", rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertFalse(rule.isSatisfied(4));
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
    }

    @Test
    public void serializeAndDeserialize() {
        var evaluatedIndicator = new FixedNumIndicator(series, 5, 6, 4, 3);
        var rule = new CrossedDownIndicatorRule(evaluatedIndicator, series.numFactory().numOf(5));
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
