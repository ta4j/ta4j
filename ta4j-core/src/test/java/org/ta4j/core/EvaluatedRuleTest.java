/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.EvaluatedRule.JoinedResult;
import org.ta4j.core.rules.AbstractEvaluatedRule;
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

    @Test
    public void typedTruthTablesEvaluateBothOperandsAndKeepDecisionSeparateFromPayload() {
        for (boolean conjunction : List.of(true, false)) {
            for (boolean leftValue : List.of(true, false)) {
                for (boolean rightValue : List.of(true, false)) {
                    RecordingRule left = RecordingRule.constant(leftValue);
                    RecordingRule right = RecordingRule.constant(rightValue);
                    AtomicInteger joins = new AtomicInteger();
                    BiFunction<Snapshot, Snapshot, Boolean> joiner = (l, r) -> {
                        assertSame(left.lastEvaluated, l);
                        assertSame(right.lastEvaluated, r);
                        joins.incrementAndGet();
                        return false; // A lossy payload must not define the logical decision.
                    };
                    EvaluatedRule<JoinedResult<Boolean>> joined = conjunction ? left.and(right, joiner)
                            : left.or(right, joiner);
                    TradingRecord record = new BaseTradingRecord();
                    JoinedResult<Boolean> result = joined.evaluate(7, record);
                    boolean expected = conjunction ? leftValue && rightValue : leftValue || rightValue;

                    assertFalse(result.value());
                    assertEquals(expected, result.satisfied());
                    assertEquals(expected, joined.toBoolean(result));
                    assertEquals(expected, joined.toBoolean(result));
                    assertEquals(1, left.evaluations.get());
                    assertEquals(1, right.evaluations.get());
                    assertEquals(1, left.coercions.get());
                    assertEquals(1, right.coercions.get());
                    assertEquals(1, joins.get());
                    assertEquals(7, left.lastIndex);
                    assertEquals(7, right.lastIndex);
                    assertSame(record, left.lastRecord);
                    assertSame(record, right.lastRecord);
                }
            }
        }
    }

    @Test
    public void typedEvaluationOrdersProjectionsBeforeJoiningEvenThroughRuleBridge() {
        List<String> calls = new ArrayList<>();
        RecordingRule left = new RecordingRule((index, record) -> {
            calls.add("left evaluate");
            return new Snapshot(index, false);
        }, snapshot -> {
            calls.add("left project");
            return snapshot.satisfied();
        });
        RecordingRule right = new RecordingRule((index, record) -> {
            calls.add("right evaluate");
            return new Snapshot(index, true);
        }, snapshot -> {
            calls.add("right project");
            return snapshot.satisfied();
        });
        Rule joined = left.and(right, (l, r) -> {
            calls.add("join");
            return l.index() + r.index();
        });

        assertFalse(joined.isSatisfied(2));
        assertEquals(List.of("left evaluate", "left project", "right evaluate", "right project", "join"), calls);
        assertNull(left.lastRecord);
        assertNull(right.lastRecord);
    }

    @Test
    public void heterogeneousJoinsChainPayloadsAndCapturedDecisions() {
        RecordingRule left = RecordingRule.constant(false);
        EvaluatedRule<Integer> score = new EvaluatedRule<>() {
            @Override
            public Integer evaluate(int index, TradingRecord record) {
                return index + 2;
            }

            @Override
            public boolean toBoolean(Integer result) {
                return result > 0;
            }
        };
        EvaluatedRule<JoinedResult<Observations>> first = left.and(score, Observations::new);
        EvaluatedRule<JoinedResult<String>> chain = first.or(RecordingRule.constant(true),
                (observations, gate) -> observations.value().score() + ":" + gate.index());

        JoinedResult<String> result = chain.evaluate(7);

        assertEquals("9:7", result.value());
        assertTrue(chain.toBoolean(result));
        assertFalse(first.toBoolean(new JoinedResult<>(new Observations(new Snapshot(7, false), 9), false)));
        assertEquals(1, left.evaluations.get());
        assertEquals(1, left.coercions.get());
    }

    @Test
    public void repeatedOperandOccurrencesAreNotDeduplicated() {
        RecordingRule rule = RecordingRule.constant(true);
        EvaluatedRule<JoinedResult<Integer>> joined = rule.and(rule, (l, r) -> l.index() + r.index());

        assertEquals(new JoinedResult<>(6, true), joined.evaluate(3));
        assertEquals(2, rule.evaluations.get());
        assertEquals(2, rule.coercions.get());
        assertTrue(joined.isSatisfied(3));
        assertEquals(4, rule.evaluations.get());
        assertEquals(4, rule.coercions.get());
    }

    @Test
    public void ordinaryEvaluatedCompositionStillShortCircuits() {
        RecordingRule left = RecordingRule.constant(false);
        RecordingRule right = RecordingRule.constant(true);
        Rule and = left.and(right);
        Rule or = right.or(left);

        assertFalse(and.isSatisfied(0));
        assertEquals(0, right.evaluations.get());
        assertTrue(or.isSatisfied(0));
        assertEquals(1, left.evaluations.get());
        assertEquals(1, right.evaluations.get());
        assertFalse(and instanceof EvaluatedRule<?>);
        assertFalse(or instanceof EvaluatedRule<?>);
    }

    @Test
    public void joinedSnapshotRetainsPayloadAndDecisionAfterRecordMutation() {
        BaseTradingRecord record = new BaseTradingRecord();
        RecordingRule flat = new RecordingRule((index, r) -> new Snapshot(index, r.isClosed()), Snapshot::satisfied);
        EvaluatedRule<JoinedResult<List<Snapshot>>> joined = flat.and(RecordingRule.constant(true), List::of);
        JoinedResult<List<Snapshot>> before = joined.evaluate(0, record);
        record.enter(0);

        assertTrue(joined.toBoolean(before));
        assertEquals(List.of(new Snapshot(0, true), new Snapshot(0, true)), before.value());
        JoinedResult<List<Snapshot>> after = joined.evaluate(1, record);
        assertFalse(joined.toBoolean(after));
        assertEquals(new Snapshot(1, false), after.value().get(0));
    }

    @Test
    public void typedCompositionRejectsNullArgumentsAndOutputs() {
        RecordingRule rule = RecordingRule.constant(true);
        assertThrows(NullPointerException.class, () -> rule.and(null, List::of));
        assertThrows(NullPointerException.class, () -> rule.or(null, List::of));
        assertThrows(NullPointerException.class, () -> rule.and(rule, null));
        assertThrows(NullPointerException.class, () -> rule.or(rule, null));
        assertThrows(NullPointerException.class, () -> new JoinedResult<>(null, true));
        EvaluatedRule<JoinedResult<Object>> nullJoin = rule.and(rule, (l, r) -> null);
        assertThrows(NullPointerException.class, () -> nullJoin.evaluate(0));
        assertThrows(NullPointerException.class, () -> nullJoin.toBoolean(null));
        assertEquals(2, rule.evaluations.get());
    }

    @Test
    public void typedCompositionStopsAtNullOrFailedOperandAndPropagatesJoinerFailure() {
        for (boolean failOnLeft : List.of(true, false)) {
            for (boolean failProjection : List.of(true, false)) {
                RecordingRule good = RecordingRule.constant(true);
                IllegalStateException failure = new IllegalStateException("operand failed");
                RecordingRule bad = new RecordingRule((index, record) -> {
                    if (!failProjection) {
                        throw failure;
                    }
                    return new Snapshot(index, true);
                }, snapshot -> {
                    throw failure;
                });
                AtomicInteger joins = new AtomicInteger();
                EvaluatedRule<JoinedResult<Integer>> joined = (failOnLeft ? bad : good)
                        .or(failOnLeft ? good : bad, (l, r) -> joins.incrementAndGet());
                assertSame(failure, assertThrows(IllegalStateException.class, () -> joined.evaluate(0)));
                assertEquals(failOnLeft ? 0 : 1, good.evaluations.get());
                assertEquals(0, joins.get());
            }
            RecordingRule good = RecordingRule.constant(true);
            RecordingRule nullRule = new RecordingRule((index, record) -> null, Snapshot::satisfied);
            EvaluatedRule<JoinedResult<List<Snapshot>>> joined = (failOnLeft ? nullRule : good)
                    .and(failOnLeft ? good : nullRule, List::of);
            assertThrows(NullPointerException.class, () -> joined.evaluate(0));
            assertEquals(0, nullRule.coercions.get());
            assertEquals(failOnLeft ? 0 : 1, good.evaluations.get());
        }
        RecordingRule rule = RecordingRule.constant(true);
        IllegalArgumentException failure = new IllegalArgumentException("join failed");
        EvaluatedRule<JoinedResult<Object>> joined = rule.and(rule, (l, r) -> {
            throw failure;
        });
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> joined.evaluate(0)));
        assertEquals(2, rule.coercions.get());
    }

    @Test
    public void genericCompositionOverridesDispatchThroughInterfaceAndBaseClass() {
        EvaluatedRule<Snapshot> direct = new RecordingRule((index, record) -> new Snapshot(index, true),
                Snapshot::satisfied) {
            @Override
            public <S, T> EvaluatedRule<JoinedResult<T>> and(EvaluatedRule<S> other,
                    BiFunction<? super Snapshot, ? super S, ? extends T> joiner) {
                throw new UnsupportedOperationException("custom AND policy");
            }
        };
        EvaluatedRule<Snapshot> based = new AbstractEvaluatedRule<>() {
            @Override
            public Snapshot evaluate(int index, TradingRecord record) {
                return new Snapshot(index, true);
            }

            @Override
            public boolean toBoolean(Snapshot result) {
                return result.satisfied();
            }

            @Override
            public <S, T> EvaluatedRule<JoinedResult<T>> or(EvaluatedRule<S> other,
                    BiFunction<? super Snapshot, ? super S, ? extends T> joiner) {
                throw new UnsupportedOperationException("custom OR policy");
            }
        };
        assertThrows(UnsupportedOperationException.class, () -> direct.and(based, List::of));
        assertThrows(UnsupportedOperationException.class, () -> based.or(direct, List::of));
    }

    private record Observations(Snapshot gate, int score) {
    }

    private record Snapshot(int index, boolean satisfied) {
    }

    private static class RecordingRule implements EvaluatedRule<Snapshot> {

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
