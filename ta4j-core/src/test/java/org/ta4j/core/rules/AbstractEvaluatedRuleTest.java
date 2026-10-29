/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.EvaluatedRule;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.RuleSerialization;
import org.ta4j.core.serialization.RuleSerializationException;

public class AbstractEvaluatedRuleTest {

    private TraceTestLogger traceLogger;
    private BarSeries series;
    private ClosePriceIndicator close;

    @Before
    public void setUp() {
        traceLogger = new TraceTestLogger();
        traceLogger.open();
        series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        close = new ClosePriceIndicator(series);
    }

    @After
    public void tearDown() {
        traceLogger.close();
    }

    @Test
    public void booleanBridgeEvaluatesProjectsAndTracesOnce() {
        ThresholdGateRule rule = new ThresholdGateRule(close, 3);
        rule.setName("Rich gate");
        traceLogger.clear();

        assertTrue(rule.isSatisfied(2, null));

        assertEquals(1, rule.evaluations.get());
        assertEquals(1, rule.coercions.get());
        assertEquals(1, occurrences(traceLogger.getLogOutput(), "Rich gate#isSatisfied"));
    }

    @Test
    public void directEvaluationDoesNotEmitBooleanTrace() {
        ThresholdGateRule rule = new ThresholdGateRule(close, 3);
        rule.setName("Rich gate");
        traceLogger.clear();

        Gate gate = rule.evaluate(1);

        assertFalse(rule.toBoolean(gate));
        assertFalse(traceLogger.getLogOutput().contains("Rich gate#isSatisfied"));
    }

    @Test
    public void summaryTraceModeSuppressesEvaluatedChildTrace() {
        ThresholdGateRule rich = new ThresholdGateRule(close, 3);
        rich.setName("Rich gate");
        Rule composite = BooleanRule.TRUE.and(rich);
        composite.setName("Composite");
        traceLogger.clear();

        assertTrue(composite.isSatisfiedWithTraceMode(4, null, Rule.TraceMode.SUMMARY));

        String output = traceLogger.getLogOutput();
        assertTrue(output.contains("Composite#isSatisfied"));
        assertFalse(output.contains("Rich gate#isSatisfied"));
        assertEquals(1, rich.evaluations.get());
    }

    @Test
    public void nullEvaluationFailsWithoutTraceOrCoercion() {
        NullResultRule rule = new NullResultRule();
        traceLogger.clear();

        NullPointerException error = assertThrows(NullPointerException.class, () -> rule.isSatisfied(0));

        assertEquals("evaluate(...) must not return null", error.getMessage());
        assertEquals(0, rule.coercions.get());
        assertFalse(traceLogger.getLogOutput().contains("#isSatisfied"));
    }

    @Test
    public void namingFollowsAbstractRuleConventions() {
        ThresholdGateRule rule = new ThresholdGateRule(close, 1);

        assertEquals("ThresholdGateRule", rule.getName());
        assertFalse(rule.hasCustomName());

        rule.setName("Close gate");
        assertEquals("Close gate", rule.getName());
        assertEquals("Close gate", rule.toString());
    }

    @Test
    public void capturedSnapshotIgnoresCurrentBarMutationAndFreshEvaluationSeesIt() {
        ThresholdGateRule rule = new ThresholdGateRule(close, 5);
        int endIndex = series.getEndIndex();
        Gate beforeMutation = rule.evaluate(endIndex);

        series.addPrice(series.numFactory().numOf(4));

        assertTrue(rule.toBoolean(beforeMutation));
        assertEquals(series.numFactory().numOf(5), beforeMutation.value());
        Gate afterMutation = rule.evaluate(endIndex);
        assertEquals(series.numFactory().numOf(4), afterMutation.value());
        assertFalse(rule.toBoolean(afterMutation));
    }

    @Test
    public void thresholdEndpointIsInclusiveFromNonZeroBeginIndex() {
        series.setMaximumBarCount(3);
        int beginIndex = series.getBeginIndex();
        ThresholdGateRule rule = new ThresholdGateRule(close, 3);

        assertEquals(2, beginIndex);
        assertTrue(rule.isSatisfied(beginIndex));
        assertEquals(series.numFactory().numOf(3), rule.evaluate(beginIndex).value());
        assertTrue(rule.isSatisfied(beginIndex + 1));
    }

    @Test
    public void constructorBackedRuleRoundTripsConfigurationButNotSnapshots() {
        ThresholdGateRule rule = new ThresholdGateRule(close, 3);
        rule.setName("Close gate");
        rule.isSatisfied(4);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertEquals(2, descriptor.getParameters().size());
        assertEquals("3", String.valueOf(descriptor.getParameters().get("threshold")));
        assertEquals("Close gate", descriptor.getParameters().get("__customName"));
        assertEquals(1, descriptor.getComponents().size());
        assertEquals("ClosePriceIndicator", descriptor.getComponents().get(0).getType());

        assertTrue(restored instanceof ThresholdGateRule);
        ThresholdGateRule restoredGate = (ThresholdGateRule) restored;
        assertEquals("Close gate", restoredGate.getName());
        assertEquals(0, restoredGate.evaluations.get());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertEquals(rule.evaluate(i), restoredGate.evaluate(i));
        }

        Rule jsonRestored = RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
        assertTrue(jsonRestored instanceof EvaluatedRule<?>);
    }

    @Test
    public void ruleCopiesReconstructsSupportedEvaluatedRules() {
        ThresholdGateRule rule = new ThresholdGateRule(close, 3);
        rule.setName("Close gate");

        Rule copy = RuleCopies.copy(rule);

        assertNotSame(rule, copy);
        assertTrue(copy instanceof ThresholdGateRule);
        assertEquals("Close gate", copy.getName());
        assertEquals(rule.isSatisfied(2), copy.isSatisfied(2));
    }

    @Test
    public void unsupportedCustomEvaluatedRuleKeepsExistingFallbacks() {
        LambdaGateRule rule = new LambdaGateRule(close, value -> value.isGreaterThan(value.getNumFactory().two()));

        assertFalse(RuleSerialization.isSerializationSupported(rule));
        assertThrows(RuleSerializationException.class, rule::toJson);
        assertSame(rule, RuleCopies.copy(rule));
        assertTrue(rule.isSatisfied(2));
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        for (int from = text.indexOf(token); from >= 0; from = text.indexOf(token, from + token.length())) {
            count++;
        }
        return count;
    }

    record Gate(Num value, Num threshold) {
    }

    private static final class NullResultRule extends AbstractEvaluatedRule<Gate> {

        private final AtomicInteger coercions = new AtomicInteger();

        @Override
        public Gate evaluate(int index, TradingRecord tradingRecord) {
            return null;
        }

        @Override
        public boolean toBoolean(Gate result) {
            coercions.incrementAndGet();
            return true;
        }
    }

    private static final class LambdaGateRule extends AbstractEvaluatedRule<Num> {

        private final Indicator<Num> indicator;
        private final Predicate<Num> predicate;

        LambdaGateRule(Indicator<Num> indicator, Predicate<Num> predicate) {
            this.indicator = indicator;
            this.predicate = predicate;
        }

        @Override
        public Num evaluate(int index, TradingRecord tradingRecord) {
            return indicator.getValue(index);
        }

        @Override
        public boolean toBoolean(Num result) {
            return predicate.test(result);
        }
    }
}

/**
 * Constructor-backed evaluated rule fixture. Top level so rule serialization
 * can resolve it by simple name in {@code org.ta4j.core.rules}.
 */
final class ThresholdGateRule extends AbstractEvaluatedRule<AbstractEvaluatedRuleTest.Gate> {

    private final Indicator<Num> indicator;
    private final Num threshold;
    final transient AtomicInteger evaluations = new AtomicInteger();
    final transient AtomicInteger coercions = new AtomicInteger();

    ThresholdGateRule(Indicator<Num> indicator, Number threshold) {
        this.indicator = indicator;
        this.threshold = indicator.getBarSeries().numFactory().numOf(threshold);
    }

    @Override
    public AbstractEvaluatedRuleTest.Gate evaluate(int index, TradingRecord tradingRecord) {
        evaluations.incrementAndGet();
        return new AbstractEvaluatedRuleTest.Gate(indicator.getValue(index), threshold);
    }

    @Override
    public boolean toBoolean(AbstractEvaluatedRuleTest.Gate result) {
        coercions.incrementAndGet();
        return result.value().isGreaterThanOrEqual(result.threshold());
    }
}
