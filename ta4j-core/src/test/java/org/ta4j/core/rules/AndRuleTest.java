/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class AndRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    private BarSeries series;
    private RuleTraceTestLogger ruleTraceTestLogger;

    @Before
    public void setUp() {
        ruleTraceTestLogger = new RuleTraceTestLogger();
        ruleTraceTestLogger.open();

        satisfiedRule = new BooleanRule(true);
        unsatisfiedRule = new BooleanRule(false);
        series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
    }

    @After
    public void tearDownLogger() {
        ruleTraceTestLogger.close();
    }

    @Test
    public void isSatisfied() {
        assertFalse(satisfiedRule.and(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.and(satisfiedRule).isSatisfied(0));
        assertFalse(unsatisfiedRule.and(BooleanRule.FALSE).isSatisfied(0));
        assertFalse(BooleanRule.FALSE.and(unsatisfiedRule).isSatisfied(0));

        assertTrue(satisfiedRule.and(BooleanRule.TRUE).isSatisfied(10));
        assertTrue(BooleanRule.TRUE.and(satisfiedRule).isSatisfied(10));
        assertFalse(unsatisfiedRule.and(BooleanRule.TRUE).isSatisfied(10));
        assertFalse(BooleanRule.TRUE.and(unsatisfiedRule).isSatisfied(10));
    }

    @Test
    public void traceLoggingRollupModeSuppressesChildRuleLogs() {
        Rule rule1 = new FixedRule(1);
        rule1.setName("Entry Rule");
        rule1.setTraceMode(Rule.TraceMode.VERBOSE);
        Rule rule2 = new FixedRule(1, 2);
        rule2.setName("Exit Rule");
        rule2.setTraceMode(Rule.TraceMode.VERBOSE);

        AndRule andRule = new AndRule(rule1, rule2);
        andRule.setName("EntryAndExit");
        andRule.setTraceMode(Rule.TraceMode.ROLLUP);

        ruleTraceTestLogger.clear();
        andRule.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Rollup mode should still log the parent composite rule",
                logContent.contains("EntryAndExit#isSatisfied"));
        assertFalse("Rollup mode should suppress child rule logs", logContent.contains("Entry Rule#isSatisfied"));
        assertFalse("Rollup mode should suppress child rule logs", logContent.contains("Exit Rule#isSatisfied"));
        assertEquals("Rollup mode should not mutate first child trace mode", Rule.TraceMode.VERBOSE,
                rule1.getTraceMode());
        assertEquals("Rollup mode should not mutate second child trace mode", Rule.TraceMode.VERBOSE,
                rule2.getTraceMode());
    }

    @Test
    public void traceLoggingVerboseModePreservesChildRuleLogs() {
        Rule rule1 = new FixedRule(1);
        rule1.setName("Rule 1");
        Rule rule2 = new FixedRule(2);
        rule2.setName("Rule 2");

        AndRule andRule = new AndRule(rule1, rule2);
        andRule.setName("Rule Pair");
        andRule.setTraceMode(Rule.TraceMode.VERBOSE);

        ruleTraceTestLogger.clear();
        andRule.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Verbose mode should log the parent composite rule", logContent.contains("Rule Pair#isSatisfied"));
        assertTrue("Verbose mode should keep child rule logs", logContent.contains("Rule 1#isSatisfied"));
        assertTrue("Verbose mode should keep child rule logs", logContent.contains("Rule 2#isSatisfied"));
    }

    @Test
    public void traceLoggingDoesNotMutateSharedChildRuleDuringConcurrentCompositeEvaluation() throws Exception {
        BlockingFixedRule sharedChild = new BlockingFixedRule(1, 2);
        sharedChild.setTraceMode(Rule.TraceMode.OFF);

        AndRule verboseParent = new AndRule(sharedChild, BooleanRule.TRUE);
        verboseParent.setTraceMode(Rule.TraceMode.VERBOSE);
        AndRule rollupParent = new AndRule(sharedChild, BooleanRule.TRUE);
        rollupParent.setTraceMode(Rule.TraceMode.ROLLUP);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var verboseEvaluation = executor.submit(() -> verboseParent.isSatisfied(1));
            var rollupEvaluation = executor.submit(() -> rollupParent.isSatisfied(1));

            assertTrue("Both parent evaluations should reach the shared child",
                    sharedChild.awaitEntered(5, TimeUnit.SECONDS));
            assertEquals("Shared child trace mode must not be mutated while evaluations are in flight",
                    Rule.TraceMode.OFF, sharedChild.getTraceMode());

            sharedChild.release();

            assertTrue(verboseEvaluation.get(5, TimeUnit.SECONDS));
            assertTrue(rollupEvaluation.get(5, TimeUnit.SECONDS));
            assertEquals("Shared child trace mode must remain unchanged after concurrent evaluations",
                    Rule.TraceMode.OFF, sharedChild.getTraceMode());
            assertTrue("Shared child must only observe its own configured trace mode",
                    sharedChild.observedModes().stream().allMatch(Rule.TraceMode.OFF::equals));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void traceLoggingUsesPathDepthToDisambiguateRepeatedChildInstances() {
        FixedRule repeatedChild = new FixedRule(1);
        repeatedChild.setName("Repeated Child");
        AndRule leftBranch = new AndRule(repeatedChild, new FixedRule(1));
        leftBranch.setName("Same Label");
        AndRule rightBranch = new AndRule(repeatedChild, new FixedRule(1));
        rightBranch.setName("Same Label");
        AndRule root = new AndRule(leftBranch, rightBranch);
        root.setName("Root");
        root.setTraceMode(Rule.TraceMode.VERBOSE);

        ruleTraceTestLogger.clear();
        root.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Left repeated child should have a unique path", logContent.contains(
                "Repeated Child#isSatisfied(1): true traceMode=VERBOSE ruleType=FixedRule path=root.rule1.rule1 depth=2"));
        assertTrue("Right repeated child should have a unique path", logContent.contains(
                "Repeated Child#isSatisfied(1): true traceMode=VERBOSE ruleType=FixedRule path=root.rule2.rule1 depth=2"));
        assertTrue("Repeated child events should retain parent attribution", logContent.contains("parent=Same Label"));
    }

    @Test
    public void traceLoggingCanBeScopedToSingleCompositeEvaluationWithoutMutatingRuleModes() {
        FixedRule rule1 = new FixedRule(1);
        rule1.setName("Scoped Child 1");
        rule1.setTraceMode(Rule.TraceMode.VERBOSE);
        FixedRule rule2 = new FixedRule(1);
        rule2.setName("Scoped Child 2");
        rule2.setTraceMode(Rule.TraceMode.VERBOSE);
        AndRule andRule = new AndRule(rule1, rule2);
        andRule.setName("Scoped Parent");

        ruleTraceTestLogger.clear();
        assertTrue(andRule.isSatisfiedWithTraceMode(1, null, Rule.TraceMode.ROLLUP));

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Scoped rollup evaluation should log the parent", logContent.contains("Scoped Parent#isSatisfied"));
        assertFalse("Scoped rollup evaluation should suppress first child logs",
                logContent.contains("Scoped Child 1#isSatisfied"));
        assertFalse("Scoped rollup evaluation should suppress second child logs",
                logContent.contains("Scoped Child 2#isSatisfied"));
        assertEquals("Scoped evaluation should not mutate parent mode", Rule.TraceMode.OFF, andRule.getTraceMode());
        assertEquals("Scoped evaluation should not mutate first child mode", Rule.TraceMode.VERBOSE,
                rule1.getTraceMode());
        assertEquals("Scoped evaluation should not mutate second child mode", Rule.TraceMode.VERBOSE,
                rule2.getTraceMode());
    }

    @Test
    public void serializeAndDeserialize() {
        Rule composite = satisfiedRule.and(BooleanRule.TRUE);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, composite);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, composite);
    }

    private static final class BlockingFixedRule extends FixedRule {

        private final CountDownLatch entered;
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Rule.TraceMode> observedModes = new CopyOnWriteArrayList<>();

        private BlockingFixedRule(int index, int expectedEntrants) {
            super(index);
            entered = new CountDownLatch(expectedEntrants);
        }

        @Override
        public boolean isSatisfied(int index, org.ta4j.core.TradingRecord tradingRecord) {
            observedModes.add(getTraceMode());
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return super.isSatisfied(index, tradingRecord);
        }

        private boolean awaitEntered(long timeout, TimeUnit unit) throws InterruptedException {
            return entered.await(timeout, unit);
        }

        private void release() {
            release.countDown();
        }

        private List<Rule.TraceMode> observedModes() {
            return observedModes;
        }
    }
}
