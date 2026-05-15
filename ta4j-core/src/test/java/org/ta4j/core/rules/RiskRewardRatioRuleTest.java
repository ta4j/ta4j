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
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class RiskRewardRatioRuleTest {

    private BarSeries series;
    private TraceTestLogger traceTestLogger;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        traceTestLogger = new TraceTestLogger();
        traceTestLogger.open();
    }

    @After
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
        assertTrue("Trace should include the current price", logContent.contains("currentPrice=150"));
        assertTrue("Trace should include the stop price", logContent.contains("stopPrice=120"));
        assertTrue("Trace should include the target price", logContent.contains("targetPrice=240"));
        assertTrue("Trace should include the computed risk", logContent.contains("risk=30"));
        assertTrue("Trace should include the computed reward", logContent.contains("reward=90"));
        assertTrue("Trace should include the risk/reward ratio", logContent.contains("riskReward=3"));
        assertTrue("Trace should explain the risk/reward result", logContent.contains("reason=riskRewardMet"));
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
