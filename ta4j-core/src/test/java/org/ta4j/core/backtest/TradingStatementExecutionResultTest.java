/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.walkforward.WalkForwardRuntimeReport;

public class TradingStatementExecutionResultTest extends AbstractIndicatorTest<BarSeries, Num> {

    public TradingStatementExecutionResultTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void backtestResultExposesSharedContractCriterionAndRecordViews() {
        BacktestExecutionResult result = createBacktestResult();
        TradingStatementExecutionResult<BacktestRuntimeReport> executionResult = result;
        AnalysisCriterion criterion = new NumberOfPositionsCriterion();

        List<TradingRecord> records = executionResult.tradingRecords();
        assertEquals(result.tradingStatements().size(), records.size());
        for (int i = 0; i < result.tradingStatements().size(); i++) {
            assertSame(result.tradingStatements().get(i).getTradingRecord(), records.get(i));
        }

        List<Num> expectedValues = new ArrayList<>();
        for (TradingStatement statement : result.tradingStatements()) {
            expectedValues.add(criterion.calculate(result.barSeries(), statement.getTradingRecord()));
        }
        List<Num> actualValues = executionResult.criterionValues(criterion);
        assertEquals(expectedValues, actualValues);

        Map<Integer, Num> valuesByIndex = executionResult.criterionValuesByIndex(criterion);
        assertEquals(actualValues.size(), valuesByIndex.size());
        for (int i = 0; i < actualValues.size(); i++) {
            assertEquals(actualValues.get(i), valuesByIndex.get(i));
        }
    }

    @Test
    public void walkForwardResultExposesSharedContractCriterionAndRecordViews() {
        StrategyWalkForwardExecutionResult result = createWalkForwardResult();
        TradingStatementExecutionResult<WalkForwardRuntimeReport> executionResult = result;
        AnalysisCriterion criterion = new NumberOfPositionsCriterion();

        List<TradingRecord> records = executionResult.tradingRecords();
        assertEquals(result.tradingStatements().size(), records.size());
        for (int i = 0; i < result.tradingStatements().size(); i++) {
            assertSame(result.tradingStatements().get(i).getTradingRecord(), records.get(i));
        }

        List<Num> expectedValues = new ArrayList<>();
        for (TradingStatement statement : result.tradingStatements()) {
            expectedValues.add(criterion.calculate(result.barSeries(), statement.getTradingRecord()));
        }
        List<Num> actualValues = executionResult.criterionValues(criterion);
        assertEquals(expectedValues, actualValues);
        assertEquals(expectedValues, result.criterionValues(criterion));

        Map<Integer, Num> valuesByIndex = executionResult.criterionValuesByIndex(criterion);
        assertEquals(actualValues.size(), valuesByIndex.size());
        for (int i = 0; i < actualValues.size(); i++) {
            assertEquals(actualValues.get(i), valuesByIndex.get(i));
        }
    }

    @Test
    public void weightedRankingNormalizesEquivalentMultipliersToSameScores() {
        BacktestExecutionResult result = createBacktestResult();
        MappedCriterion criterionOne = mappedCriterion(result, true, 9.0, 6.0, 3.0);
        MappedCriterion criterionTwo = mappedCriterion(result, true, 2.0, 5.0, 8.0);
        MappedCriterion criterionThree = mappedCriterion(result, false, 9.0, 5.0, 1.0);

        TradingStatementExecutionResult.RankingProfile profileA = TradingStatementExecutionResult.RankingProfile.of(
                weightedCriterion(criterionOne, 1.5), weightedCriterion(criterionTwo, 1.1),
                weightedCriterion(criterionThree, 0.8));
        TradingStatementExecutionResult.RankingProfile profileB = TradingStatementExecutionResult.RankingProfile.of(
                weightedCriterion(criterionOne, 15.0), weightedCriterion(criterionTwo, 11.0),
                weightedCriterion(criterionThree, 8.0));

        List<TradingStatementExecutionResult.RankedTradingStatement> rankedA = result.rankTradingStatements(profileA);
        List<TradingStatementExecutionResult.RankedTradingStatement> rankedB = result.rankTradingStatements(profileB);

        assertEquals(rankedA.size(), rankedB.size());
        for (int i = 0; i < rankedA.size(); i++) {
            assertEquals(rankedA.get(i).originalIndex(), rankedB.get(i).originalIndex());
            assertEquals(rankedA.get(i).compositeScore().doubleValue(), rankedB.get(i).compositeScore().doubleValue(),
                    1.0e-12);
        }
    }

    @Test
    public void weightedFactoriesAndConvenienceAliasesPreserveDefaultBehavior() {
        BacktestExecutionResult result = createBacktestResult();
        MappedCriterion criterionOne = mappedCriterion(result, true, 3.0, 2.0, 1.0);
        MappedCriterion criterionTwo = mappedCriterion(result, false, 1.0, 2.0, 3.0);

        TradingStatementExecutionResult.WeightedCriterion equalWeight = TradingStatementExecutionResult.WeightedCriterion
                .of(criterionOne);
        TradingStatementExecutionResult.WeightedCriterion explicitWeight = TradingStatementExecutionResult.WeightedCriterion
                .of(criterionTwo, 2.5);

        assertEquals(1.0, equalWeight.multiplier().doubleValue(), 1.0e-12);
        assertEquals(2.5, explicitWeight.multiplier().doubleValue(), 1.0e-12);

        TradingStatementExecutionResult.RankingProfile expectedProfile = TradingStatementExecutionResult.RankingProfile
                .of(equalWeight, explicitWeight);
        TradingStatementExecutionResult.RankingProfile actualProfile = TradingStatementExecutionResult.RankingProfile
                .weighted(equalWeight, explicitWeight);

        assertEquals(expectedProfile, actualProfile);
        assertSame(TradingStatementExecutionResult.DirectionAwareMinMaxNormalizer.INSTANCE, actualProfile.normalizer());
        assertEquals(TradingStatementExecutionResult.MissingValuePolicy.WORST_SCORE,
                actualProfile.missingValuePolicy());
    }

    @Test
    public void weightedRankingHandlesMixedCriterionDirections() {
        BacktestExecutionResult result = createBacktestResult();
        MappedCriterion rewardCriterion = mappedCriterion(result, true, 10.0, 7.0, 4.0);
        MappedCriterion drawdownCriterion = mappedCriterion(result, false, 10.0, 5.0, 1.0);

        TradingStatementExecutionResult.RankingProfile profile = TradingStatementExecutionResult.RankingProfile
                .of(weightedCriterion(rewardCriterion, 1.0), weightedCriterion(drawdownCriterion, 1.0));

        List<Integer> rankedIndexes = rankedIndexes(result.rankTradingStatements(profile));
        assertEquals(List.of(1, 0, 2), rankedIndexes);
    }

    @Test
    public void weightedRankingUsesWorstScoreForMissingValuesByDefault() {
        BacktestExecutionResult result = createBacktestResult();
        MappedCriterion missingCriterion = mappedCriterion(result, true, 3.0, Double.NaN, 1.0);
        MappedCriterion baselineCriterion = mappedCriterion(result, true, 3.0, 2.0, 1.0);

        TradingStatementExecutionResult.RankingProfile profile = TradingStatementExecutionResult.RankingProfile
                .of(weightedCriterion(missingCriterion, 1.0), weightedCriterion(baselineCriterion, 1.0));

        List<TradingStatementExecutionResult.RankedTradingStatement> ranked = result.rankTradingStatements(profile);
        assertEquals(List.of(0, 1, 2), rankedIndexes(ranked));
        assertEquals(3, ranked.size());

        TradingStatementExecutionResult.RankedTradingStatement middle = ranked.get(1);
        assertEquals(1, middle.originalIndex());
        assertEquals(0.25, middle.compositeScore().doubleValue(), 1.0e-12);
        assertTrue(middle.rawScores().get(missingCriterion).isNaN());
        assertEquals(0.0, middle.normalizedScores().get(missingCriterion).doubleValue(), 1.0e-12);
    }

    @Test
    public void weightedRankingZeroSpreadCriterionDoesNotChangeOrdering() {
        BacktestExecutionResult result = createBacktestResult();
        MappedCriterion zeroSpreadCriterion = mappedCriterion(result, true, 5.0, 5.0, 5.0);
        MappedCriterion variableCriterion = mappedCriterion(result, true, 1.0, 2.0, 3.0);

        TradingStatementExecutionResult.RankingProfile withZeroSpread = TradingStatementExecutionResult.RankingProfile
                .of(weightedCriterion(zeroSpreadCriterion, 1.0), weightedCriterion(variableCriterion, 1.0));
        TradingStatementExecutionResult.RankingProfile variableOnly = TradingStatementExecutionResult.RankingProfile
                .of(weightedCriterion(variableCriterion, 1.0));

        List<Integer> withZeroSpreadOrder = rankedIndexes(result.rankTradingStatements(withZeroSpread));
        List<Integer> variableOnlyOrder = rankedIndexes(result.rankTradingStatements(variableOnly));

        assertEquals(variableOnlyOrder, withZeroSpreadOrder);
    }

    @Test
    public void weightedRankingUsesStableOriginalIndexTieBreaks() {
        BacktestExecutionResult result = createBacktestResult();
        MappedCriterion tieCriterion = mappedCriterion(result, true, 2.0, 2.0, 2.0);
        TradingStatementExecutionResult.RankingProfile profile = TradingStatementExecutionResult.RankingProfile
                .of(weightedCriterion(tieCriterion, 1.0));

        List<Integer> rankedIndexes = rankedIndexes(result.rankTradingStatements(profile));
        assertEquals(List.of(0, 1, 2), rankedIndexes);
    }

    @Test
    public void weightedRankingSupportsPluggableNormalizer() {
        BacktestExecutionResult result = createBacktestResult();
        MappedCriterion criterion = mappedCriterion(result, true, 3.0, 2.0, 1.0);

        TradingStatementExecutionResult.RankingProfile defaultProfile = TradingStatementExecutionResult.RankingProfile
                .of(weightedCriterion(criterion, 1.0));
        TradingStatementExecutionResult.CriterionNormalizer inverseNormalizer = (analysisCriterion, rawValue, bestValue,
                worstValue, factory) -> factory.one()
                        .minus(TradingStatementExecutionResult.DirectionAwareMinMaxNormalizer.INSTANCE
                                .normalize(analysisCriterion, rawValue, bestValue, worstValue, factory));
        TradingStatementExecutionResult.RankingProfile invertedProfile = new TradingStatementExecutionResult.RankingProfile(
                List.of(weightedCriterion(criterion, 1.0)), inverseNormalizer,
                TradingStatementExecutionResult.MissingValuePolicy.WORST_SCORE);

        assertEquals(List.of(0, 1, 2), rankedIndexes(result.rankTradingStatements(defaultProfile)));
        assertEquals(List.of(2, 1, 0), rankedIndexes(result.rankTradingStatements(invertedProfile)));
    }

    @Test
    public void weightedRankingWorksForWalkForwardResultThroughSharedContract() {
        StrategyWalkForwardExecutionResult result = createWalkForwardResult();
        int statementCount = result.tradingStatements().size();
        assertTrue(statementCount >= 2);

        double[] descendingValues = new double[statementCount];
        for (int i = 0; i < statementCount; i++) {
            descendingValues[i] = statementCount - i;
        }
        MappedCriterion criterion = mappedCriterion(result, true, descendingValues);
        TradingStatementExecutionResult.RankingProfile profile = TradingStatementExecutionResult.RankingProfile
                .of(weightedCriterion(criterion, 1.0));

        List<TradingStatementExecutionResult.RankedTradingStatement> ranked = result.rankTradingStatements(profile);
        assertFalse(ranked.isEmpty());
        assertEquals(0, ranked.get(0).originalIndex());

        List<TradingStatement> top = result.topTradingStatements(2, profile);
        assertEquals(Math.min(2, statementCount), top.size());
        assertSame(result.tradingStatements().getFirst(), top.getFirst());
    }

    @Test
    public void weightedRankingConvenienceOverloadsMatchProfileBehavior() {
        BacktestExecutionResult result = createBacktestResult();
        MappedCriterion criterionOne = mappedCriterion(result, true, 4.0, 3.0, 1.0);
        MappedCriterion criterionTwo = mappedCriterion(result, false, 5.0, 4.0, 2.0);
        TradingStatementExecutionResult.WeightedCriterion weightedOne = TradingStatementExecutionResult.WeightedCriterion
                .of(criterionOne, 3.0);
        TradingStatementExecutionResult.WeightedCriterion weightedTwo = TradingStatementExecutionResult.WeightedCriterion
                .of(criterionTwo, 1.0);
        TradingStatementExecutionResult.RankingProfile profile = TradingStatementExecutionResult.RankingProfile
                .weighted(weightedOne, weightedTwo);

        List<TradingStatementExecutionResult.RankedTradingStatement> rankedFromProfile = result
                .rankTradingStatements(profile);
        List<TradingStatementExecutionResult.RankedTradingStatement> rankedFromVarargs = result
                .rankTradingStatements(weightedOne, weightedTwo);
        assertEquals(rankedIndexes(rankedFromProfile), rankedIndexes(rankedFromVarargs));

        List<TradingStatement> topFromProfile = result.topTradingStatements(2, profile);
        List<TradingStatement> topFromVarargs = result.topTradingStatements(2, weightedOne, weightedTwo);
        assertEquals(topFromProfile.size(), topFromVarargs.size());
        for (int i = 0; i < topFromProfile.size(); i++) {
            assertSame(topFromProfile.get(i), topFromVarargs.get(i));
        }
    }

    @Test
    public void backtestWeightedTopStrategiesAttachRawCriterionScores() {
        BacktestExecutionResult result = createBacktestResult();
        MappedCriterion criterionOne = mappedCriterion(result, true, 3.0, 2.0, 1.0);
        MappedCriterion criterionTwo = mappedCriterion(result, false, 5.0, 4.0, 1.0);

        List<TradingStatement> topStatements = result.getTopStrategiesWeighted(2,
                TradingStatementExecutionResult.WeightedCriterion.of(criterionOne, 1.0),
                TradingStatementExecutionResult.WeightedCriterion.of(criterionTwo, 1.0));
        assertEquals(2, topStatements.size());
        for (TradingStatement statement : topStatements) {
            Num expectedOne = criterionOne.calculate(result.barSeries(), statement.getTradingRecord());
            Num expectedTwo = criterionTwo.calculate(result.barSeries(), statement.getTradingRecord());
            assertEquals(expectedOne, statement.getCriterionScore(criterionOne).orElseThrow());
            assertEquals(expectedTwo, statement.getCriterionScore(criterionTwo).orElseThrow());
        }
    }

    @Test
    public void weightedRankingValidatesInvalidInputs() {
        BacktestExecutionResult result = createBacktestResult();
        MappedCriterion criterionOne = mappedCriterion(result, true, 3.0, 2.0, 1.0);
        MappedCriterion criterionTwo = mappedCriterion(result, true, 1.0, 2.0, 3.0);

        assertThrows(NullPointerException.class,
                () -> result.rankTradingStatements((TradingStatementExecutionResult.RankingProfile) null));
        assertThrows(NullPointerException.class,
                () -> result.rankTradingStatements((TradingStatementExecutionResult.WeightedCriterion[]) null));
        assertThrows(IllegalArgumentException.class,
                () -> new TradingStatementExecutionResult.RankingProfile(List.of(), null, null));
        assertThrows(NullPointerException.class,
                () -> new TradingStatementExecutionResult.WeightedCriterion(null, numFactory.one()));
        assertThrows(IllegalArgumentException.class,
                () -> new TradingStatementExecutionResult.WeightedCriterion(criterionOne, numFactory.minusOne()));
        assertThrows(NullPointerException.class, () -> TradingStatementExecutionResult.RankingProfile
                .of((TradingStatementExecutionResult.WeightedCriterion[]) null));

        TradingStatementExecutionResult.RankingProfile zeroWeightProfile = TradingStatementExecutionResult.RankingProfile
                .of(new TradingStatementExecutionResult.WeightedCriterion(criterionOne, numFactory.zero()),
                        new TradingStatementExecutionResult.WeightedCriterion(criterionTwo, numFactory.zero()));
        assertThrows(IllegalArgumentException.class, () -> result.rankTradingStatements(zeroWeightProfile));
    }

    private TradingStatementExecutionResult.WeightedCriterion weightedCriterion(AnalysisCriterion criterion,
            double multiplier) {
        return TradingStatementExecutionResult.WeightedCriterion.of(criterion, numOf(multiplier));
    }

    private MappedCriterion mappedCriterion(TradingStatementExecutionResult<?> result, boolean higherIsBetter,
            double... values) {
        List<TradingRecord> records = result.tradingRecords();
        if (values.length != records.size()) {
            throw new IllegalArgumentException("values length must match trading record count");
        }
        Map<TradingRecord, Num> valuesByRecord = new IdentityHashMap<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            Num value = Double.isNaN(values[i]) ? NaN.NaN : result.barSeries().numFactory().numOf(values[i]);
            valuesByRecord.put(records.get(i), value);
        }
        return new MappedCriterion(valuesByRecord, result.barSeries().numFactory(), higherIsBetter);
    }

    private List<Integer> rankedIndexes(List<TradingStatementExecutionResult.RankedTradingStatement> rankedStatements) {
        List<Integer> indexes = new ArrayList<>(rankedStatements.size());
        for (TradingStatementExecutionResult.RankedTradingStatement ranked : rankedStatements) {
            indexes.add(ranked.originalIndex());
        }
        return indexes;
    }

    private BacktestExecutionResult createBacktestResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 102, 104, 106, 108, 110, 112, 114, 116, 118)
                .build();
        Strategy strategyOne = new BaseStrategy("strategy-1", new FixedRule(0), new FixedRule(4));
        Strategy strategyTwo = new BaseStrategy("strategy-2", new FixedRule(1), new FixedRule(5));
        Strategy strategyThree = new BaseStrategy("strategy-3", new FixedRule(2), new FixedRule(6));
        BacktestExecutor executor = new BacktestExecutor(series);
        return executor.executeWithRuntimeReport(List.of(strategyOne, strategyTwo, strategyThree), numFactory.one());
    }

    private StrategyWalkForwardExecutionResult createWalkForwardResult() {
        BarSeries series = buildSeries(48);
        Strategy strategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        StrategyWalkForwardExecutor executor = new StrategyWalkForwardExecutor(series);
        return executor.execute(strategy, walkForwardConfig());
    }

    private BarSeries buildSeries(int bars) {
        double[] data = new double[bars];
        for (int i = 0; i < bars; i++) {
            data[i] = 100 + (i * 0.5);
        }
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
    }

    private static WalkForwardConfig walkForwardConfig() {
        return new WalkForwardConfig(12, 6, 6, 0, 0, 6, 3, List.of(2), 1, List.of(1), 42L);
    }

    private static final class MappedCriterion implements AnalysisCriterion {

        private final Map<TradingRecord, Num> valuesByRecord;
        private final NumFactory numFactory;
        private final boolean higherIsBetter;

        private MappedCriterion(Map<TradingRecord, Num> valuesByRecord, NumFactory numFactory, boolean higherIsBetter) {
            this.valuesByRecord = valuesByRecord;
            this.numFactory = numFactory;
            this.higherIsBetter = higherIsBetter;
        }

        @Override
        public Num calculate(BarSeries series, Position position) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            return valuesByRecord.getOrDefault(tradingRecord, NaN.NaN);
        }

        @Override
        public boolean betterThan(Num criterionValue1, Num criterionValue2) {
            if (Num.isNaNOrNull(criterionValue1)) {
                return false;
            }
            if (Num.isNaNOrNull(criterionValue2)) {
                return true;
            }
            if (higherIsBetter) {
                return criterionValue1.isGreaterThan(criterionValue2);
            }
            return criterionValue1.isLessThan(criterionValue2);
        }
    }
}
