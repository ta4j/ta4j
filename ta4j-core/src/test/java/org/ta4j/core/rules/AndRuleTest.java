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

import org.apache.logging.log4j.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class AndRuleTest {

    private Rule satisfiedRule;
    private Rule unsatisfiedRule;
    private BarSeries series;
    private TraceTestLogger ruleTraceTestLogger;

    @Before
    public void setUp() {
        ruleTraceTestLogger = new TraceTestLogger();
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
    public void traceLoggingSummaryModeSuppressesChildRuleLogs() {
        Rule rule1 = new FixedRule(1);
        rule1.setName("Entry Rule");
        Rule rule2 = new FixedRule(1, 2);
        rule2.setName("Exit Rule");

        AndRule andRule = new AndRule(rule1, rule2);
        andRule.setName("EntryAndExit");

        ruleTraceTestLogger.clear();
        andRule.isSatisfiedWithTraceMode(1, null, Rule.TraceMode.SUMMARY);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Summary mode should still log the parent composite rule",
                logContent.contains("EntryAndExit#isSatisfied"));
        assertFalse("Summary mode should suppress child rule logs", logContent.contains("Entry Rule#isSatisfied"));
        assertFalse("Summary mode should suppress child rule logs", logContent.contains("Exit Rule#isSatisfied"));
    }

    @Test
    public void traceLoggingVerboseModePreservesChildRuleLogs() {
        Rule rule1 = new FixedRule(1);
        rule1.setName("Rule 1");
        Rule rule2 = new FixedRule(2);
        rule2.setName("Rule 2");

        AndRule andRule = new AndRule(rule1, rule2);
        andRule.setName("Rule Pair");

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

        AndRule verboseParent = new AndRule(sharedChild, BooleanRule.TRUE);
        AndRule summaryParent = new AndRule(sharedChild, BooleanRule.TRUE);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var verboseEvaluation = executor
                    .submit(() -> verboseParent.isSatisfiedWithTraceMode(1, null, Rule.TraceMode.VERBOSE));
            var summaryEvaluation = executor
                    .submit(() -> summaryParent.isSatisfiedWithTraceMode(1, null, Rule.TraceMode.SUMMARY));

            assertTrue("Both parent evaluations should reach the shared child",
                    sharedChild.awaitEntered(5, TimeUnit.SECONDS));

            sharedChild.release();

            assertTrue(verboseEvaluation.get(5, TimeUnit.SECONDS));
            assertTrue(summaryEvaluation.get(5, TimeUnit.SECONDS));
            assertTrue("Shared child should observe the verbose parent scope",
                    sharedChild.observedModes().contains("VERBOSE"));
            assertTrue("Shared child should observe summary child suppression",
                    sharedChild.observedModes().contains("null"));
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

        ruleTraceTestLogger.clear();
        root.isSatisfied(1);

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Left repeated child should have a unique path", logContent.contains(
                "Repeated Child#isSatisfied(1): true mode=VERBOSE ruleType=FixedRule path=root.rule1.rule1 depth=2"));
        assertTrue("Right repeated child should have a unique path", logContent.contains(
                "Repeated Child#isSatisfied(1): true mode=VERBOSE ruleType=FixedRule path=root.rule2.rule1 depth=2"));
        assertTrue("Repeated child events should retain parent attribution", logContent.contains("parent=Same Label"));
    }

    @Test
    public void traceLoggingCanBeScopedToSingleCompositeEvaluationWithoutMutatingRuleModes() {
        FixedRule rule1 = new FixedRule(1);
        rule1.setName("Scoped Child 1");
        FixedRule rule2 = new FixedRule(1);
        rule2.setName("Scoped Child 2");
        AndRule andRule = new AndRule(rule1, rule2);
        andRule.setName("Scoped Parent");

        ruleTraceTestLogger.clear();
        assertTrue(andRule.isSatisfiedWithTraceMode(1, null, Rule.TraceMode.SUMMARY));

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue("Scoped summary evaluation should log the parent", logContent.contains("Scoped Parent#isSatisfied"));
        assertFalse("Scoped summary evaluation should suppress first child logs",
                logContent.contains("Scoped Child 1#isSatisfied"));
        assertFalse("Scoped summary evaluation should suppress second child logs",
                logContent.contains("Scoped Child 2#isSatisfied"));

        ruleTraceTestLogger.clear();
        assertTrue(andRule.isSatisfied(1));
        String defaultTrace = ruleTraceTestLogger.getLogOutput();
        assertTrue("A scoped summary evaluation should not suppress later default child traces",
                defaultTrace.contains("Scoped Child 1#isSatisfied"));
    }

    @Test
    public void traceLoggingDoesNotCreateParentScopeWhenCompositeLoggerTraceIsDisabled() {
        FixedRule childRule = new FixedRule(1);
        childRule.setName("Composite Child");

        AndRule andRule = new AndRule(childRule, BooleanRule.TRUE);
        andRule.setName("Composite Parent");

        ruleTraceTestLogger.setLoggerLevel(AndRule.class, Level.INFO);
        ruleTraceTestLogger.setLoggerLevel(FixedRule.class, Level.TRACE);
        ruleTraceTestLogger.clear();

        assertTrue(andRule.isSatisfied(1));

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertFalse("Composite should not emit parent logs when the composite logger is not tracing",
                logContent.contains("Composite Parent#isSatisfied"));
        assertTrue("A TRACE-enabled child logger should still emit its own default trace",
                logContent.contains("Composite Child#isSatisfied"));
        assertTrue("Child trace should not inherit a parent frame when the composite logger is not tracing",
                logContent.contains("path=root depth=0"));
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
        private final List<String> observedModes = new CopyOnWriteArrayList<>();

        private BlockingFixedRule(int index, int expectedEntrants) {
            super(index);
            entered = new CountDownLatch(expectedEntrants);
        }

        @Override
        public boolean isSatisfied(int index, org.ta4j.core.TradingRecord tradingRecord) {
            RuleTraceContext.Frame frame = RuleTraceContext.currentFrame();
            observedModes.add(frame == null ? "none" : String.valueOf(frame.traceMode()));
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

        private List<String> observedModes() {
            return observedModes;
        }
    }
}
