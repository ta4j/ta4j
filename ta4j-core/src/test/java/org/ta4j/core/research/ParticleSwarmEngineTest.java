/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;

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
