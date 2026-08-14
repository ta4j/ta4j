/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.research.ParameterResearch.Direction;
import org.ta4j.core.research.ParameterResearch.EvaluatedCandidate;
import org.ta4j.core.research.ParameterResearch.ParameterDomain;
import org.ta4j.core.research.ParameterResearch.ParameterSet;
import org.ta4j.core.research.ParameterResearch.ParameterValue;
import org.ta4j.core.research.ParameterResearch.SwarmSettings;
import org.ta4j.core.research.ParameterResearch.TerminationReason;

class ParticleSwarmEngineTest {

    @Test
    void particleSwarmContinuesThroughTransientCollisions() {
        // With inertia disabled and unit attraction weights the movement is
        // fully scripted. The first post-observation projection collides with
        // the two already-seen points; the engine must keep moving until a
        // particle reaches the unseen middle point instead of declaring
        // convergence on the first cached batch.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 3)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 1.0, 1.0, 1.0),
                new ScriptedRandom(0d, 1d, 0.2, 0.2, 0.5, 0.5, 0.2, 0.2, 0.5, 0.5, 0.2, 0.2, 0.5, 0.5), ranking,
                Direction.MAXIMIZE, -1, -1);

        List<ParameterSet> first = engine.propose(2);
        assertThat(first).hasSize(2);
        for (int i = 0; i < first.size(); i++) {
            ParameterSet set = first.get(i);
            engine.observe(EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(i + 1), Map.of()));
        }

        List<ParameterSet> second = engine.propose(2);
        assertThat(engine.terminationReason()).isNull();
        assertThat(second).hasSize(2);
        assertThat(second.stream().map(ParameterSet::stableId)).contains("a=2");

        for (int i = 0; i < second.size(); i++) {
            ParameterSet set = second.get(i);
            int score = set.stableId().equals("a=2") ? 3 : 2;
            engine.observe(EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(score), Map.of()));
        }

        assertThat(engine.propose(2)).isEmpty();
        assertThat(engine.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
    }

    @Test
    void finalizeObservedIsIdempotent() {
        // A repeated finalizeObserved() call must be a no-op: re-running the
        // iteration would double-count completeIteration() and advance the
        // stagnation streak twice for the same batch.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 2)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 1.0, 1.0, 1.0),
                new ScriptedRandom(0d, 1d), ranking, Direction.MAXIMIZE, -1, 1);

        List<ParameterSet> batch = engine.propose(2);
        assertThat(batch).hasSize(2);
        for (int i = 0; i < batch.size(); i++) {
            ParameterSet set = batch.get(i);
            engine.observe(EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(i + 1), Map.of()));
        }

        assertThat(engine.iterationsCompleted()).isZero();
        engine.finalizeObserved();
        assertThat(engine.iterationsCompleted()).isEqualTo(1);
        assertThat(engine.terminationReason()).isNull();
        engine.finalizeObserved();
        assertThat(engine.iterationsCompleted()).isEqualTo(1);
        assertThat(engine.terminationReason()).isNull();
    }

    @Test
    void gbestTracksRankingTieBreakImprovements() {
        // Two particles share the same primary score but one was repaired:
        // the ranking prefers the unrepaired candidate, so the global best
        // must move to it even though scoreIsBetter() alone sees a tie.
        // Fully scripted: particle 0 lands on 3.0 ("a=3", repaired) and
        // particle 1 on 7.0 ("a=7", unrepaired, lower score). After the
        // second batch, particle 1 reaches "a=6" with the same score as
        // particle 0 but without repairs, so the gbest must jump to 6.0;
        // the next move from 6.0 then discovers the unseen "a=4".
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 7)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> {
            int byScore = b.score().compareTo(a.score());
            if (byScore != 0) {
                return byScore;
            }
            int byRepair = Integer.compare(a.parameters().repairCount(), b.parameters().repairCount());
            if (byRepair != 0) {
                return byRepair;
            }
            return Integer.compare(a.evaluationOrdinal(), b.evaluationOrdinal());
        };
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 1.0, 1.0, 1.0),
                new ScriptedRandom(1d / 3d, 1d, 0d, 0d, 0d, 0.25, 0d, 1d / 3d, 0d, 1d / 3d), ranking,
                Direction.MAXIMIZE, -1, -1);

        List<ParameterSet> first = engine.propose(2);
        assertThat(first).hasSize(2);
        engine.observe(EvaluatedCandidate.valid("a=3",
                new ParameterSet(List.of(new ParameterValue("a", "3", true, "clamped"))), 0, DecimalNum.valueOf(5),
                Map.of()));
        engine.observe(
                EvaluatedCandidate.valid("a=7", new ParameterSet(List.of(new ParameterValue("a", "7", false, ""))), 1,
                        DecimalNum.valueOf(4), Map.of()));

        List<ParameterSet> second = engine.propose(2);
        engine.observe(EvaluatedCandidate.valid("a=3",
                new ParameterSet(List.of(new ParameterValue("a", "3", true, "clamped"))), 2, DecimalNum.valueOf(5),
                Map.of()));
        engine.observe(
                EvaluatedCandidate.valid("a=6", new ParameterSet(List.of(new ParameterValue("a", "6", false, ""))), 3,
                        DecimalNum.valueOf(5), Map.of()));

        List<ParameterSet> third = engine.propose(2);
        assertThat(third.stream().map(ParameterSet::stableId)).contains("a=4");
    }

    @Test
    void particleSwarmRejectsOverflowingDomainSpans() {
        // A finite bound pair whose span overflows double precision used to
        // turn the first position draw into infinity and poison the swarm;
        // the plan must fail at engine construction instead.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.decimal("a", -1.7e308, 1.7e308, 1e300)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 1.0, 1.0, 1.0), new ScriptedRandom(0d),
                        (a, b) -> 0, Direction.MAXIMIZE, -1, -1));
        assertThat(exception.getMessage()).contains("overflows double precision");
    }

    @Test
    void particleSwarmRejectsOverflowingDerivedScales() {
        // Finite domain spans can still overflow the derived update terms
        // when the weights or the clamp factor are huge; the engine must
        // reject the plan instead of projecting NaN positions onto index
        // zero via the clamp.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.decimal("a", 0, 1e154, 1e150)));

        assertThrows(IllegalArgumentException.class,
                () -> new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.5, Double.MAX_VALUE, 0.5, 0.2),
                        new ScriptedRandom(0d), (a, b) -> 0, Direction.MAXIMIZE, -1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.5, 0.5, 0.5, Double.MAX_VALUE),
                        new ScriptedRandom(0d), (a, b) -> 0, Direction.MAXIMIZE, -1, -1));
    }

    @Test
    void particleSwarmAcceptsRepresentableExtremeWeights() {
        // Huge weights over a tiny span: each move() contribution stays
        // finite on its own even though the naive sum of the scales would
        // overflow, so this representable plan must be accepted.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.decimal("a", 0, 1e-300, 1e-300)));

        assertDoesNotThrow(
                () -> new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.5, Double.MAX_VALUE, Double.MAX_VALUE, 0.2),
                        new ScriptedRandom(0d), (a, b) -> 0, Direction.MAXIMIZE, -1, -1));
    }

    @Test
    void particleSwarmCountsStallMovesAgainstTheIterationLimit() {
        // All-zero scripted draws pin both particles to index zero, so every
        // batch after the first stalls on already-proposed points. Each retry
        // move must consume an iteration, and the engine must stop at the
        // iteration limit instead of burning the whole stall budget.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 0, 1)));
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.5, 0.5, 0.5, 0.2),
                new ScriptedRandom(0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d), (a, b) -> 0,
                Direction.MAXIMIZE, 3, -1);

        List<ParameterSet> first = engine.propose(2);
        assertThat(first).hasSize(2);
        for (ParameterSet set : first) {
            engine.observe(EvaluatedCandidate.valid(set.stableId(), set, 0, DecimalNum.valueOf(1), Map.of()));
        }

        assertThat(engine.propose(2)).isEmpty();
        assertThat(engine.terminationReason()).isEqualTo(TerminationReason.ITERATION_LIMIT);
        assertThat(engine.iterationsCompleted()).isEqualTo(3);
    }

    /**
     * Deterministic {@link Random} feeding one scripted {@code nextDouble()} draw
     * per call; the particle-swarm engines use no other random primitive.
     */
    private static final class ScriptedRandom extends Random {

        private final double[] script;
        private int cursor;

        private ScriptedRandom(double... script) {
            this.script = script;
        }

        @Override
        public double nextDouble() {
            if (cursor >= script.length) {
                throw new IllegalStateException("script exhausted at draw " + cursor);
            }
            return script[cursor++];
        }
    }
}
