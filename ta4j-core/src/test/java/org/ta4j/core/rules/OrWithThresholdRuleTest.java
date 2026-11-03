/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OrWithThresholdRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    private BarSeries series;
    private TraceTestLogger ruleTraceTestLogger;

    @BeforeEach
    public void setUp() {
        ruleTraceTestLogger = new TraceTestLogger();
        ruleTraceTestLogger.open();

        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
        series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).build();
    }

    @AfterEach
    public void tearDownLogger() {
        ruleTraceTestLogger.close();
    }

    @Test
    public void isSatisfiedWhenBothRulesAlwaysSatisfied() {
        Rule rule = new OrWithThresholdRule(satisfiedRule, satisfiedRule, 1);
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWhenBothRulesNeverSatisfied() {
        Rule rule = new OrWithThresholdRule(unsatisfiedRule, unsatisfiedRule, 1);
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWhenFirstRuleSatisfiedSecondNot() {
        Rule rule = new OrWithThresholdRule(satisfiedRule, unsatisfiedRule, 1);
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWhenSecondRuleSatisfiedFirstNot() {
        Rule rule = new OrWithThresholdRule(unsatisfiedRule, satisfiedRule, 1);
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedUsesCurrentSeriesBeginIndex() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        InPipeRule inPipe = new InPipeRule(closePrice, 1000, 0);
        Rule rule = new OrWithThresholdRule(inPipe, new BooleanRule(false), 3);

        series.setMaximumBarCount(5);
        assertFalse(rule.isSatisfied(6));
    }

    @Test
    public void isSatisfiedWithThresholdOnlyFirstRuleSatisfied() {
        Rule rule1At5 = new FixedRule(5);
        Rule rule2Never = new BooleanRule(false);

        Rule rule = new OrWithThresholdRule(rule1At5, rule2Never, 4);
        assertFalse(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWithThresholdOnlySecondRuleSatisfied() {
        Rule rule1Never = new BooleanRule(false);
        Rule rule2At7 = new FixedRule(7);

        Rule rule = new OrWithThresholdRule(rule1Never, rule2At7, 4);
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
        assertTrue(rule.isSatisfied(10));
    }

    @Test
    public void isSatisfiedWithThresholdEitherRuleSatisfiedAtDifferentTimes() {
        Rule rule1At5 = new FixedRule(5);
        Rule rule2At9 = new FixedRule(9);

        Rule rule = new OrWithThresholdRule(rule1At5, rule2At9, 4);
        assertFalse(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
        assertTrue(rule.isSatisfied(10));
    }

    @Test
    public void isSatisfiedWithThresholdBothRulesOutsideWindow() {
        Rule rule1At2 = new FixedRule(2);
        Rule rule2At3 = new FixedRule(3);

        Rule rule = new OrWithThresholdRule(rule1At2, rule2At3, 3);
        assertFalse(rule.isSatisfied(7));
    }

    @Test
    public void isSatisfiedWhenIndexLessThanThreshold() {
        Rule rule = new OrWithThresholdRule(satisfiedRule, satisfiedRule, 4);
        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
    }

    @Test
    public void isSatisfiedWhenIndexEqualsThresholdMinus1() {
        Rule rule1At4 = new FixedRule(4);
        Rule rule2Never = new BooleanRule(false);

        Rule rule = new OrWithThresholdRule(rule1At4, rule2Never, 4);
        assertTrue(rule.isSatisfied(4));
    }

    @Test
    public void isSatisfiedWithThreshold1() {
        Rule rule1At7 = new FixedRule(7);
        Rule rule2Never = new BooleanRule(false);

        Rule rule = new OrWithThresholdRule(rule1At7, rule2Never, 1);
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertFalse(rule.isSatisfied(8));
    }

    @Test
    public void isSatisfiedWithLargeThreshold() {
        Rule rule1At2 = new FixedRule(2);
        Rule rule2Never = new BooleanRule(false);

        Rule rule = new OrWithThresholdRule(rule1At2, rule2Never, 7);
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void isSatisfiedWithBothRulesSatisfiedAtSameIndex() {
        Rule rule1At7 = new FixedRule(7);
        Rule rule2At7 = new FixedRule(7);

        Rule rule = new OrWithThresholdRule(rule1At7, rule2At7, 4);
        assertFalse(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertTrue(rule.isSatisfied(8));
        assertTrue(rule.isSatisfied(9));
        assertTrue(rule.isSatisfied(10));
    }

    @Test
    public void isSatisfiedEarlyExitOptimization() {
        Rule rule1At5 = new FixedRule(5, 6, 7, 8, 9);
        Rule rule2Never = new BooleanRule(false);

        Rule rule = new OrWithThresholdRule(rule1At5, rule2Never, 4);
        assertTrue(rule.isSatisfied(7));
    }

    @Test
    public void constructorThrowsExceptionWhenThresholdIsZero() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrWithThresholdRule(satisfiedRule, satisfiedRule, 0);
        });
    }

    @Test
    public void constructorThrowsExceptionWhenThresholdIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrWithThresholdRule(satisfiedRule, satisfiedRule, -1);
        });
    }

    @Test
    public void constructorThrowsExceptionWhenRule1IsNull() {
        assertThrows(NullPointerException.class, () -> {
            new OrWithThresholdRule(null, satisfiedRule, 1);
        });
    }

    @Test
    public void constructorThrowsExceptionWhenRule2IsNull() {
        assertThrows(NullPointerException.class, () -> {
            new OrWithThresholdRule(satisfiedRule, null, 1);
        });
    }

    @Test
    public void getRule1ReturnsFirstRule() {
        OrWithThresholdRule rule = new OrWithThresholdRule(satisfiedRule, unsatisfiedRule, 1);
        Rule returnedRule = rule.getRule1();

        assertNotSame(satisfiedRule, returnedRule);
        assertTrue(returnedRule.isSatisfied(0));
    }

    @Test
    public void getRule2ReturnsSecondRule() {
        OrWithThresholdRule rule = new OrWithThresholdRule(satisfiedRule, unsatisfiedRule, 1);
        Rule returnedRule = rule.getRule2();

        assertNotSame(unsatisfiedRule, returnedRule);
        assertFalse(returnedRule.isSatisfied(0));
    }

    @Test
    public void serializeAndDeserialize() {
        Rule composite = new OrWithThresholdRule(satisfiedRule, BooleanRule.TRUE, 3);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, composite);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, composite);
    }

    @Test
    public void traceLoggingVerboseModeEmitsThresholdSummaryAndChildren() {
        FixedRule rule1 = new FixedRule(5);
        rule1.setName("Threshold Rule 1");
        FixedRule rule2 = new FixedRule(7);
        rule2.setName("Threshold Rule 2");
        OrWithThresholdRule rule = new OrWithThresholdRule(rule1, rule2, 4);
        rule.setName("Threshold Or");

        ruleTraceTestLogger.clear();
        rule.isSatisfied(7);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue(logContent.contains("Threshold Or#isSatisfied"), "Verbose mode should log threshold OR");
        assertTrue(logContent.contains("threshold=4"), "Verbose mode should include threshold value");
        assertTrue(logContent.contains("rule1=true"), "Verbose mode should include first child result");
        assertTrue(logContent.contains("Threshold Rule 1#isSatisfied"), "Verbose mode should log first child");
    }

    @Test
    public void traceLoggingSummaryModeEmitsInsufficientBarsFailureContext() {
        OrWithThresholdRule rule = new OrWithThresholdRule(satisfiedRule, satisfiedRule, 4);
        rule.setName("Threshold Or Failure");

        ruleTraceTestLogger.clear();
        rule.isSatisfiedWithTraceMode(2, null, Rule.TraceMode.SUMMARY);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue(logContent.contains("Threshold Or Failure#isSatisfied"),
                "Summary mode should log threshold OR failure");
        assertTrue(logContent.contains("threshold=4"), "Summary mode should include threshold");
        assertTrue(logContent.contains("windowStart=0"), "Summary mode should include window start");
        assertTrue(logContent.contains("windowEnd=2"), "Summary mode should include window end");
        assertTrue(logContent.contains("reason=insufficientBars"),
                "Summary mode should include insufficient bars reason");
    }

    @Test
    public void doesNotScanWindowBeforeSeriesBeginIndex() {
        BarSeries trimmedSeries = new MockBarSeriesBuilder().withData(10, 10, 10, 1, 10, 10, 10, 10, 10, 10).build();
        trimmedSeries.setMaximumBarCount(5);
        var indicator = new FixedNumIndicator(trimmedSeries, 10, 10, 10, 1, 10, 10, 10, 10, 10, 10);
        Rule child = new UnderIndicatorRule(indicator, trimmedSeries.numFactory().numOf(5));

        Rule rule = new OrWithThresholdRule(child, child, 4);

        assertFalse(rule.isSatisfied(6));
    }
}
