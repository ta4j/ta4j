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
import org.ta4j.core.rules.helper.ChainLink;

public class ChainRuleTest {

    private ChainRule chainRule;
    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        var indicator = new FixedNumIndicator(series, 6, 5, 8, 5, 1, 10, 2, 30);
        var underIndicatorRule = new UnderIndicatorRule(indicator, series.numFactory().numOf(5));
        var overIndicatorRule = new OverIndicatorRule(indicator, 7);
        var isEqualRule = new IsEqualRule(indicator, 5);
        chainRule = new ChainRule(underIndicatorRule, new ChainLink(overIndicatorRule, 3),
                new ChainLink(isEqualRule, 2));
    }

    @Test
    public void isSatisfied() {
        assertFalse(chainRule.isSatisfied(0));
        assertTrue(chainRule.isSatisfied(4));
        assertTrue(chainRule.isSatisfied(6));
        assertFalse(chainRule.isSatisfied(7));
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, chainRule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, chainRule);
    }
}
