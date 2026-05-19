/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class GeneticCandidateSearchTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    private enum SearchMode {
        BASELINE, BOOST
    }

    record Candidate(int base, int delta, SearchMode mode, boolean enabled) {
    }

    @Test
    void searchIsDeterministicAndRetainsTopCandidatesAcrossGenerations() {
        CandidateCodec<Candidate> codec = new CandidateCodec<>(
                List.of(ParameterDomain.integerRange("base", 1, 5, 1), ParameterDomain.integerRange("delta", 1, 3, 1),
                        ParameterDomain.ofValues("mode", List.of(SearchMode.BASELINE, SearchMode.BOOST)),
                        ParameterDomain.constrainedBoolean("enabled", false, true)),
                values -> new Candidate(values.get("base", Integer.class), values.get("delta", Integer.class),
                        values.get("mode", SearchMode.class), values.get("enabled", Boolean.class)));

        CandidateFitnessEvaluator<Candidate> evaluator = candidate -> {
            double score = 100.0;
            score -= Math.abs(candidate.base() - 3) * 10.0;
            score -= Math.abs(candidate.delta() - 2) * 8.0;
            score += candidate.mode() == SearchMode.BOOST ? 12.0 : 0.0;
            score += candidate.enabled() ? 5.0 : -5.0;
            return NUM_FACTORY.numOf(score);
        };

        GeneticCandidateSearch.Settings settings = new GeneticCandidateSearch.Settings(12, 8, 3, 2, 0.7, 0.35, 2, 7L);

        GeneticCandidateSearch<Candidate> firstSearch = new GeneticCandidateSearch<>(codec, evaluator, settings);
        GeneticCandidateSearch<Candidate> secondSearch = new GeneticCandidateSearch<>(codec, evaluator, settings);

        GeneticCandidateSearch.SearchResult<Candidate> first = firstSearch.search();
        GeneticCandidateSearch.SearchResult<Candidate> second = secondSearch.search();

        assertThat(first.topCandidates()).extracting(GeneticCandidateSearch.CandidateResult::id)
                .containsExactlyElementsOf(
                        second.topCandidates().stream().map(GeneticCandidateSearch.CandidateResult::id).toList());
        assertThat(first.topCandidates().getFirst().context()).isEqualTo(new Candidate(3, 2, SearchMode.BOOST, true));
        assertThat(first.uniqueCandidateCount()).isGreaterThanOrEqualTo(first.topCandidates().size());
    }
}
