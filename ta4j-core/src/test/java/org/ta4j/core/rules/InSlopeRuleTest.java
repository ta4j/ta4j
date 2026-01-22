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
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.num.Num;

public class InSlopeRuleTest {

    private InSlopeRule rulePositiveSlope;
    private InSlopeRule ruleNegativeSlope;
    private BarSeries series;

    @Before
    public void setUp() {
        series = new BaseBarSeriesBuilder().build();
        Indicator<Num> indicator = new FixedNumIndicator(series, 50, 70, 80, 90, 99, 60, 30, 20, 10, 0);
        rulePositiveSlope = new InSlopeRule(indicator, series.numFactory().numOf(20), series.numFactory().numOf(30));
        ruleNegativeSlope = new InSlopeRule(indicator, series.numFactory().numOf(-40), series.numFactory().numOf(-20));
    }

    @Test
    public void isSatisfied() {
        assertFalse(rulePositiveSlope.isSatisfied(0));
        assertTrue(rulePositiveSlope.isSatisfied(1));
        assertFalse(rulePositiveSlope.isSatisfied(2));
        assertFalse(rulePositiveSlope.isSatisfied(9));

        assertFalse(ruleNegativeSlope.isSatisfied(0));
        assertFalse(ruleNegativeSlope.isSatisfied(1));
        assertTrue(ruleNegativeSlope.isSatisfied(5));
        assertFalse(ruleNegativeSlope.isSatisfied(9));
    }

    @Test
    public void testSerializationRoundTrip() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rulePositiveSlope);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rulePositiveSlope);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, ruleNegativeSlope);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, ruleNegativeSlope);
    }
}
