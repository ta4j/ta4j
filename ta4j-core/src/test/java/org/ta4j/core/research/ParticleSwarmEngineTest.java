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
            engine.observe(i, set.stableId(),
                    EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(i + 1), Map.of()));
        }

        List<ParameterSet> second = engine.propose(2);
        assertThat(engine.terminationReason()).isNull();
        assertThat(second).hasSize(2);
        assertThat(second.stream().map(ParameterSet::stableId)).contains("a=2");

        for (int i = 0; i < second.size(); i++) {
            ParameterSet set = second.get(i);
            int score = set.stableId().equals("a=2") ? 3 : 2;
            engine.observe(i, set.stableId(),
                    EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(score), Map.of()));
        }

        assertThat(engine.propose(2)).isEmpty();
        assertThat(engine.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
    }

    @Test
    void movedBatchesCountOnceAgainstIterationLimit() {
        // Each swarm update advances the iteration tracker exactly once: the
        // launch batch is counted when it is finalized and every subsequent
        // move when it runs, so maxIterations(3) yields the launch batch plus
        // two moved batches instead of terminating one batch early on a
        // double-counted update.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 6)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 1.0, 1.0, 1.0),
                new ScriptedRandom(0d, 1d, 0d), ranking, Direction.MAXIMIZE, 3, -1);

        List<ParameterSet> first = engine.propose(2);
        assertThat(first).hasSize(2);
        observeFailed(engine, first);

        List<ParameterSet> second = engine.propose(2);
        assertThat(second).hasSize(2);
        observeFailed(engine, second);

        List<ParameterSet> third = engine.propose(2);
        assertThat(third).hasSize(2);
        observeFailed(engine, third);

        assertThat(engine.propose(2)).isEmpty();
        assertThat(engine.terminationReason()).isEqualTo(TerminationReason.ITERATION_LIMIT);
        assertThat(engine.iterationsCompleted()).isEqualTo(3);
    }

    private static void observeFailed(ParticleSwarmEngine engine, List<ParameterSet> batch) {
        for (int i = 0; i < batch.size(); i++) {
            ParameterSet set = batch.get(i);
            engine.observe(i, set.stableId(), EvaluatedCandidate.failed(set.stableId(), set, i, "always", Map.of()));
        }
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
            engine.observe(i, set.stableId(),
                    EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(i + 1), Map.of()));
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
    void finalBatchIsFinalizedBeforeTheIterationCapFires() {
        // When the last move exhausts the iteration cap, the pending batch
        // must still be finalized: its evaluations are the run's final
        // observations, and skipping the finalize would drop them from
        // pbest/gbest and the stagnation streak. A final batch that fails to
        // improve therefore reports NO_IMPROVEMENT (not ITERATION_LIMIT)
        // when the no-improvement limit is configured.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 4)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 1.0, 1.0, 1.0),
                new ScriptedRandom(0d, 1d, 0d, 0d, 0d, 1d / 3d), ranking, Direction.MAXIMIZE, 2, 1);

        List<ParameterSet> first = engine.propose(2);
        assertThat(first.stream().map(ParameterSet::stableId)).containsExactlyInAnyOrder("a=1", "a=4");
        engine.observe(0, "a=1", EvaluatedCandidate.valid("a=1", first.get(0), 0, DecimalNum.valueOf(5), Map.of()));
        engine.observe(1, "a=4", EvaluatedCandidate.valid("a=4", first.get(1), 1, DecimalNum.valueOf(4), Map.of()));

        List<ParameterSet> second = engine.propose(2);
        assertThat(second.stream().map(ParameterSet::stableId)).contains("a=3");
        engine.observe(0, "a=1", EvaluatedCandidate.valid("a=1", second.get(0), 2, DecimalNum.valueOf(5), Map.of()));
        engine.observe(1, "a=3", EvaluatedCandidate.valid("a=3", second.get(1), 3, DecimalNum.valueOf(0), Map.of()));

        assertThat(engine.propose(2)).isEmpty();
        assertThat(engine.terminationReason()).isEqualTo(TerminationReason.NO_IMPROVEMENT);
        assertThat(engine.iterationsCompleted()).isEqualTo(2);
    }

    @Test
    void invalidEvaluationsDoNotAdvanceTheStagnationStreak() {
        // A batch whose candidates are all invalid carries no ranking
        // evidence: it neither improves nor declines the best valid score,
        // so it must not advance the stagnation streak and terminate the run
        // with NO_IMPROVEMENT while unseen candidates remain. The sweep keeps
        // exploring the declared space, ending with SEARCH_SPACE_EXHAUSTED
        // only when every cell has been proposed.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 4)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 1.0, 1.0, 1.0),
                new ScriptedRandom(0d, 1d, 0d), ranking, Direction.MAXIMIZE, -1, 1);

        List<ParameterSet> first = engine.propose(2);
        assertThat(first.stream().map(ParameterSet::stableId)).containsExactlyInAnyOrder("a=1", "a=4");
        observeFailed(engine, first);

        List<ParameterSet> second = engine.propose(2);
        assertThat(second).hasSize(2);
        assertThat(engine.terminationReason()).isNull();
        observeFailed(engine, second);
        assertThat(engine.propose(2)).isEmpty();
        assertThat(engine.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
        // Launch + the two sweep moves that explored the remaining cells; the
        // final move that proposed nothing new still counts as one update.
        assertThat(engine.iterationsCompleted()).isEqualTo(3);
    }

    @Test
    void zeroCapacityProposeFinalizesButSkipsMoveAndIteration() {
        // An exhausted budget still asks the engine for a zero-sized batch:
        // the pending observations must be finalized, but the swarm must not
        // move and the iteration tracker must not advance for a request that
        // cannot contribute any proposal.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 2)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 1.0, 1.0, 1.0),
                new ScriptedRandom(0d, 1d), ranking, Direction.MAXIMIZE, -1, 1);

        List<ParameterSet> batch = engine.propose(2);
        assertThat(batch).hasSize(2);
        for (int i = 0; i < batch.size(); i++) {
            ParameterSet set = batch.get(i);
            engine.observe(i, set.stableId(),
                    EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(i + 1), Map.of()));
        }

        assertThat(engine.propose(0)).isEmpty();
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
        assertThat(first.stream().map(ParameterSet::stableId)).containsExactlyInAnyOrder("a=3", "a=7");
        engine.observe(0, "a=3",
                EvaluatedCandidate.valid("a=3",
                        new ParameterSet(List.of(new ParameterValue("a", "3", true, "clamped"))), 0,
                        DecimalNum.valueOf(5), Map.of()));
        engine.observe(1, "a=7",
                EvaluatedCandidate.valid("a=7", new ParameterSet(List.of(new ParameterValue("a", "7", false, ""))), 1,
                        DecimalNum.valueOf(4), Map.of()));

        List<ParameterSet> second = engine.propose(2);
        assertThat(second.stream().map(ParameterSet::stableId)).containsExactlyInAnyOrder("a=3", "a=6");
        engine.observe(0, "a=3",
                EvaluatedCandidate.valid("a=3",
                        new ParameterSet(List.of(new ParameterValue("a", "3", true, "clamped"))), 2,
                        DecimalNum.valueOf(5), Map.of()));
        engine.observe(1, "a=6",
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
        for (int i = 0; i < first.size(); i++) {
            ParameterSet set = first.get(i);
            engine.observe(i, set.stableId(),
                    EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(1), Map.of()));
        }

        assertThat(engine.propose(2)).isEmpty();
        assertThat(engine.terminationReason()).isEqualTo(TerminationReason.ITERATION_LIMIT);
        assertThat(engine.iterationsCompleted()).isEqualTo(3);
    }

    @Test
    void particleSwarmSuppressesCognitivePullWithoutValidPersonalBest() {
        // Particle A launches on index 0, where the objective fails, so its
        // pbest snapshot still holds the invalid launch point while the
        // validated personal best stays absent. Without gating, the cognitive
        // term drags A back toward index 0 on every move and it reaches
        // index 3; with the gate, only the social pull toward B's valid
        // global best at index 10 acts and A reaches index 4.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 0, 10)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 0.5, 0.5, 0.2),
                new ScriptedRandom(0d, 1d, 0.5, 0.5, 0.5, 0.5, 0.75, 0.5, 0.5, 0.5), ranking, Direction.MAXIMIZE, -1,
                -1);

        List<ParameterSet> first = engine.propose(2);
        assertThat(first.stream().map(ParameterSet::stableId)).containsExactly("a=0", "a=10");
        engine.observe(0, "a=0", EvaluatedCandidate.failed("a=0", first.get(0), 0, "invalid", Map.of()));
        engine.observe(1, "a=10", EvaluatedCandidate.valid("a=10", first.get(1), 1, DecimalNum.valueOf(10), Map.of()));

        List<ParameterSet> second = engine.propose(2);
        assertThat(second.stream().map(ParameterSet::stableId)).contains("a=2", "a=10");
        engine.observe(0, "a=2", EvaluatedCandidate.failed("a=2", second.get(0), 2, "invalid", Map.of()));
        engine.observe(1, "a=10", EvaluatedCandidate.valid("a=10", second.get(1), 3, DecimalNum.valueOf(10), Map.of()));

        List<ParameterSet> third = engine.propose(2);
        assertThat(third.stream().map(ParameterSet::stableId)).contains("a=4", "a=10");
    }

    @Test
    void collidingParticlesKeepPerOccurrenceOutcomes() {
        // Both particles project onto the single grid cell, so the launch batch
        // carries one raw id twice. A raw-id-keyed outcome map would let the
        // second observation overwrite the first, leaking particle 1's score
        // into particle 0's personal best and flipping the global best; outcomes
        // must be keyed by occurrence instead.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 0, 0)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 1.0, 1.0, 1.0),
                new ScriptedRandom(0d, 0d), ranking, Direction.MAXIMIZE, -1, -1);

        List<ParameterSet> first = engine.propose(2);
        assertThat(first).hasSize(2);
        assertThat(first.stream().map(ParameterSet::stableId)).containsExactly("a=0", "a=0");
        ParameterSet shared = first.get(0);
        engine.observe(0, "a=0", EvaluatedCandidate.valid("a=0", shared, 0, DecimalNum.valueOf(10), Map.of()));
        engine.observe(1, "a=0", EvaluatedCandidate.valid("a=0", shared, 1, DecimalNum.valueOf(0), Map.of()));
        engine.finalizeObserved();

        assertThat(engine.gbestEvaluated).isNotNull();
        assertThat(engine.gbestEvaluated.score()).isEqualByComparingTo(DecimalNum.valueOf(10));
    }

    /**
     * Deterministic {@link Random} feeding one scripted {@code nextDouble()} draw
     * per call. It is seeded explicitly so that any unscripted primitive would
     * still produce a reproducible sequence, and {@code nextInt(int)} is scripted
     * too: particle-swarm engines draw it to seed the start cursor of particles
     * with no validated personal best, and an unseeded fallback would silently
     * break determinism.
     */
    private static final class ScriptedRandom extends Random {

        private final double[] script;
        private int cursor;

        private ScriptedRandom(double... script) {
            super(0L);
            this.script = script;
        }

        @Override
        public double nextDouble() {
            if (cursor >= script.length) {
                throw new IllegalStateException("script exhausted at draw " + cursor);
            }
            return script[cursor++];
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            // nextDouble() draws can be exactly 1.0 in the scripted feeds:
            // clamp so the result stays within Random's [0, bound) contract.
            return Math.min(bound - 1, (int) Math.floor(nextDouble() * bound));
        }
    }
}
