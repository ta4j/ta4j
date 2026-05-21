/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

public class IsLowestRuleTest {

    private IsLowestRule rule;
    private BarSeries series;
    private TraceTestLogger traceTestLogger;

    @Before
    public void setUp() {
        series = new BaseBarSeriesBuilder().build();
        Indicator<Num> indicator = new FixedNumIndicator(series, 1, -5, 3, -6, 5, -7, 0, -1, 2, -8);
        rule = new IsLowestRule(indicator, 3);
        traceTestLogger = new TraceTestLogger();
        traceTestLogger.open();
    }

    @After
    public void tearDown() {
        traceTestLogger.close();
    }

    @Test
    public void isSatisfied() {
        assertTrue(rule.isSatisfied(0));
        assertTrue(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertTrue(rule.isSatisfied(3));
        assertFalse(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
        assertFalse(rule.isSatisfied(7));
        assertFalse(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
    }

    @Test
    public void traceExplainsNaNValues() {
        Indicator<Num> indicator = new FixedIndicator<>(series, NaN.NaN);
        IsLowestRule nanRule = new IsLowestRule(indicator, 3);

        assertFalse(nanRule.isSatisfiedWithTraceMode(0, Rule.TraceMode.VERBOSE));

        assertTrue("Trace should explain NaN-specific false results",
                traceTestLogger.getLogOutput().contains("reason=valueNaN"));
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
