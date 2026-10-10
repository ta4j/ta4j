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
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.num.Num;

public class OverIndicatorRuleTest {

    private OverIndicatorRule rule;
    private BarSeries series;
    private TraceTestLogger traceTestLogger;

    @Before
    public void setUp() {
        series = new BaseBarSeriesBuilder().build();
        Indicator<Num> indicator = new FixedNumIndicator(series, 20, 15, 10, 5, 0, -5, -10, 100);
        rule = new OverIndicatorRule(indicator, series.numFactory().numOf(5));
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
        assertTrue(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertFalse(rule.isSatisfied(4));
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
    }

    @Test
    public void traceIncludesComparedValues() {
        assertTrue(rule.isSatisfiedWithTraceMode(2, Rule.TraceMode.VERBOSE));

        String logContent = traceTestLogger.getLogOutput();
        assertTrue("Trace should include the evaluated indicator value", logContent.contains("firstValue=10"));
        assertTrue("Trace should include the threshold value", logContent.contains("secondValue=5"));
        assertTrue("Trace should include the comparison operator", logContent.contains("operator=>"));
        assertTrue("Trace should explain the comparison result", logContent.contains("reason=firstAboveSecond"));
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
