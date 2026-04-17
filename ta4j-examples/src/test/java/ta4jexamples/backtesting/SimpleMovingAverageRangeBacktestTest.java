/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.TradingStatementExecutionResult.WeightedCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.PruningPolicy;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.FixedRule;

public class SimpleMovingAverageRangeBacktestTest {

    @Test
    public void test() throws InterruptedException {
        SimpleMovingAverageRangeBacktest.main(null);
    }

    @Test
    public void selectTopStrategiesUsesWeightedRankingConvenienceApiAndPreservesCriterionScores() {
        BacktestExecutionResult result = createBacktestResult();

        List<TradingStatement> expected = result.getTopStrategiesWeighted(2,
                WeightedCriterion.of(new NetProfitCriterion(), 7.0),
                WeightedCriterion.of(new ReturnOverMaxDrawdownCriterion(), 3.0));
        List<TradingStatement> actual = SimpleMovingAverageRangeBacktest.selectTopStrategies(result, 2);

        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getStrategy().getName(), actual.get(i).getStrategy().getName());
            assertEquals(2, actual.get(i).getCriterionScores().size());

            Set<String> criterionTypes = new HashSet<>();
            actual.get(i)
                    .getCriterionScores()
                    .keySet()
                    .forEach(criterion -> criterionTypes.add(criterion.getClass().getName()));
            assertTrue(criterionTypes.contains(NetProfitCriterion.class.getName()));
            assertTrue(criterionTypes.contains(ReturnOverMaxDrawdownCriterion.class.getName()));
        }
    }

    @Test
    public void smaResearchUsesTrainingWindowAndHoldoutValidation() {
        BarSeries series = new MockBarSeriesBuilder()
                .withData(100d, 101d, 103d, 106d, 110d, 115d, 121d, 128d, 136d, 145d, 155d, 166d, 178d, 191d, 205d)
                .build();
        ParameterResearchReport report = SimpleMovingAverageRangeBacktest.runSmaResearch(series, PruningPolicy.NONE, 5);

        assertEquals(0, report.prunedCandidateCount());
        assertEquals(3, report.trainingScores().size());
        assertEquals(3, report.validationScores().size());
        assertEquals(0, report.window().trainingStartIndex());
        assertEquals(9, report.window().trainingEndIndex());
        assertEquals(10, report.window().validationStartIndex());
        assertEquals(14, report.window().validationEndIndex());
    }

    private BacktestExecutionResult createBacktestResult() {
        BarSeries series = new MockBarSeriesBuilder().withData(100d, 120d, 90d, 140d, 115d, 130d).build();
        List<Strategy> strategies = List.of(new BaseStrategy("Hold to finish", new FixedRule(0), new FixedRule(5)),
                new BaseStrategy("Buy the dip", new FixedRule(2), new FixedRule(3)),
                new BaseStrategy("Early loss", new FixedRule(1), new FixedRule(2)));

        BacktestExecutor executor = new BacktestExecutor(series);
        return executor.executeWithRuntimeReport(strategies, series.numFactory().numOf(50), Trade.TradeType.BUY);
    }
}
