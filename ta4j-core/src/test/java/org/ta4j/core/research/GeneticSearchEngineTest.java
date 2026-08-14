/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.research.ParameterResearch.Direction;
import org.ta4j.core.research.ParameterResearch.EvaluatedCandidate;
import org.ta4j.core.research.ParameterResearch.GeneticSettings;
import org.ta4j.core.research.ParameterResearch.ParameterDomain;
import org.ta4j.core.research.ParameterResearch.ParameterSet;
import org.ta4j.core.research.ParameterResearch.ParameterValue;
import org.ta4j.core.research.ParameterResearch.TerminationReason;

class GeneticSearchEngineTest {

    @Test
    void geneticStagnationIgnoresTieBreakerImprovements() {
        // The no-improvement streak must track primary scores only: repair-count
        // tie-breakers improving generation over generation must not reset it.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 10)),
                DomainSpec.of(ParameterDomain.integer("b", 1, 10)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> {
            int byScore = b.score().compareTo(a.score());
            return byScore != 0 ? byScore : Integer.compare(a.parameters().repairCount(), b.parameters().repairCount());
        };
        GeneticSearchEngine engine = new GeneticSearchEngine(specs, new GeneticSettings(4, 1, 2, 0.9, 0.1),
                new Random(0), ranking, Direction.MAXIMIZE, -1, 2);

        List<List<Integer>> repairScript = List.of(List.of(4, 3, 2, 1), List.of(1, 0, 0, 0), List.of(0, 0, 0, 0));
        for (List<Integer> repairs : repairScript) {
            List<ParameterSet> batch = engine.propose(10);
            assertThat(batch).isNotEmpty();
            for (int i = 0; i < batch.size(); i++) {
                ParameterSet set = batch.get(i);
                int count = repairs.get(Math.min(i, repairs.size() - 1));
                engine.observe(EvaluatedCandidate.valid(set.stableId(), withRepairs(set, count), i,
                        DecimalNum.valueOf(5), Map.of()));
            }
        }

        assertThat(engine.propose(10)).isEmpty();
        assertThat(engine.terminationReason()).isEqualTo(TerminationReason.NO_IMPROVEMENT);
    }

    @Test
    void finalizedObservationCountsFinalGeneration() {
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 4)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        GeneticSearchEngine engine = new GeneticSearchEngine(specs, new GeneticSettings(2, 1, 2, 0.0, 0.0),
                new Random(0), ranking, Direction.MAXIMIZE, -1, -1);

        List<ParameterSet> batch = engine.propose(4);
        assertThat(batch).isNotEmpty();
        for (int i = 0; i < batch.size(); i++) {
            ParameterSet set = batch.get(i);
            engine.observe(EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(1), Map.of()));
        }

        assertThat(engine.iterationsCompleted()).isZero();
        engine.finalizeObserved();
        assertThat(engine.iterationsCompleted()).isEqualTo(1);
        engine.finalizeObserved();
        assertThat(engine.iterationsCompleted()).isEqualTo(1);
    }

    private static ParameterSet withRepairs(ParameterSet set, int repairs) {
        List<ParameterValue> values = new ArrayList<>();
        for (int i = 0; i < set.values().size(); i++) {
            ParameterValue value = set.values().get(i);
            values.add(i < repairs ? new ParameterValue(value.name(), value.value(), true, "repaired") : value);
        }
        return new ParameterSet(values);
    }
}
