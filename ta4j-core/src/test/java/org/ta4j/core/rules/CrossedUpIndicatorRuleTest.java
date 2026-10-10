/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class CrossedUpIndicatorRuleTest {

    private BarSeries series;
    private TraceTestLogger traceTestLogger;

    @BeforeEach
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        traceTestLogger = new TraceTestLogger();
        traceTestLogger.open();
    }

    @AfterEach
    public void tearDown() {
        traceTestLogger.close();
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
    public void traceIncludesCurrentAndPriorCrossValues() {
        var evaluatedIndicator = new FixedNumIndicator(series, 8, 9, 10, 12);
        var rule = new CrossedUpIndicatorRule(evaluatedIndicator, 10);

        assertTrue(rule.isSatisfiedWithTraceMode(3, Rule.TraceMode.VERBOSE));

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("firstValue=12"), "Trace should include the current evaluated value");
        assertTrue(logContent.contains("secondValue=10"), "Trace should include the current threshold value");
        assertTrue(logContent.contains("previousFirstValue=10"), "Trace should include the previous evaluated value");
        assertTrue(logContent.contains("previousSecondValue=10"), "Trace should include the previous threshold value");
        assertTrue(logContent.contains("priorFirstValue=9"), "Trace should include the cross-base evaluated value");
        assertTrue(logContent.contains("priorSecondValue=10"), "Trace should include the cross-base threshold value");
        assertTrue(logContent.contains("reason=crossedUp"), "Trace should explain the cross result");
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
        assertTrue(rule.isSatisfied(2), "first cross up");
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
