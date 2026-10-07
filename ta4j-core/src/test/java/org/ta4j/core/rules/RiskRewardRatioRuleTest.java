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
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class RiskRewardRatioRuleTest {

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
    public void bullishRiskRewardThresholds() {
        Indicator<Num> price = new FixedNumIndicator(series, 150, 150);
        Indicator<Num> stop = new FixedNumIndicator(series, 120, 120);
        Indicator<Num> target = new FixedNumIndicator(series, 240, 240);
        RiskRewardRatioRule rule = new RiskRewardRatioRule(price, stop, target, true, 3.0);
        assertTrue(rule.isSatisfied(0));

        RiskRewardRatioRule strictRule = new RiskRewardRatioRule(price, stop, target, true, 4.0);
        assertFalse(strictRule.isSatisfied(0));
    }

    @Test
    public void bearishRiskRewardThresholds() {
        Indicator<Num> price = new FixedNumIndicator(series, 140, 140);
        Indicator<Num> stop = new FixedNumIndicator(series, 180, 180);
        Indicator<Num> target = new FixedNumIndicator(series, 60, 60);
        RiskRewardRatioRule rule = new RiskRewardRatioRule(price, stop, target, false, 2.0);

        assertTrue(rule.isSatisfied(0));
    }

    @Test
    public void traceIncludesPriceStopTargetAndRatio() {
        Indicator<Num> price = new FixedNumIndicator(series, 150);
        Indicator<Num> stop = new FixedNumIndicator(series, 120);
        Indicator<Num> target = new FixedNumIndicator(series, 240);
        RiskRewardRatioRule rule = new RiskRewardRatioRule(price, stop, target, true, 3.0);

        assertTrue(rule.isSatisfiedWithTraceMode(0, Rule.TraceMode.VERBOSE));

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("currentPrice=150"), "Trace should include the current price");
        assertTrue(logContent.contains("stopPrice=120"), "Trace should include the stop price");
        assertTrue(logContent.contains("targetPrice=240"), "Trace should include the target price");
        assertTrue(logContent.contains("risk=30"), "Trace should include the computed risk");
        assertTrue(logContent.contains("reward=90"), "Trace should include the computed reward");
        assertTrue(logContent.contains("riskReward=3"), "Trace should include the risk/reward ratio");
        assertTrue(logContent.contains("reason=riskRewardMet"), "Trace should explain the risk/reward result");
    }

    @Test
    public void serializeAndDeserialize() {
        Indicator<Num> price = new FixedNumIndicator(series, 150);
        Indicator<Num> stop = new FixedNumIndicator(series, 120);
        Indicator<Num> target = new FixedNumIndicator(series, 240);
        RiskRewardRatioRule rule = new RiskRewardRatioRule(price, stop, target, true, 3.0);

        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
