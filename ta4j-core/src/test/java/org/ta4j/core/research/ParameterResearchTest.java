/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.backtest.TradingStatementExecutionResult.RankingProfile;
import org.ta4j.core.backtest.TradingStatementExecutionResult.WeightedCriterion;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.ValueAtRiskCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch.CandidateFailureStage;
import org.ta4j.core.research.ParameterResearch.CandidateGenerationResult;
import org.ta4j.core.research.ParameterResearch.CandidateValidator;
import org.ta4j.core.research.ParameterResearch.ParameterDomain;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.ParameterSet;
import org.ta4j.core.research.ParameterResearch.PruningGroup;
import org.ta4j.core.research.ParameterResearch.PruningPolicy;
import org.ta4j.core.research.ParameterResearch.ResearchConfig;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.rules.StopLossRule;
import org.ta4j.core.strategy.named.NamedStrategy;

class ParameterResearchTest {

    @Test
    void candidateGenerationNormalizesDeduplicatesAndCapturesRejectedValues() {
        BarSeries series = buildSeries(5);
        ParameterDomain domain = ParameterDomain.integerRange("barCount", 0, 1, 1, 1, 20, true);

        CandidateGenerationResult result = ParameterResearch.generateCandidateSpace(series, List.of(domain));

        assertThat(result.rawCandidateCount()).isEqualTo(2);
        assertThat(result.candidates()).extracting(ParameterResearch.StrategyCandidate::id)
                .containsExactly("barCount=1");
        assertThat(result.invalidCandidates()).hasSize(1);
        assertThat(result.invalidCandidates().getFirst().stage()).isEqualTo(CandidateFailureStage.DUPLICATE_NORMALIZED);
        assertThat(result.generatedCandidateCount()).isEqualTo(2);
        assertThat(result.candidateSpaceHash()).isNotBlank();
    }

    @Test
    void periodRangeCapsValuesToTrainingSeriesLength() {
        BarSeries series = buildSeries(5);
        ParameterDomain domain = ParameterDomain.periodRange("barCount", 1, 11, 10);

        CandidateGenerationResult result = ParameterResearch.generateCandidateSpace(series, List.of(domain));

        assertThat(result.candidates()).extracting(ParameterResearch.StrategyCandidate::id)
                .containsExactly("barCount=1", "barCount=5");
    }

    @Test
    void candidateGenerationRejectsNullLiteralValues() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ParameterDomain.values("barCount", Arrays.asList(1, null)));

        assertThat(exception).hasMessageContaining("values cannot contain null entries");
    }

    @Test
    void integerRangeNormalizerReportsMalformedRawValuesWithParameterContext() {
        BarSeries series = buildSeries(5);
        ParameterDomain integerRange = ParameterDomain.integerRange("barCount", 1, 1, 1);
        ParameterDomain malformed = new ParameterDomain("barCount", List.of("oops", "1"), integerRange.normalizer());

        CandidateGenerationResult result = ParameterResearch.generateCandidateSpace(series, List.of(malformed));

        assertThat(result.rawCandidateCount()).isEqualTo(2);
        assertThat(result.candidates()).extracting(ParameterResearch.StrategyCandidate::id)
                .containsExactly("barCount=1");
        assertThat(result.invalidCandidates()).hasSize(1);
        assertThat(result.invalidCandidates().getFirst().reason()).contains("barCount")
                .contains("oops")
                .contains("integer");
    }

    @Test
    void parameterSetIntValueReportsMalformedValuesWithParameterContext() {
        ParameterSet parameters = new ParameterSet(
                List.of(new ParameterResearch.ParameterValue("barCount", "oops", "oops", false, "")));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> parameters.intValue("barCount"));

        assertThat(exception).hasMessageContaining("barCount").hasMessageContaining("oops");
        assertThat(exception).hasCauseInstanceOf(NumberFormatException.class);
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

        assertThat(result.generatedCandidateCount()).isEqualTo(1);
        assertThat(result.candidates()).extracting(ParameterResearch.StrategyCandidate::id)
                .containsExactly("barCount=3");
        assertThat(result.invalidCandidates()).hasSize(1);
        assertThat(result.invalidCandidates().getFirst().stage()).isEqualTo(CandidateFailureStage.GENERATION);
        assertThat(result.invalidCandidates().getFirst().reason()).contains("wrongName");
    }

    @Test
    void candidateGenerationResultRequiresAtLeastOneCandidate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CandidateGenerationResult(List.of(), List.of(), 0L, "hash"));

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

        assertThat(result.rawCandidateCount()).isEqualTo(4);
        assertThat(result.generatedCandidateCount()).isEqualTo(4);
        assertThat(result.candidates()).extracting(ParameterResearch.StrategyCandidate::id)
                .containsExactly("entry=1|exit=2", "entry=1|exit=3");
        assertThat(result.invalidCandidates()).hasSize(2);
    }

    @Test
    void researchGeneratesCandidateSpaceFromTrainingWindowToAvoidHoldoutLengthLeakage() {
        BarSeries series = buildSeries(12);
        ParameterDomain periodDomain = ParameterDomain.periodRange("barCount", 1, 11, 10);
        ResearchConfig config = defaultConfig(series, 5, 7).withPruningPolicy(PruningPolicy.NONE);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(periodDomain),
                CandidateValidator.acceptAll(), ParameterResearchTest::fixedByBarCount, config);

        assertThat(report.window().trainingStartIndex()).isEqualTo(0);
        assertThat(report.window().trainingEndIndex()).isEqualTo(4);
        assertThat(report.trainingScores()).extracting(ParameterResearch.CandidateScore::candidateId)
                .containsOnly("barCount=1", "barCount=5");
        assertThat(report.trainingScores()).extracting(ParameterResearch.CandidateScore::candidateId)
                .doesNotContain("barCount=11");
    }

    @Test
    void noValidatorRunOverloadUsesAcceptAllValidator() {
        BarSeries series = buildSeries(8);
        ParameterDomain domain = ParameterDomain.values("barCount", List.of(1, 2));
        ResearchConfig config = defaultConfig(series, 6, 2).withPruningPolicy(PruningPolicy.NONE);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::fixedByBarCount, config);

        assertThat(report.invalidCandidateCount()).isZero();
        assertThat(report.trainingScores()).extracting(ParameterResearch.CandidateScore::candidateId)
                .containsExactly("barCount=1", "barCount=2");
    }

    @Test
    void holdoutShortcutUsesAllPreHoldoutBarsForTraining() {
        BarSeries series = buildSeries(10);
        ParameterDomain domain = ParameterDomain.values("barCount", List.of(1));
        ResearchConfig config = ResearchConfig
                .holdout(4, RankingProfile.weighted(WeightedCriterion.of(new NetProfitCriterion(), 1.0)),
                        series.numFactory().one(), 1)
                .withPruningPolicy(PruningPolicy.NONE);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::fixedByBarCount, config);

        assertThat(report.window().trainingStartIndex()).isEqualTo(0);
        assertThat(report.window().trainingEndIndex()).isEqualTo(5);
        assertThat(report.window().validationStartIndex()).isEqualTo(6);
        assertThat(report.window().validationEndIndex()).isEqualTo(9);
    }

    @Test
    void exactTradingRecordPruningKeepsRepresentativeForEquivalentExecutedBehavior() {
        BarSeries series = buildSeries(12);
        ParameterDomain domain = ParameterDomain.values("group", List.of(1, 2, 3));
        ResearchConfig config = defaultConfig(series, 8, 4);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain), CandidateValidator.acceptAll(),
                ParameterResearchTest::groupedTradingRecordStrategy, config);

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
    void exactTradingRecordPruningDistinguishesStopLossVariantsOnCurrentClose() {
        double[] prices = { 100, 101, 102, 103, 104, 105, 103, 97, 94, 95, 96, 97, 98 };
        BarSeries series = new MockBarSeriesBuilder().withData(prices).build();
        ParameterDomain domain = ParameterDomain.values("stopPct", List.of(5, 15));
        ResearchConfig config = defaultConfig(series, 9, 0).withTradeExecutionModel(new TradeOnCurrentCloseModel());

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain), CandidateValidator.acceptAll(),
                ParameterResearchTest::stopLossStrategy, config);

        assertThat(report.representativeCount()).isEqualTo(2);
        assertThat(report.prunedCandidateCount()).isZero();
        assertThat(report.pruningGroups()).extracting(PruningGroup::representativeId)
                .containsExactly("stopPct=5", "stopPct=15");
        assertThat(report.pruningGroups())
                .allSatisfy(group -> assertThat(group.reason()).isEqualTo("exact trading record"));
    }

    @Test
    void indicatorDistancePruningIsExplicitAndReportsWarning() {
        BarSeries series = buildSeries(12);
        ParameterDomain domain = ParameterDomain.values("offset", List.of(0, 1, 10));
        ResearchConfig config = defaultConfig(series, 8, 4).withIndicatorDistance(2.0,
                (targetSeries, parameters) -> new OffsetCloseIndicator(targetSeries, parameters.intValue("offset")));

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain), CandidateValidator.acceptAll(),
                ParameterResearchTest::offsetStrategy, config);

        assertThat(report.pruningPolicy()).isEqualTo(PruningPolicy.INDICATOR_DISTANCE);
        assertThat(report.pruningGroups().getFirst().discardedIds()).containsExactly("offset=1");
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("INDICATOR_DISTANCE"));
    }

    @Test
    void indicatorDistanceUsesBestRankedCandidateAsRepresentative() {
        BarSeries series = buildSeries(8);
        ParameterDomain domain = ParameterDomain.values("cycles", List.of(1, 2));
        ResearchConfig config = ResearchConfig
                .holdout(6, 2,
                        RankingProfile.weighted(WeightedCriterion.of(new NumberOfPositionsCriterion(false), 1.0)),
                        series.numFactory().one(), 2)
                .withIndicatorDistance(0.0, (targetSeries, parameters) -> new ClosePriceIndicator(targetSeries));

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain), CandidateValidator.acceptAll(),
                ParameterResearchTest::fixedCycleStrategy, config);

        assertThat(report.pruningGroups()).hasSize(1);
        assertThat(report.pruningGroups().getFirst().representativeId()).isEqualTo("cycles=2");
        assertThat(report.pruningGroups().getFirst().discardedIds()).containsExactly("cycles=1");
    }

    @Test
    void indicatorDistanceReportsBadIndicatorCandidatesWithoutAbortingResearch() {
        BarSeries series = buildSeries(8);
        ParameterDomain domain = ParameterDomain.values("offset", List.of(0, 1));
        ResearchConfig config = defaultConfig(series, 6, 2).withIndicatorDistance(0.0, (targetSeries, parameters) -> {
            int offset = parameters.intValue("offset");
            if (offset == 1) {
                throw new IllegalArgumentException("offset cannot be 1");
            }
            return new OffsetCloseIndicator(targetSeries, offset);
        });

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain), CandidateValidator.acceptAll(),
                ParameterResearchTest::offsetStrategy, config);

        assertThat(report.pruningGroups()).hasSize(1);
        assertThat(report.pruningGroups().getFirst().representativeId()).isEqualTo("offset=0");
        assertThat(report.invalidCandidates()).anySatisfy(candidate -> {
            assertThat(candidate.candidateId()).isEqualTo("offset=1");
            assertThat(candidate.stage()).isEqualTo(CandidateFailureStage.PRUNING_INDICATOR);
            assertThat(candidate.reason()).contains("offset cannot be 1");
        });
    }

    @Test
    void objectiveDistancePruningIsPostEvaluationAndReportsWarning() {
        BarSeries series = buildSeries(12);
        ParameterDomain domain = ParameterDomain.values("group", List.of(1, 2, 3));
        ResearchConfig config = defaultConfig(series, 8, 4).withObjectiveDistance(1.0);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain), CandidateValidator.acceptAll(),
                ParameterResearchTest::objectiveDistanceStrategy, config);

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

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain), CandidateValidator.acceptAll(),
                ParameterResearchTest::fixedCycleStrategy, config);

        assertThat(report.pruningGroups()).hasSize(1);
        assertThat(report.pruningGroups().getFirst().representativeId()).isEqualTo("cycles=2");
        assertThat(report.pruningGroups().getFirst().discardedIds()).containsExactly("cycles=1");
    }

    @Test
    void reportWarnsWhenRequestedWindowsAreReduced() {
        BarSeries series = buildSeries(5);
        ParameterDomain domain = ParameterDomain.values("barCount", List.of(1));
        ResearchConfig config = defaultConfig(series, 20, 20).withPruningPolicy(PruningPolicy.NONE);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain), CandidateValidator.acceptAll(),
                ParameterResearchTest::fixedByBarCount, config);

        assertThat(report.warnings()).anyMatch(warning -> warning.contains("validationBarCount was reduced"));
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("trainingBarCount was reduced"));
    }

    @Test
    void reportSummaryIsBoundedAndIncludesMetricLabels() {
        BarSeries series = buildSeries(8);
        ParameterDomain domain = ParameterDomain.values("cycles", List.of(1, 2, 3));
        ResearchConfig config = defaultConfig(series, 6, 2).withPruningPolicy(PruningPolicy.NONE);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::fixedCycleStrategy, config);

        String summary = report.formatSummary(1);

        assertThat(summary).contains("Candidate space:");
        assertThat(summary).contains("Training top candidates:");
        assertThat(summary).contains("Validation top candidates:");
        assertThat(summary).contains("Net Profit=");
        assertThat(summary).contains("raw=");
        assertThat(summary).contains("... 2 more");
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

    @Test
    void researchExecutionMatchesDirectManagerRunOnFullSeries() {
        BarSeries series = buildSeriesWithOpens(100, 100, 100, 100, 100, 100, 100, 100, 110, 120, 130, 140);
        ParameterDomain domain = ParameterDomain.values("x", List.of(1));
        NetProfitCriterion criterion = new NetProfitCriterion();
        ResearchConfig config = ResearchConfig.holdout(8, 4,
                RankingProfile.weighted(WeightedCriterion.of(criterion, 1.0)), series.numFactory().one(), 3);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::smaCrossStrategy, config);

        Strategy strategy = smaCrossStrategy(series, report.candidates().getFirst().parameters());
        Num oracleTraining = criterion.calculate(series,
                new BarSeriesManager(series).run(strategy, TradeType.BUY, series.numFactory().one(), 0, 7));
        Num oracleValidation = criterion.calculate(series,
                new BarSeriesManager(series).run(strategy, TradeType.BUY, series.numFactory().one(), 8, 11));

        assertThat(report.trainingScores()).hasSize(1);
        assertThat(report.trainingScores().getFirst().metricValues().get(criterion))
                .isEqualByComparingTo(oracleTraining);
        assertThat(report.validationScores()).hasSize(1);
        assertThat(report.validationScores().getFirst().metricValues().get(criterion))
                .isEqualByComparingTo(oracleValidation);
    }

    @Test
    void nullTradeTypeHonorsStrategyStartingType() {
        BarSeries series = buildSeriesWithOpens(111, 110, 109, 108, 107, 106, 105, 104, 103, 102, 101, 100);
        ParameterDomain domain = ParameterDomain.values("x", List.of(1));
        NetProfitCriterion criterion = new NetProfitCriterion();
        ResearchConfig baseConfig = ResearchConfig.holdout(8, 4,
                RankingProfile.weighted(WeightedCriterion.of(criterion, 1.0)), series.numFactory().one(), 3);

        ParameterResearchReport nullTypeReport = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::sellStrategy, baseConfig);
        ParameterResearchReport buyOverrideReport = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::sellStrategy, baseConfig.withTradeType(TradeType.BUY));

        Strategy strategy = sellStrategy(series, nullTypeReport.candidates().getFirst().parameters());
        Num oracleSell = criterion.calculate(series,
                new BarSeriesManager(series).run(strategy, TradeType.SELL, series.numFactory().one(), 0, 7));
        Num oracleBuy = criterion.calculate(series,
                new BarSeriesManager(series).run(strategy, TradeType.BUY, series.numFactory().one(), 0, 7));

        Num nullTypeMetric = nullTypeReport.trainingScores().getFirst().metricValues().get(criterion);
        Num buyMetric = buyOverrideReport.trainingScores().getFirst().metricValues().get(criterion);

        assertThat(nullTypeMetric).isEqualByComparingTo(oracleSell);
        assertThat(buyMetric).isEqualByComparingTo(oracleBuy);
        assertThat(nullTypeMetric).isNotEqualByComparingTo(buyMetric);
    }

    @Test
    void researchThreadsCostsAndExecutionModelIntoManager() {
        BarSeries series = buildSeriesWithOpens(100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111);
        ParameterDomain domain = ParameterDomain.values("x", List.of(1));
        NetProfitCriterion criterion = new NetProfitCriterion();
        ResearchConfig costedConfig = ResearchConfig
                .holdout(8, 4, RankingProfile.weighted(WeightedCriterion.of(criterion, 1.0)), series.numFactory().one(),
                        3)
                .withCosts(new FixedTransactionCostModel(1.0), new ZeroCostModel())
                .withTradeExecutionModel(new TradeOnCurrentCloseModel());

        ParameterResearchReport costedReport = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::midTradeStrategy, costedConfig);
        ParameterResearchReport zeroCostReport = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::midTradeStrategy, ResearchConfig.holdout(8, 4,
                        RankingProfile.weighted(WeightedCriterion.of(criterion, 1.0)), series.numFactory().one(), 3));

        Strategy strategy = midTradeStrategy(series, costedReport.candidates().getFirst().parameters());
        Num oracleCosted = criterion.calculate(series,
                new BarSeriesManager(series, costedConfig.transactionCostModel(), costedConfig.holdingCostModel(),
                        costedConfig.tradeExecutionModel(), costedConfig.tradingRecordFactory())
                        .run(strategy, TradeType.BUY, series.numFactory().one(), 0, 7));

        Num costedMetric = costedReport.trainingScores().getFirst().metricValues().get(criterion);
        Num zeroCostMetric = zeroCostReport.trainingScores().getFirst().metricValues().get(criterion);

        assertThat(costedMetric).isEqualByComparingTo(oracleCosted);
        assertThat(costedMetric).isNotEqualByComparingTo(zeroCostMetric);
    }

    @Test
    void executionFailuresAreIsolatedPerCandidateAndStaged() {
        BarSeries series = buildSeries(8);
        ParameterDomain domain = ParameterDomain.values("group", List.of(1, 2, 3, 4));
        ResearchConfig config = defaultConfig(series, 6, 2).withPruningPolicy(PruningPolicy.NONE);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain), CandidateValidator.acceptAll(),
                ParameterResearchTest::runtimeThrowingStrategy, config);

        assertThat(report.generatedCandidateCount()).isEqualTo(4);
        assertThat(report.validCandidateCount()).isEqualTo(3);
        assertThat(report.invalidCandidateCount()).isEqualTo(1);
        assertThat(report.invalidCandidates()).hasSize(1);
        assertThat(report.invalidCandidates().getFirst().candidateId()).isEqualTo("group=2");
        assertThat(report.invalidCandidates().getFirst().stage()).isEqualTo(CandidateFailureStage.TRAINING_EXECUTION);
        assertThat(report.invalidCandidates().getFirst().reason()).contains("boom");
        assertThat(report.invalidCandidateCountByStage()).containsEntry(CandidateFailureStage.TRAINING_EXECUTION, 1);
        assertThat(report.invalidStageBreakdown()).contains("training-execution=1");
        assertThat(report.trainingScores()).hasSize(3);
    }

    @Test
    void runRejectsWhenAllCandidatesFailToBuild() {
        BarSeries series = buildSeries(8);
        ParameterDomain domain = ParameterDomain.values("group", List.of(1, 2));
        ResearchConfig config = defaultConfig(series, 6, 2);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> ParameterResearch
                .run(series, List.of(domain), CandidateValidator.acceptAll(), (targetSeries, parameters) -> {
                    throw new IllegalStateException("cannot build");
                }, config));

        assertThat(exception).hasMessageContaining("No candidates could be evaluated on the training window");
    }

    @Test
    void terminalIndexSeriesIsSafeAcrossGenerationExecutionPruningAndIndicatorCapture() {
        BarSeries proxy = new MaxIndexBarSeries("terminal", new MockBarSeriesBuilder().withData(100d).build());
        ParameterDomain domain = ParameterDomain.integerRange("barCount", 1, 1, 1, 1, 1, true);
        ResearchConfig config = ResearchConfig.holdout(0, 0,
                RankingProfile.weighted(WeightedCriterion.of(new NetProfitCriterion(), 1.0)), proxy.numFactory().one(),
                1);
        ParameterResearch.StrategyFactory factory = (series, parameters) -> new BaseStrategy(parameters.stableId(),
                new FixedRule(Integer.MAX_VALUE), new FixedRule(Integer.MAX_VALUE));

        ParameterResearchReport exactRecordReport = ParameterResearch.run(proxy, List.of(domain), factory, config);
        ParameterResearchReport indicatorReport = ParameterResearch.run(proxy, List.of(domain), factory,
                config.withIndicatorDistance(0.0, (targetSeries, parameters) -> new ClosePriceIndicator(targetSeries)));

        assertThat(exactRecordReport.window().trainingStartIndex()).isEqualTo(Integer.MAX_VALUE);
        assertThat(exactRecordReport.window().trainingEndIndex()).isEqualTo(Integer.MAX_VALUE);
        assertThat(exactRecordReport.barCount()).isEqualTo(1);
        assertThat(exactRecordReport.trainingScores()).hasSize(1);
        assertThat(exactRecordReport.pruningGroups()).hasSize(1);
        assertThat(exactRecordReport.pruningGroups().getFirst().representativeId()).isEqualTo("barCount=1");
        assertThat(exactRecordReport.warnings()).anyMatch(warning -> warning.contains("restarts at index 0"));
        assertThat(indicatorReport.trainingScores()).hasSize(1);
        assertThat(indicatorReport.pruningGroups()).hasSize(1);
        assertThat(indicatorReport.pruningGroups().getFirst().representativeId()).isEqualTo("barCount=1");
    }

    @Test
    void generateCandidateSpaceEnforcesCardinalityBudgetBeforeMaterialization() {
        BarSeries series = buildSeries(10);
        List<ParameterDomain> smallDomains = List.of(
                ParameterDomain.values("a", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
                ParameterDomain.values("b", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)));

        CandidateGenerationResult capped = ParameterResearch.generateCandidateSpace(series, smallDomains,
                CandidateValidator.acceptAll(), 100);
        assertThat(capped.rawCandidateCount()).isEqualTo(100);
        assertThat(capped.candidates()).hasSize(100);

        IllegalArgumentException belowBudget = assertThrows(IllegalArgumentException.class, () -> ParameterResearch
                .generateCandidateSpace(series, smallDomains, CandidateValidator.acceptAll(), 99));
        assertThat(belowBudget).hasMessageContaining("exceeds the maximum of 99");

        IllegalArgumentException zeroBudget = assertThrows(IllegalArgumentException.class, () -> ParameterResearch
                .generateCandidateSpace(series, smallDomains, CandidateValidator.acceptAll(), 0));
        assertThat(zeroBudget).hasMessageContaining("maxCombinations must be > 0");

        List<ParameterDomain> hugeDomains = List.of(ParameterDomain.integerRange("a", 1, 100_000, 1),
                ParameterDomain.integerRange("b", 1, 100_000, 1));
        IllegalArgumentException overBudget = assertThrows(IllegalArgumentException.class, () -> ParameterResearch
                .generateCandidateSpace(series, hugeDomains, CandidateValidator.acceptAll(), 10));
        assertThat(overBudget).hasMessageContaining("exceeds the maximum of 10");

        CandidateGenerationResult exactBudget = ParameterResearch.generateCandidateSpace(series,
                List.of(ParameterDomain.integerRange("a", 1, 1000, 1)), CandidateValidator.acceptAll(), 1000);
        assertThat(exactBudget.rawCandidateCount()).isEqualTo(1000);
        assertThat(exactBudget.candidates()).hasSize(1000);
    }

    @Test
    void integerRangeExposesLazyUnmodifiableValuesAndOverflowsSafely() {
        ParameterDomain range = ParameterDomain.integerRange("x", 5, 9, 2);
        assertThat(range.rawValues()).containsExactly("5", "7", "9");
        assertThat(range.rawValues().get(1)).isEqualTo("7");

        ParameterDomain huge = ParameterDomain.integerRange("big", 1, 100_000, 1);
        assertThat(huge.rawValues()).hasSize(100_000);
        assertThat(huge.rawValues().get(0)).isEqualTo("1");
        assertThat(huge.rawValues().get(99_999)).isEqualTo("100000");

        assertThrows(UnsupportedOperationException.class, () -> range.rawValues().add("11"));
        assertThrows(UnsupportedOperationException.class,
                () -> ParameterDomain.values("x", List.of("a")).rawValues().add("b"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ParameterDomain.integerRange("x", Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
        assertThat(exception).hasMessageContaining("Integer range is too large");
    }

    @Test
    void stableIdEscapesSeparatorCharactersAndCandidateIdsValidate() {
        ParameterSet multiValue = new ParameterSet(
                List.of(new ParameterResearch.ParameterValue("a", "x|y", "x|y", false, ""),
                        new ParameterResearch.ParameterValue("b", "p=q", "p=q", false, "")));
        assertThat(multiValue.stableId()).isEqualTo("a=x\\|y|b=p\\=q");

        ParameterSet twoParams = new ParameterSet(
                List.of(new ParameterResearch.ParameterValue("a", "x", "x", false, ""),
                        new ParameterResearch.ParameterValue("b", "y", "y", false, "")));
        ParameterSet singleParam = new ParameterSet(
                List.of(new ParameterResearch.ParameterValue("a", "x|b=y", "x|b=y", false, "")));
        assertThat(twoParams.stableId()).isEqualTo("a=x|b=y");
        assertThat(singleParam.stableId()).isEqualTo("a=x\\|b\\=y");
        assertThat(twoParams.stableId()).isNotEqualTo(singleParam.stableId());

        ParameterSet backslashValue = new ParameterSet(
                List.of(new ParameterResearch.ParameterValue("a", "x\\y", "x\\y", false, "")));
        assertThat(backslashValue.stableId()).isEqualTo("a=x\\\\y");

        ParameterSet params = new ParameterSet(List.of(new ParameterResearch.ParameterValue("a", "1", "1", false, "")));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ParameterResearch.StrategyCandidate("not-the-id", params));
        assertThat(exception).hasMessageContaining("id must equal the stable id");
        assertThat(new ParameterResearch.StrategyCandidate(params.stableId(), params).id()).isEqualTo("a=1");
    }

    @Test
    void pruningGroupEnforcesRepresentativeFirstMemberUniquenessAndDistance() {
        IllegalArgumentException representative = assertThrows(IllegalArgumentException.class,
                () -> new PruningGroup("a", List.of("b", "a"), "r", 0d));
        assertThat(representative).hasMessageContaining("representative must be the first member");

        assertThrows(IllegalArgumentException.class, () -> new PruningGroup("a", List.of("a", "a"), "r", 0d));
        assertThrows(IllegalArgumentException.class, () -> new PruningGroup("a", List.of("a", ""), "r", 0d));
        assertThrows(IllegalArgumentException.class, () -> new PruningGroup("a", List.of("a"), "r", -0.5));
        assertThrows(IllegalArgumentException.class, () -> new PruningGroup("a", List.of("a"), "r", Double.NaN));

        PruningGroup group = new PruningGroup("a", List.of("a", "b", "c"), null, 0d);
        assertThat(group.reason()).isEmpty();
        assertThat(group.discardedIds()).containsExactly("b", "c");
        assertThat(group.maximumDistance()).isZero();
    }

    @Test
    void reportRejectsInconsistentCounts() {
        BarSeries series = buildSeries(8);
        ParameterDomain domain = ParameterDomain.values("cycles", List.of(1));
        ParameterResearchReport valid = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::fixedCycleStrategy,
                defaultConfig(series, 6, 2).withPruningPolicy(PruningPolicy.NONE));

        IllegalArgumentException rawCount = assertThrows(IllegalArgumentException.class,
                () -> new ParameterResearchReport(valid.datasetId(), valid.barCount(), valid.window(),
                        valid.candidateSpaceHash(), valid.pruningPolicy(), valid.rawCandidateCount() - 1,
                        valid.generatedCandidateCount(), valid.validCandidateCount(), valid.invalidCandidateCount(),
                        valid.candidates(), valid.baselineTopCandidateId(), valid.selectedTopCandidateId(),
                        valid.pruningGroups(), valid.baselineScores(), valid.trainingScores(), valid.validationScores(),
                        valid.invalidCandidates(), valid.warnings(), valid.trainingRuntimeReport(),
                        valid.validationRuntimeReport()));
        assertThat(rawCount).hasMessageContaining("rawCandidateCount must be >= generatedCandidateCount");

        IllegalArgumentException invalidCount = assertThrows(IllegalArgumentException.class,
                () -> new ParameterResearchReport(valid.datasetId(), valid.barCount(), valid.window(),
                        valid.candidateSpaceHash(), valid.pruningPolicy(), valid.rawCandidateCount(),
                        valid.generatedCandidateCount(), valid.validCandidateCount(), valid.invalidCandidateCount() + 1,
                        valid.candidates(), valid.baselineTopCandidateId(), valid.selectedTopCandidateId(),
                        valid.pruningGroups(), valid.baselineScores(), valid.trainingScores(), valid.validationScores(),
                        valid.invalidCandidates(), valid.warnings(), valid.trainingRuntimeReport(),
                        valid.validationRuntimeReport()));
        assertThat(invalidCount).hasMessageContaining("invalidCandidateCount must match invalidCandidates.size()");
    }

    @Test
    void metricValuesKeyCriteriaByIdentityAndSummaryDisambiguatesLabels() {
        BarSeries series = buildSeries(8);
        ParameterDomain domain = ParameterDomain.values("cycles", List.of(1));
        NetProfitCriterion netProfit = new NetProfitCriterion();
        ValueAtRiskCriterion valueAtRisk95 = new ValueAtRiskCriterion(0.95);
        ValueAtRiskCriterion valueAtRisk99 = new ValueAtRiskCriterion(0.99);
        ResearchConfig config = ResearchConfig
                .holdout(6, 2,
                        RankingProfile.weighted(WeightedCriterion.of(netProfit, 1.0),
                                WeightedCriterion.of(valueAtRisk95, 1.0), WeightedCriterion.of(valueAtRisk99, 1.0)),
                        series.numFactory().one(), 1)
                .withPruningPolicy(PruningPolicy.NONE);

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::fixedCycleStrategy, config);

        Map<AnalysisCriterion, Num> metrics = report.trainingScores().getFirst().metricValues();
        assertThat(metrics).hasSize(3);
        assertThat(metrics).containsKeys(netProfit, valueAtRisk95, valueAtRisk99);
        assertThat(metrics.keySet()).filteredOn(criterion -> criterion instanceof ValueAtRiskCriterion).hasSize(2);

        String summary = report.formatSummary(1);
        assertThat(summary).contains("Net Profit=").contains("Value At Risk #1=").contains("Value At Risk #2=");
    }

    @Test
    void generationStagesCountSeparatelyAndReportValidatesCounts() {
        BarSeries series = buildSeries(8);
        List<String> rawValues = new ArrayList<>(200);
        for (int i = 0; i < 200; i++) {
            rawValues.add(i % 2 == 0 ? "v" + i : "bad-" + i);
        }
        ParameterDomain domain = new ParameterDomain("x", rawValues, (targetSeries, name, rawValue) -> {
            if (rawValue.startsWith("bad-")) {
                throw new IllegalArgumentException("malformed value " + rawValue);
            }
            return new ParameterResearch.ParameterValue(name, rawValue, rawValue, false, "");
        });

        CandidateGenerationResult result = ParameterResearch.generateCandidateSpace(series, List.of(domain));
        assertThat(result.rawCandidateCount()).isEqualTo(200);
        assertThat(result.generatedCandidateCount()).isEqualTo(100);
        assertThat(result.candidates()).hasSize(100);
        assertThat(result.invalidCandidates()).hasSize(100);
        assertThat(result.invalidCandidates())
                .allSatisfy(candidate -> assertThat(candidate.stage()).isEqualTo(CandidateFailureStage.GENERATION));

        ParameterResearchReport report = ParameterResearch.run(series, List.of(domain),
                ParameterResearchTest::plainStrategy, defaultConfig(series, 6, 2));
        assertThat(report.rawCandidateCount()).isEqualTo(200);
        assertThat(report.generatedCandidateCount()).isEqualTo(100);
        assertThat(report.validCandidateCount()).isEqualTo(100);
        assertThat(report.invalidCandidateCount()).isEqualTo(100);
        assertThat(report.invalidStageBreakdown()).isEqualTo("{generation=100}");
        String summary = report.formatSummary(0);
        assertThat(summary).contains("raw=200")
                .contains("generated=100")
                .contains("valid=100")
                .contains("invalid=100")
                .contains("stages={generation=100}");
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
        int entryIndex = group <= 2 ? series.getBeginIndex() : series.getBeginIndex() + 3;
        return new BaseStrategy(parameters.stableId(), new FixedRule(entryIndex), new FixedRule(series.getEndIndex()));
    }

    private static Strategy objectiveDistanceStrategy(BarSeries series, ParameterSet parameters) {
        return new BaseStrategy(parameters.stableId(), new FixedRule(series.getBeginIndex()),
                new FixedRule(series.getEndIndex()));
    }

    private static Strategy offsetStrategy(BarSeries series, ParameterSet parameters) {
        int offset = parameters.intValue("offset");
        int entryIndex = offset <= 1 ? series.getBeginIndex() : series.getBeginIndex() + 1;
        return new BaseStrategy(parameters.stableId(), new FixedRule(entryIndex), new FixedRule(series.getEndIndex()));
    }

    private static Strategy fixedCycleStrategy(BarSeries series, ParameterSet parameters) {
        int cycles = parameters.intValue("cycles");
        if (cycles == 1) {
            return new BaseStrategy(parameters.stableId(), new FixedRule(0), new FixedRule(1));
        }
        return new BaseStrategy(parameters.stableId(), new FixedRule(0, 2), new FixedRule(1, 3));
    }

    private static Strategy stopLossStrategy(BarSeries series, ParameterSet parameters) {
        double stopPct = Double.parseDouble(parameters.value("stopPct"));
        return new BaseStrategy(parameters.stableId(), new FixedRule(5),
                new StopLossRule(new ClosePriceIndicator(series), series.numFactory().numOf(stopPct)));
    }

    private static Strategy smaCrossStrategy(BarSeries series, ParameterSet parameters) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        return new BaseStrategy(parameters.stableId(),
                new CrossedUpIndicatorRule(new SMAIndicator(close, 2), new SMAIndicator(close, 4)),
                new FixedRule(series.getEndIndex()));
    }

    private static Strategy sellStrategy(BarSeries series, ParameterSet parameters) {
        return new BaseStrategy(parameters.stableId(), new FixedRule(1), new FixedRule(2), TradeType.SELL);
    }

    private static Strategy midTradeStrategy(BarSeries series, ParameterSet parameters) {
        return new BaseStrategy(parameters.stableId(), new FixedRule(5), new FixedRule(6));
    }

    private static Strategy runtimeThrowingStrategy(BarSeries series, ParameterSet parameters) {
        if (parameters.intValue("group") == 2) {
            return new BaseStrategy(parameters.stableId(), new FixedRule(1), (index, record) -> {
                throw new IllegalStateException("boom");
            });
        }
        return new BaseStrategy(parameters.stableId(), new FixedRule(1), new FixedRule(6));
    }

    private static Strategy plainStrategy(BarSeries series, ParameterSet parameters) {
        return new BaseStrategy(parameters.stableId(), new FixedRule(0), new FixedRule(1));
    }

    private static BarSeries buildSeries(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + i;
        }
        return new MockBarSeriesBuilder().withData(prices).build();
    }

    private static BarSeries buildSeriesWithOpens(double... closes) {
        BaseBarSeries series = new BaseBarSeriesBuilder().withName("price").build();
        double previousClose = closes[0];
        for (int i = 0; i < closes.length; i++) {
            series.barBuilder()
                    .timePeriod(Duration.ofMinutes(1))
                    .beginTime(Instant.EPOCH.plus(Duration.ofMinutes(i)))
                    .openPrice(previousClose)
                    .closePrice(closes[i])
                    .add();
            previousClose = closes[i];
        }
        return series;
    }

    private static final class MaxIndexBarSeries extends BaseBarSeries {

        private final Bar innerBar;

        private MaxIndexBarSeries(String name, BarSeries inner) {
            super(name, inner.getBarData());
            this.innerBar = inner.getBar(0);
        }

        @Override
        public Bar getBar(int index) {
            return innerBar;
        }

        @Override
        public int getBarCount() {
            return 1;
        }

        @Override
        public int getBeginIndex() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getEndIndex() {
            return Integer.MAX_VALUE;
        }
    }

    private static final class FixtureNamedStrategy extends NamedStrategy {

        private FixtureNamedStrategy(BarSeries series, int entryIndex, int exitIndex) {
            super(NamedStrategy.buildLabel(FixtureNamedStrategy.class, String.valueOf(entryIndex),
                    String.valueOf(exitIndex)), entryRule(series, entryIndex), exitRule(series, exitIndex));
        }

        private FixtureNamedStrategy(BarSeries series, String... params) {
            this(series, parseStrategyParameter("entryIndex", params[0]),
                    parseStrategyParameter("exitIndex", params[1]));
        }

        private static int parseStrategyParameter(String name, String rawValue) {
            try {
                return Integer.parseInt(rawValue);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Strategy parameter " + name + " must be an integer: " + rawValue,
                        ex);
            }
        }

        private static Rule entryRule(BarSeries series, int entryIndex) {
            Objects.requireNonNull(series, "series");
            return new FixedRule(entryIndex);
        }

        private static Rule exitRule(BarSeries series, int exitIndex) {
            Objects.requireNonNull(series, "series");
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
