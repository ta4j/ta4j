/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class AndRuleTest {

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
        series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
    }

    @AfterEach
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
        assertTrue(logContent.contains("EntryAndExit#isSatisfied"),
                "Summary mode should still log the parent composite rule");
        assertFalse(logContent.contains("Entry Rule#isSatisfied"), "Summary mode should suppress child rule logs");
        assertFalse(logContent.contains("Exit Rule#isSatisfied"), "Summary mode should suppress child rule logs");
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
        assertTrue(logContent.contains("Rule Pair#isSatisfied"), "Verbose mode should log the parent composite rule");
        assertTrue(logContent.contains("Rule 1#isSatisfied"), "Verbose mode should keep child rule logs");
        assertTrue(logContent.contains("Rule 2#isSatisfied"), "Verbose mode should keep child rule logs");
    }

    @Test
    public void traceLoggingDoesNotMutateSharedChildRuleDuringConcurrentCompositeEvaluation() throws Exception {
        BlockingFixedRule sharedChild = new BlockingFixedRule(1, 2);

        AndRule verboseParent = new AndRule(sharedChild, BooleanRule.TRUE);
        AndRule summaryParent = new AndRule(sharedChild, BooleanRule.TRUE);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> verboseEvaluation = executor
                    .submit(() -> verboseParent.isSatisfiedWithTraceMode(1, null, Rule.TraceMode.VERBOSE));
            Future<Boolean> summaryEvaluation = executor
                    .submit(() -> summaryParent.isSatisfiedWithTraceMode(1, null, Rule.TraceMode.SUMMARY));

            assertTrue(sharedChild.awaitEntered(5, TimeUnit.SECONDS),
                    "Both parent evaluations should reach the shared child");

            sharedChild.release();

            assertTrue(verboseEvaluation.get(5, TimeUnit.SECONDS));
            assertTrue(summaryEvaluation.get(5, TimeUnit.SECONDS));
            assertTrue(sharedChild.observedModes().contains("VERBOSE"),
                    "Shared child should observe the verbose parent scope");
            assertTrue(sharedChild.observedModes().contains("null"),
                    "Shared child should observe summary child suppression");
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
        assertTrue(logContent.contains(
                "Repeated Child#isSatisfied(1): true mode=VERBOSE ruleType=FixedRule path=root.rule1.rule1 depth=2"),
                "Left repeated child should have a unique path");
        assertTrue(logContent.contains(
                "Repeated Child#isSatisfied(1): true mode=VERBOSE ruleType=FixedRule path=root.rule2.rule1 depth=2"),
                "Right repeated child should have a unique path");
        assertTrue(logContent.contains("parent=Same Label"), "Repeated child events should retain parent attribution");
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
        assertTrue(logContent.contains("Scoped Parent#isSatisfied"), "Scoped summary evaluation should log the parent");
        assertFalse(logContent.contains("Scoped Child 1#isSatisfied"),
                "Scoped summary evaluation should suppress first child logs");
        assertFalse(logContent.contains("Scoped Child 2#isSatisfied"),
                "Scoped summary evaluation should suppress second child logs");

        ruleTraceTestLogger.clear();
        assertTrue(andRule.isSatisfied(1));
        String defaultTrace = ruleTraceTestLogger.getLogOutput();
        assertTrue(defaultTrace.contains("Scoped Child 1#isSatisfied"),
                "A scoped summary evaluation should not suppress later default child traces");
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
        assertFalse(logContent.contains("Composite Parent#isSatisfied"),
                "Composite should not emit parent logs when the composite logger is not tracing");
        assertTrue(logContent.contains("Composite Child#isSatisfied"),
                "A TRACE-enabled child logger should still emit its own default trace");
        assertTrue(logContent.contains("path=root depth=0"),
                "Child trace should not inherit a parent frame when the composite logger is not tracing");
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
