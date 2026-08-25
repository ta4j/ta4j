/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.CalmarRatioCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.MonteCarloMaximumDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.reports.BaseTradingStatement;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.FixedRule;

/**
 * Verifies that the production ranking and selection workflows share equity
 * curves across the criteria they evaluate without changing any result: the
 * reported scores match a plain sequential evaluation exactly, and an ordinary
 * custom {@link AnalysisCriterion} keeps running its regular two-argument
 * calculation exactly once per statement.
 */
public class SharedCurveWorkflowTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    private BacktestExecutionResult createBacktestResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 2d, 4d, 3d, 5d, 4d, 6d, 5d, 7d)
                .build();
        Strategy strategyOne = new BaseStrategy("strategy-1", new FixedRule(0), new FixedRule(2));
        Strategy strategyTwo = new BaseStrategy("strategy-2", new FixedRule(3), new FixedRule(5));
        Strategy strategyThree = new BaseStrategy("strategy-3", new FixedRule(6), new FixedRule(9));
        List<TradingStatement> statements = List.of(createTradingStatement(series, strategyOne, 0, 2),
                createTradingStatement(series, strategyTwo, 3, 5), createTradingStatement(series, strategyThree, 6, 9));
        return new BacktestExecutionResult(series, statements, BacktestRuntimeReport.empty());
    }

    private TradingStatement createTradingStatement(BarSeries series, Strategy strategy, int entryIndex,
            int exitIndex) {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(),
                new ZeroCostModel());
        tradingRecord.operate(entryIndex, series.getBar(entryIndex).getClosePrice(), numFactory.one());
        tradingRecord.operate(exitIndex, series.getBar(exitIndex).getClosePrice(), numFactory.one());
        return new BaseTradingStatement(strategy, tradingRecord, null, null);
    }

    @Test
    public void getTopStrategiesMatchesSequentialEvaluationAndRunsFallbackOncePerStatement() {
        BacktestExecutionResult result = createBacktestResult();
        List<AnalysisCriterion> criteria = new ArrayList<>();
        criteria.add(new MaximumDrawdownCriterion());
        criteria.add(new ReturnOverMaxDrawdownCriterion());
        criteria.add(new MonteCarloMaximumDrawdownCriterion());
        CountingCriterion countingCriterion = new CountingCriterion(numFactory);
        criteria.add(countingCriterion);

        Map<TradingStatement, Map<AnalysisCriterion, Num>> sequentialValues = new LinkedHashMap<>();
        for (TradingStatement statement : result.tradingStatements()) {
            Map<AnalysisCriterion, Num> values = new LinkedHashMap<>();
            for (AnalysisCriterion criterion : criteria) {
                values.put(criterion, criterion.calculate(result.barSeries(), statement.getTradingRecord()));
            }
            sequentialValues.put(statement, values);
        }
        int fallbackCallsBeforeRanking = countingCriterion.calculations.get();
        List<TradingStatement> top = result.getTopStrategies(result.tradingStatements().size(), criteria);

        List<String> expectedNames = expectedOrder(result.tradingStatements(), criteria, sequentialValues).stream()
                .map(statement -> statement.getStrategy().getName())
                .toList();
        List<String> actualNames = top.stream().map(statement -> statement.getStrategy().getName()).toList();
        assertEquals(expectedNames, actualNames);
        assertEquals("the fallback criterion must run once per statement",
                fallbackCallsBeforeRanking + result.tradingStatements().size(), countingCriterion.calculations.get());
    }

    @Test
    public void rankTradingStatementsMatchesSequentialEvaluationAndRunsFallbackOncePerStatement() {
        BacktestExecutionResult result = createBacktestResult();
        MaximumDrawdownCriterion drawdown = new MaximumDrawdownCriterion();
        CalmarRatioCriterion calmar = new CalmarRatioCriterion();
        CountingCriterion countingCriterion = new CountingCriterion(numFactory);

        List<TradingStatementExecutionResult.RankedTradingStatement> ranked = result.rankTradingStatements(
                TradingStatementExecutionResult.WeightedCriterion.of(drawdown),
                TradingStatementExecutionResult.WeightedCriterion.of(calmar),
                TradingStatementExecutionResult.WeightedCriterion.of(countingCriterion));

        assertEquals(result.tradingStatements().size(), ranked.size());
        for (TradingStatementExecutionResult.RankedTradingStatement row : ranked) {
            TradingRecord tradingRecord = row.statement().getTradingRecord();
            assertNumEquals(drawdown.calculate(result.barSeries(), tradingRecord), row.rawScores().get(drawdown));
            assertNumEquals(calmar.calculate(result.barSeries(), tradingRecord), row.rawScores().get(calmar));
            assertNumEquals(numFactory.one(), row.rawScores().get(countingCriterion));
        }
        assertEquals("the fallback criterion must run once per statement", result.tradingStatements().size(),
                countingCriterion.calculations.get());
    }

    private static List<TradingStatement> expectedOrder(List<TradingStatement> statements,
            List<AnalysisCriterion> criteria, Map<TradingStatement, Map<AnalysisCriterion, Num>> valuesByStatement) {
        List<TradingStatement> ordered = new ArrayList<>(statements);
        ordered.sort((left, right) -> {
            for (AnalysisCriterion criterion : criteria) {
                Num leftValue = valuesByStatement.get(left).get(criterion);
                Num rightValue = valuesByStatement.get(right).get(criterion);
                if (!leftValue.equals(rightValue)) {
                    return criterion.betterThan(leftValue, rightValue) ? -1 : 1;
                }
            }
            return 0;
        });
        return ordered;
    }

    private static final class CountingCriterion implements AnalysisCriterion {

        private final AtomicInteger calculations = new AtomicInteger();
        private final NumFactory numFactory;

        CountingCriterion(NumFactory numFactory) {
            this.numFactory = numFactory;
        }

        @Override
        public Num calculate(BarSeries series, Position position) {
            return numFactory.zero();
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            calculations.incrementAndGet();
            return numFactory.one();
        }

        /** The higher the criterion value, the better. */
        @Override
        public boolean betterThan(Num criterionValue1, Num criterionValue2) {
            return criterionValue1.isGreaterThan(criterionValue2);
        }
    }
}
