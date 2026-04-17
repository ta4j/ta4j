/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.TradingStatementExecutionResult.RankingProfile;
import org.ta4j.core.backtest.TradingStatementExecutionResult.WeightedCriterion;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch.CandidateFailureStage;
import org.ta4j.core.research.ParameterResearch.CandidateGenerationResult;
import org.ta4j.core.research.ParameterResearch.ParameterDomain;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.ParameterSet;
import org.ta4j.core.research.ParameterResearch.PruningPolicy;
import org.ta4j.core.research.ParameterResearch.ResearchConfig;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.strategy.named.NamedStrategy;

class ParameterResearchTest {

    @Test
    void candidateGenerationNormalizesDeduplicatesAndCapturesRejectedValues() {
        BarSeries series = buildSeries(5);
        ParameterDomain domain = ParameterDomain.integerRange("barCount", 0, 1, 1, 1, 20, true);

        CandidateGenerationResult result = ParameterResearch.generateCandidateSpace(series, List.of(domain));

        assertThat(result.candidates()).extracting(ParameterResearch.StrategyCandidate::id)
                .containsExactly("barCount=1");
        assertThat(result.invalidCandidates()).hasSize(1);
        assertThat(result.invalidCandidates().getFirst().stage()).isEqualTo(CandidateFailureStage.DUPLICATE_NORMALIZED);
        assertThat(result.generatedCandidateCount()).isEqualTo(2);
        assertThat(result.candidateSpaceHash()).isNotBlank();
    }

    @Test
    void candidateGenerationRejectsNullLiteralValues() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ParameterDomain.values("barCount", Arrays.asList(1, null)));

        assertThat(exception).hasMessageContaining("values cannot contain null entries");
    }

    @Test
    void candidateGenerationReportsNormalizerNameMismatch() {
        BarSeries series = buildSeries(5);
        ParameterDomain domain = new ParameterDomain("barCount", List.of("bad", "3"), (targetSeries, name, raw) -> {
            if ("bad".equals(raw)) {
                return new ParameterResearch.ParameterValue("wrongName", raw, raw, false, "");
            }
            return new ParameterResearch.ParameterValue(name, raw, raw, false, "");
        });

        CandidateGenerationResult result = ParameterResearch.generateCandidateSpace(series, List.of(domain));

        assertThat(result.candidates()).extracting(ParameterResearch.StrategyCandidate::id)
                .containsExactly("barCount=3");
        assertThat(result.invalidCandidates()).hasSize(1);
        assertThat(result.invalidCandidates().getFirst().stage()).isEqualTo(CandidateFailureStage.GENERATION);
        assertThat(result.invalidCandidates().getFirst().reason()).contains("wrongName");
    }

    @Test
    void candidateGenerationResultRequiresAtLeastOneCandidate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CandidateGenerationResult(List.of(), List.of(), "hash"));

        assertThat(exception).hasMessageContaining("candidates cannot be empty");
    }

    @Test
    void multiParameterDomainsGenerateCartesianProductAndCaptureInvalidCombinations() {
        BarSeries series = buildSeries(8);
        List<ParameterDomain> domains = List.of(ParameterDomain.values("entry", List.of(1, 3)),
                ParameterDomain.values("exit", List.of(2, 3)));

        CandidateGenerationResult result = ParameterResearch.generateCandidateSpace(series, domains, parameters -> {
            if (parameters.intValue("entry") >= parameters.intValue("exit")) {
                throw new IllegalArgumentException("entry must be before exit");
            }
        });

        assertThat(result.candidates()).extracting(ParameterResearch.StrategyCandidate::id)
                .containsExactly("entry=1|exit=2", "entry=1|exit=3");
        assertThat(result.invalidCandidates()).hasSize(2);
    }

    @Test
    void researchGeneratesCandidateSpaceFromTrainingWindowToAvoidHoldoutLengthLeakage() {
        BarSeries series = buildSeries(12);
        ParameterDomain periodDomain = ParameterDomain.integerRange("barCount", 1, 11, 10, 1, Integer.MAX_VALUE, true);
        ResearchConfig config = defaultConfig(series, 5, 7).withPruningPolicy(PruningPolicy.NONE);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(periodDomain),
                ParameterResearch.CandidateValidator.acceptAll(), ParameterResearchTest::fixedByBarCount, config);

        assertThat(report.window().trainingStartIndex()).isEqualTo(0);
        assertThat(report.window().trainingEndIndex()).isEqualTo(4);
        assertThat(report.trainingScores()).extracting(ParameterResearch.CandidateScore::candidateId)
                .containsOnly("barCount=1", "barCount=5");
        assertThat(report.trainingScores()).extracting(ParameterResearch.CandidateScore::candidateId)
                .doesNotContain("barCount=11");
    }

    @Test
    void exactTradingRecordPruningKeepsRepresentativeForEquivalentExecutedBehavior() {
        BarSeries series = buildSeries(12);
        ParameterDomain domain = ParameterDomain.values("group", List.of(1, 2, 3));
        ResearchConfig config = defaultConfig(series, 8, 4);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearch.CandidateValidator.acceptAll(), ParameterResearchTest::groupedTradingRecordStrategy,
                config);

        assertThat(report.representativeCount()).isEqualTo(2);
        assertThat(report.prunedCandidateCount()).isEqualTo(1);
        assertThat(report.candidates()).hasSize(3);
        assertThat(report.baselineScores()).hasSize(3);
        assertThat(report.pruningGroups().getFirst().representativeId()).isEqualTo("group=1");
        assertThat(report.pruningGroups().getFirst().discardedIds()).containsExactly("group=2");
        assertThat(report.baselineScores()).filteredOn(score -> !score.representative())
                .extracting(ParameterResearch.CandidateScore::candidateId)
                .containsExactly("group=2");
        assertThat(report.validationScores()).isNotEmpty();
    }

    @Test
    void exactSignalPruningComparesSignalsAfterStrategyUnstableBarsAreApplied() {
        BarSeries series = buildSeries(10);
        ParameterDomain domain = ParameterDomain.values("entry", List.of(1, 2));
        ResearchConfig config = defaultConfig(series, 6, 4).withPruningPolicy(PruningPolicy.EXACT_SIGNAL);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearch.CandidateValidator.acceptAll(), ParameterResearchTest::warmupOnlySignalStrategy,
                config);

        assertThat(report.representativeCount()).isEqualTo(1);
        assertThat(report.pruningGroups().getFirst().discardedIds()).containsExactly("entry=2");
    }

    @Test
    void indicatorDistancePruningIsExplicitAndReportsWarning() {
        BarSeries series = buildSeries(12);
        ParameterDomain domain = ParameterDomain.values("offset", List.of(0, 1, 10));
        ResearchConfig config = defaultConfig(series, 8, 4).withIndicatorDistance(2.0,
                (targetSeries, parameters) -> new OffsetCloseIndicator(targetSeries, parameters.intValue("offset")));

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearch.CandidateValidator.acceptAll(), ParameterResearchTest::offsetStrategy, config);

        assertThat(report.pruningPolicy()).isEqualTo(PruningPolicy.INDICATOR_DISTANCE);
        assertThat(report.pruningGroups().getFirst().discardedIds()).containsExactly("offset=1");
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("INDICATOR_DISTANCE"));
    }

    @Test
    void objectiveDistancePruningIsPostEvaluationAndReportsWarning() {
        BarSeries series = buildSeries(12);
        ParameterDomain domain = ParameterDomain.values("group", List.of(1, 2, 3));
        ResearchConfig config = defaultConfig(series, 8, 4).withObjectiveDistance(1.0);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearch.CandidateValidator.acceptAll(), ParameterResearchTest::groupedTradingRecordStrategy,
                config);

        assertThat(report.pruningPolicy()).isEqualTo(PruningPolicy.OBJECTIVE_DISTANCE);
        assertThat(report.representativeCount()).isEqualTo(1);
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("OBJECTIVE_DISTANCE"));
    }

    @Test
    void objectiveDistanceUsesBestRankedCandidateAsRepresentative() {
        BarSeries series = buildSeries(8);
        ParameterDomain domain = ParameterDomain.values("cycles", List.of(1, 2));
        ResearchConfig config = ResearchConfig
                .holdout(6, 2,
                        RankingProfile.weighted(WeightedCriterion.of(new NumberOfPositionsCriterion(false), 1.0)),
                        series.numFactory().one(), 2)
                .withObjectiveDistance(100.0);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearch.CandidateValidator.acceptAll(), ParameterResearchTest::fixedCycleStrategy, config);

        assertThat(report.pruningGroups()).hasSize(1);
        assertThat(report.pruningGroups().getFirst().representativeId()).isEqualTo("cycles=2");
        assertThat(report.pruningGroups().getFirst().discardedIds()).containsExactly("cycles=1");
    }

    @Test
    void reportWarnsWhenRequestedWindowsAreReduced() {
        BarSeries series = buildSeries(5);
        ParameterDomain domain = ParameterDomain.values("barCount", List.of(1));
        ResearchConfig config = defaultConfig(series, 20, 20).withPruningPolicy(PruningPolicy.NONE);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearch.CandidateValidator.acceptAll(), ParameterResearchTest::fixedByBarCount, config);

        assertThat(report.warnings()).anyMatch(warning -> warning.contains("validationBarCount was reduced"));
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("trainingBarCount was reduced"));
    }

    @Test
    void namedStrategyCandidatesFeedPermutationBuilderResearchAndWalkForwardAdapters() {
        BarSeries series = buildSeries(10);
        List<ParameterDomain> domains = List.of(ParameterDomain.values("entry", List.of(1, 2)),
                ParameterDomain.values("exit", List.of(4, 5)));
        CandidateGenerationResult candidateSpace = ParameterResearch.generateCandidateSpace(series, domains,
                parameters -> {
                    if (parameters.intValue("entry") >= parameters.intValue("exit")) {
                        throw new IllegalArgumentException("entry must be before exit");
                    }
                });

        List<String[]> permutations = candidateSpace.candidates()
                .stream()
                .map(candidate -> candidate.parameters().asStringArray())
                .toList();
        List<Strategy> namedStrategies = NamedStrategy.buildAllStrategyPermutations(series, permutations,
                FixtureNamedStrategy::new);
        ResearchConfig config = defaultConfig(series, 6, 4).withPruningPolicy(PruningPolicy.NONE);

        try {
            ParameterResearchReport report = ParameterResearch.run(series, candidateSpace,
                    (targetSeries, parameters) -> new FixtureNamedStrategy(targetSeries, parameters.asStringArray()),
                    config);

            assertThat(namedStrategies).hasSameSizeAs(candidateSpace.candidates());
            assertThat(report.baselineScores()).hasSize(candidateSpace.candidates().size());
            assertThat(report.trainingScores()).hasSize(candidateSpace.candidates().size());
            assertThat(report.validationScores()).isNotEmpty();
            assertThat(ParameterResearch.toWalkForwardCandidates(candidateSpace))
                    .hasSize(candidateSpace.candidates().size());
        } finally {
            NamedStrategy.unregisterImplementation(FixtureNamedStrategy.class);
        }
    }

    private static ResearchConfig defaultConfig(BarSeries series, int trainingBarCount, int validationBarCount) {
        return ResearchConfig.holdout(trainingBarCount, validationBarCount,
                RankingProfile.weighted(WeightedCriterion.of(new NetProfitCriterion(), 1.0)), series.numFactory().one(),
                3);
    }

    private static Strategy fixedByBarCount(BarSeries series, ParameterSet parameters) {
        int barCount = parameters.intValue("barCount");
        int entryIndex = Math.min(series.getEndIndex(), Math.max(series.getBeginIndex(), barCount - 1));
        int unstableBars = Math.max(0, barCount - 1);
        return new BaseStrategy(parameters.stableId(), new FixedRule(entryIndex), new FixedRule(series.getEndIndex()),
                unstableBars);
    }

    private static Strategy groupedTradingRecordStrategy(BarSeries series, ParameterSet parameters) {
        int group = parameters.intValue("group");
        int entryIndex = group <= 2 ? series.getBeginIndex() : series.getBeginIndex() + 1;
        return new BaseStrategy(parameters.stableId(), new FixedRule(entryIndex), new FixedRule(series.getEndIndex()));
    }

    private static Strategy offsetStrategy(BarSeries series, ParameterSet parameters) {
        int offset = parameters.intValue("offset");
        int entryIndex = offset <= 1 ? series.getBeginIndex() : series.getBeginIndex() + 1;
        return new BaseStrategy(parameters.stableId(), new FixedRule(entryIndex), new FixedRule(series.getEndIndex()));
    }

    private static Strategy warmupOnlySignalStrategy(BarSeries series, ParameterSet parameters) {
        int entryIndex = parameters.intValue("entry") - 1;
        return new BaseStrategy(parameters.stableId(), new FixedRule(entryIndex), new FixedRule(series.getEndIndex()),
                3);
    }

    private static Strategy fixedCycleStrategy(BarSeries series, ParameterSet parameters) {
        int cycles = parameters.intValue("cycles");
        if (cycles == 1) {
            return new BaseStrategy(parameters.stableId(), new FixedRule(0), new FixedRule(1));
        }
        return new BaseStrategy(parameters.stableId(), new FixedRule(0, 2), new FixedRule(1, 3));
    }

    private static BarSeries buildSeries(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + i;
        }
        return new MockBarSeriesBuilder().withData(prices).build();
    }

    private static final class FixtureNamedStrategy extends NamedStrategy {

        private FixtureNamedStrategy(BarSeries series, int entryIndex, int exitIndex) {
            super(NamedStrategy.buildLabel(FixtureNamedStrategy.class, String.valueOf(entryIndex),
                    String.valueOf(exitIndex)), entryRule(entryIndex), exitRule(exitIndex));
        }

        private FixtureNamedStrategy(BarSeries series, String... params) {
            this(series, Integer.parseInt(params[0]), Integer.parseInt(params[1]));
        }

        private static Rule entryRule(int entryIndex) {
            return new FixedRule(entryIndex);
        }

        private static Rule exitRule(int exitIndex) {
            return new FixedRule(exitIndex);
        }
    }

    private static final class OffsetCloseIndicator extends AbstractIndicator<Num> {

        private final ClosePriceIndicator closePrice;
        private final Num offset;

        private OffsetCloseIndicator(BarSeries series, int offset) {
            super(series);
            this.closePrice = new ClosePriceIndicator(series);
            this.offset = series.numFactory().numOf(offset);
        }

        @Override
        public Num getValue(int index) {
            return closePrice.getValue(index).plus(offset);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}
