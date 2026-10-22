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

    @BeforeEach
    public void setUp() {
        series = new BaseBarSeriesBuilder().build();
        Indicator<Num> indicator = new FixedNumIndicator(series, 20, 15, 10, 5, 0, -5, -10, 100);
        rule = new OverIndicatorRule(indicator, series.numFactory().numOf(5));
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
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
    }

    @Test
    public void traceIncludesComparedValues() {
        assertTrue(rule.isSatisfiedWithTraceMode(2, Rule.TraceMode.VERBOSE));

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("firstValue=10"), "Trace should include the evaluated indicator value");
        assertTrue(logContent.contains("secondValue=5"), "Trace should include the threshold value");
        assertTrue(logContent.contains("operator=>"), "Trace should include the comparison operator");
        assertTrue(logContent.contains("reason=firstAboveSecond"), "Trace should explain the comparison result");
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
