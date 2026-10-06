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

public class InPipeRuleTest {

    private InPipeRule rule;
    private BarSeries series;
    private TraceTestLogger traceTestLogger;

    @BeforeEach
    public void setUp() {
        series = new MockBarSeriesBuilder().withName("I am empty").build();
        var indicator = new FixedNumIndicator(series, 50d, 70d, 80d, 90d, 99d, 60d, 30d, 20d, 10d, 0d);
        rule = new InPipeRule(indicator, series.numFactory().numOf(80), series.numFactory().numOf(20));
        traceTestLogger = new TraceTestLogger();
        traceTestLogger.open();
    }

    @AfterEach
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
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertFalse(rule.isSatisfied(8));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void traceIncludesPipeValues() {
        assertFalse(rule.isSatisfiedWithTraceMode(3, Rule.TraceMode.VERBOSE));

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("value=90"), "Trace should include the reference value");
        assertTrue(logContent.contains("lowerValue=20"), "Trace should include the lower value");
        assertTrue(logContent.contains("upperValue=80"), "Trace should include the upper value");
        assertTrue(logContent.contains("reason=aboveUpper"), "Trace should explain the failed side");
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
