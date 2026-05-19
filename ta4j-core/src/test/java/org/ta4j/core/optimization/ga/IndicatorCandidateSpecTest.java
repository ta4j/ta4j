/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

class IndicatorCandidateSpecTest {

    @Test
    void specBuildsStableIndicatorCandidateIdsAndTypedIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 14, 16, 18).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        IndicatorCandidateSpec<Num> spec = smaSpec();

        CandidateCodec.DecodedCandidate<IndicatorCandidate<Num>> decoded = spec.codec(series, List.of(closePrice))
                .decode(List.of(1));
        IndicatorCandidate<Num> candidate = decoded.context();

        assertThat(decoded.id()).isEqualTo("SMA [barCount=3]");
        assertThat(candidate.displayName()).isEqualTo("SMA [barCount=3]");
        assertThat(candidate.parameters().get("barCount", Integer.class)).isEqualTo(3);
        assertThat(candidate.indicator()).isInstanceOf(SMAIndicator.class);
        assertThat(candidate.indicator().getValue(2)).isEqualTo(series.numFactory().numOf(12));
    }

    @Test
    void specCanCreateCandidatesFromDecodedValueMaps() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 14, 16, 18).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        IndicatorCandidate<Num> candidate = smaSpec().createCandidate(series, List.of(closePrice),
                Map.of("barCount", 4));

        assertThat(candidate.displayName()).isEqualTo("SMA [barCount=4]");
        assertThat(candidate.parameters().asMap()).containsEntry("barCount", 4);
    }

    @Test
    void searchScoresIndicatorCandidatesWithExternalEvaluator() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        IndicatorCandidateSpec<Num> spec = smaSpec();
        CandidateCodec<IndicatorCandidate<Num>> codec = spec.codec(series, List.of(closePrice));
        CandidateFitnessEvaluator<IndicatorCandidate<Num>> evaluator = candidate -> {
            double value = candidate.indicator().getValue(4).doubleValue();
            return series.numFactory().numOf(-Math.abs(value - 4.0));
        };
        GeneticCandidateSearch.Settings settings = new GeneticCandidateSearch.Settings(10, 6, 2, 1, 0.7, 0.35, 2, 11L);

        GeneticCandidateSearch.SearchResult<IndicatorCandidate<Num>> result = new GeneticCandidateSearch<>(codec,
                evaluator, settings).search();

        assertThat(result.topCandidates().getFirst().id()).isEqualTo("SMA [barCount=3]");
        assertThat(result.topCandidates().getFirst().context().parameters().get("barCount", Integer.class))
                .isEqualTo(3);
    }

    private static IndicatorCandidateSpec<Num> smaSpec() {
        return new IndicatorCandidateSpec<>("SMA", List.of(ParameterDomain.integerRange("barCount", 2, 4, 1)),
                (series, sourceIndicators, parameters) -> {
                    @SuppressWarnings("unchecked")
                    Indicator<Num> source = (Indicator<Num>) sourceIndicators.get(0);
                    return new SMAIndicator(source, parameters.get("barCount", Integer.class));
                });
    }
}
