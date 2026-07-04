/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.rules.helper.ChainLink;

public class ChainRuleTest {

    private ChainRule chainRule;
    private BarSeries series;
    private TraceTestLogger ruleTraceTestLogger;

    @Before
    public void setUp() {
        ruleTraceTestLogger = new TraceTestLogger();
        ruleTraceTestLogger.open();

        series = new MockBarSeriesBuilder().build();
        var indicator = new FixedNumIndicator(series, 6, 5, 8, 5, 1, 10, 2, 30);
        var underIndicatorRule = new UnderIndicatorRule(indicator, series.numFactory().numOf(5));
        var overIndicatorRule = new OverIndicatorRule(indicator, 7);
        var isEqualRule = new IsEqualRule(indicator, 5);
        chainRule = new ChainRule(underIndicatorRule, new ChainLink(overIndicatorRule, 3),
                new ChainLink(isEqualRule, 2));
    }

    @After
    public void tearDownLogger() {
        ruleTraceTestLogger.close();
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

    @Test
    public void chainLinkUsesDescriptorSerializationOnly() {
        assertFalse(new ChainLink(new FixedRule(1), 2) instanceof Serializable);
    }

    @Test
    public void chainConstructorRejectsNullRulesAndLinks() {
        FixedRule initial = new FixedRule(1);
        ChainLink link = new ChainLink(new FixedRule(2), 1);

        assertThrows(NullPointerException.class, () -> new ChainRule(null, link));
        assertThrows(NullPointerException.class, () -> new ChainRule(initial, (ChainLink[]) null));
        assertThrows(NullPointerException.class, () -> new ChainRule(initial, link, null));
        assertThrows(NullPointerException.class, () -> new ChainLink(null, 1));
        assertThrows(NullPointerException.class, () -> link.setRule(null));
    }

    @Test
    public void chainLinkGetRuleReturnsCopies() {
        FixedRule rule = new FixedRule(2);
        ChainLink link = new ChainLink(rule, 1);

        Rule firstRule = link.getRule();
        Rule secondRule = link.getRule();

        assertNotSame(rule, firstRule);
        assertNotSame(firstRule, secondRule);
        assertTrue(firstRule.isSatisfied(2));
    }

    @Test
    public void traceLoggingSummaryModeSuppressesChildRuleLogs() {
        FixedRule initial = new FixedRule(4);
        initial.setName("Initial");
        FixedRule child = new FixedRule(2);
        child.setName("Chain Child");

        ChainRule testChainRule = new ChainRule(initial, new ChainLink(child, 2));
        testChainRule.setName("Chain Summary");

        ruleTraceTestLogger.clear();
        testChainRule.isSatisfiedWithTraceMode(4, null, Rule.TraceMode.SUMMARY);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Summary mode should log the chain rule", logContent.contains("Chain Summary#isSatisfied"));
        assertFalse("Summary mode should suppress child rule logs", logContent.contains("Initial#isSatisfied"));
        assertFalse("Summary mode should suppress child rule logs", logContent.contains("Chain Child#isSatisfied"));
    }

    @Test
    public void traceLoggingVerboseModePreservesChildRuleLogs() {
        FixedRule initial = new FixedRule(4);
        initial.setName("Initial");
        FixedRule child = new FixedRule(2);
        child.setName("Chain Child");

        ChainRule testChainRule = new ChainRule(initial, new ChainLink(child, 2));
        testChainRule.setName("Chain Verbose");

        ruleTraceTestLogger.clear();
        testChainRule.isSatisfied(4);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Verbose mode should log the chain rule", logContent.contains("Chain Verbose#isSatisfied"));
        assertTrue("Verbose mode should keep child rule logs", logContent.contains("Initial#isSatisfied"));
        assertTrue("Verbose mode should keep child rule logs", logContent.contains("Chain Child#isSatisfied"));
        assertTrue("Verbose mode should attribute child path", logContent.contains("path=root.chainRule0 depth=1"));
    }

    @Test
    public void traceLoggingSummaryModeIncludesFailureContext() {
        FixedRule initial = new FixedRule(4);
        FixedRule child = new FixedRule(0);

        ChainRule testChainRule = new ChainRule(initial, new ChainLink(child, 1));
        testChainRule.setName("Chain Failure");

        ruleTraceTestLogger.clear();
        testChainRule.isSatisfiedWithTraceMode(4, null, Rule.TraceMode.SUMMARY);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Summary failure should log the chain rule", logContent.contains("Chain Failure#isSatisfied"));
        assertTrue("Summary failure should include failed chain rule", logContent.contains("failedChainRule=0"));
        assertTrue("Summary failure should include threshold", logContent.contains("threshold=1"));
    }
}
