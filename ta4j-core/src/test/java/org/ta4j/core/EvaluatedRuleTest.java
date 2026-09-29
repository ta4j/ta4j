/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.junit.Test;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.VoteRule;

public class EvaluatedRuleTest {

    @Test
    public void isSatisfiedEvaluatesOnceAndProjectsThatExactResult() {
        RecordingRule rule = RecordingRule.constant(true);
        TradingRecord record = new BaseTradingRecord();

        assertTrue(rule.isSatisfied(3, record));

        assertEquals(1, rule.evaluations.get());
        assertEquals(1, rule.coercions.get());
        assertEquals(3, rule.lastIndex);
        assertSame(record, rule.lastRecord);
        assertSame(rule.lastEvaluated, rule.lastCoerced);
    }

    @Test
    public void indexOnlyOverloadsForwardNullRecord() {
        RecordingRule rule = RecordingRule.constant(false);
        rule.lastRecord = new BaseTradingRecord();

        Snapshot snapshot = rule.evaluate(4);

        assertEquals(new Snapshot(4, false), snapshot);
        assertNull(rule.lastRecord);
        assertEquals(0, rule.coercions.get());

        rule.lastRecord = new BaseTradingRecord();
        assertFalse(rule.isSatisfied(5));
        assertEquals(5, rule.lastIndex);
        assertNull(rule.lastRecord);
        assertEquals(2, rule.evaluations.get());
        assertEquals(1, rule.coercions.get());
    }

    @Test
    public void nullEvaluationFailsBeforeCoercion() {
        RecordingRule rule = new RecordingRule((index, record) -> null, Snapshot::satisfied);

        NullPointerException error = assertThrows(NullPointerException.class, () -> rule.isSatisfied(0, null));

        assertEquals("evaluate(...) must not return null", error.getMessage());
        assertEquals(1, rule.evaluations.get());
        assertEquals(0, rule.coercions.get());
    }

    @Test
    public void evaluationAndCoercionFailuresPropagate() {
        IllegalStateException evaluationFailure = new IllegalStateException("evaluate failed");
        RecordingRule failingEvaluation = new RecordingRule((index, record) -> {
            throw evaluationFailure;
        }, Snapshot::satisfied);
        assertSame(evaluationFailure,
                assertThrows(IllegalStateException.class, () -> failingEvaluation.isSatisfied(0)));
        assertEquals(0, failingEvaluation.coercions.get());

        IllegalStateException coercionFailure = new IllegalStateException("coercion failed");
        RecordingRule failingCoercion = new RecordingRule((index, record) -> new Snapshot(index, true), snapshot -> {
            throw coercionFailure;
        });
        assertSame(coercionFailure, assertThrows(IllegalStateException.class, () -> failingCoercion.isSatisfied(0)));
        assertEquals(1, failingCoercion.evaluations.get());
    }

    @Test
    public void mixedCompositionPreservesTruthTablesAndShortCircuitInBothOrders() {
        for (boolean richValue : List.of(true, false)) {
            for (boolean legacyValue : List.of(true, false)) {
                RecordingRule rich = RecordingRule.constant(richValue);
                CountingLegacyRule legacy = new CountingLegacyRule(legacyValue);

                assertEquals(richValue && legacyValue, rich.and(legacy).isSatisfied(1));
                assertEquals(1, rich.evaluations.get());
                assertEquals(richValue ? 1 : 0, legacy.calls.get());

                assertEquals(legacyValue && richValue, legacy.and(rich).isSatisfied(1));
                assertEquals(legacyValue ? 2 : 1, rich.evaluations.get());

                rich.resetCounts();
                legacy.calls.set(0);
                assertEquals(richValue || legacyValue, rich.or(legacy).isSatisfied(1));
                assertEquals(richValue ? 0 : 1, legacy.calls.get());
                assertEquals(legacyValue || richValue, legacy.or(rich).isSatisfied(1));
                assertEquals(legacyValue ? 1 : 2, rich.evaluations.get());

                rich.resetCounts();
                legacy.calls.set(0);
                assertEquals(richValue ^ legacyValue, rich.xor(legacy).isSatisfied(1));
                assertEquals(legacyValue ^ richValue, legacy.xor(rich).isSatisfied(1));
                assertEquals(2, rich.evaluations.get());
                assertEquals(2, legacy.calls.get());
                assertEquals(2, rich.coercions.get());

                assertEquals(!richValue, rich.negation().isSatisfied(1));
            }
        }
    }

    @Test
    public void compositionForwardsExactRecordToEvaluatedChild() {
        RecordingRule rich = RecordingRule.constant(true);
        TradingRecord record = new BaseTradingRecord();

        assertTrue(BooleanRule.TRUE.and(rich).isSatisfied(7, record));

        assertEquals(7, rich.lastIndex);
        assertSame(record, rich.lastRecord);
    }

    @Test
    public void voteRuleCountsEvaluatedRuleAsOneVote() {
        RecordingRule rich = RecordingRule.constant(true);
        CountingLegacyRule legacyTrue = new CountingLegacyRule(true);
        CountingLegacyRule legacyFalse = new CountingLegacyRule(false);

        assertTrue(new VoteRule(2, rich, legacyFalse, legacyTrue).isSatisfied(2));
        assertFalse(new VoteRule(3, rich, legacyFalse, legacyTrue).isSatisfied(2));
        assertEquals(2, rich.evaluations.get());
        assertEquals(2, rich.coercions.get());
    }

    @Test
    public void evaluatedRulesDriveBaseStrategyBacktestWithTheManagerRecord() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6).build();
        RecordingRule entry = new RecordingRule((index, record) -> new Snapshot(index, index == 1),
                Snapshot::satisfied);
        // Exit two bars after the recorded entry: the result depends on the forwarded
        // trading record, not on a captured reference.
        RecordingRule exit = new RecordingRule((index, record) -> {
            Trade lastEntry = record.getLastEntry();
            return new Snapshot(index, lastEntry != null && index - lastEntry.getIndex() >= 2);
        }, Snapshot::satisfied);
        Rule entryRule = entry;

        TradingRecord record = new BarSeriesManager(series, new TradeOnCurrentCloseModel())
                .run(new BaseStrategy(entryRule, exit));

        assertEquals(1, record.getPositionCount());
        assertEquals(1, record.getPositions().get(0).getEntry().getIndex());
        assertEquals(3, record.getPositions().get(0).getExit().getIndex());
        assertSame(record, exit.lastRecord);
        assertEquals(entry.evaluations.get(), entry.coercions.get());
        assertEquals(exit.evaluations.get(), exit.coercions.get());
    }

    @Test
    public void capturedSnapshotIgnoresLaterRecordMutation() {
        BaseTradingRecord record = new BaseTradingRecord();
        RecordingRule flat = new RecordingRule((index, tradingRecord) -> new Snapshot(index, tradingRecord.isClosed()),
                Snapshot::satisfied);
        Snapshot beforeEntry = flat.evaluate(0, record);

        record.enter(0);

        assertTrue(flat.toBoolean(beforeEntry));
        assertFalse(flat.isSatisfied(1, record));
    }

    private record Snapshot(int index, boolean satisfied) {
    }

    private static final class RecordingRule implements EvaluatedRule<Snapshot> {

        private final BiFunction<Integer, TradingRecord, Snapshot> evaluation;
        private final Predicate<Snapshot> coercion;
        private final AtomicInteger evaluations = new AtomicInteger();
        private final AtomicInteger coercions = new AtomicInteger();
        private int lastIndex = -1;
        private TradingRecord lastRecord;
        private Snapshot lastEvaluated;
        private Snapshot lastCoerced;

        private RecordingRule(BiFunction<Integer, TradingRecord, Snapshot> evaluation, Predicate<Snapshot> coercion) {
            this.evaluation = evaluation;
            this.coercion = coercion;
        }

        private static RecordingRule constant(boolean satisfied) {
            return new RecordingRule((index, record) -> new Snapshot(index, satisfied), Snapshot::satisfied);
        }

        @Override
        public Snapshot evaluate(int index, TradingRecord tradingRecord) {
            evaluations.incrementAndGet();
            lastIndex = index;
            lastRecord = tradingRecord;
            lastEvaluated = evaluation.apply(index, tradingRecord);
            return lastEvaluated;
        }

        @Override
        public boolean toBoolean(Snapshot result) {
            coercions.incrementAndGet();
            lastCoerced = result;
            return coercion.test(result);
        }

        private void resetCounts() {
            evaluations.set(0);
            coercions.set(0);
        }
    }

    private static final class CountingLegacyRule implements Rule {

        private final boolean satisfied;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingLegacyRule(boolean satisfied) {
            this.satisfied = satisfied;
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            calls.incrementAndGet();
            return satisfied;
        }
    }
}
