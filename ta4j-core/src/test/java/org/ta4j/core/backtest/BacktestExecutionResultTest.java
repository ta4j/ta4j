/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.ExpectancyCriterion;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.FixedRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class BacktestExecutionResultTest extends AbstractIndicatorTest<BarSeries, Num> {

    public BacktestExecutionResultTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void toStringReturnsJsonWithCountAndRuntimeReport() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();

        Strategy strategyOne = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Strategy strategyTwo = new BaseStrategy(new FixedRule(1, 3), new FixedRule(2, 4));

        List<Strategy> strategies = List.of(strategyOne, strategyTwo);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1));

        String jsonString = result.toString();

        assertNotNull("toString() should not return null", jsonString);
        assertFalse("toString() should return non-empty JSON", jsonString.isEmpty());

        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

        assertTrue("JSON should contain barSeriesName", json.has("barSeriesName"));
        assertEquals("barSeriesName should match series name", series.getName(),
                json.get("barSeriesName").getAsString());

        assertTrue("JSON should contain tradingStatementsCount", json.has("tradingStatementsCount"));
        assertEquals("tradingStatementsCount should match actual count", strategies.size(),
                json.get("tradingStatementsCount").getAsInt());

        assertTrue("JSON should contain runtimeReport", json.has("runtimeReport"));
        assertNotNull("runtimeReport should not be null", json.get("runtimeReport"));
        assertTrue("runtimeReport should be a JSON object", json.get("runtimeReport").isJsonObject());

        JsonObject runtimeReportJson = json.get("runtimeReport").getAsJsonObject();
        assertTrue("runtimeReport should contain overallRuntime", runtimeReportJson.has("overallRuntime"));
        assertTrue("runtimeReport should contain minStrategyRuntime", runtimeReportJson.has("minStrategyRuntime"));
        assertTrue("runtimeReport should contain maxStrategyRuntime", runtimeReportJson.has("maxStrategyRuntime"));
        assertTrue("runtimeReport should contain averageStrategyRuntime",
                runtimeReportJson.has("averageStrategyRuntime"));
        assertTrue("runtimeReport should contain medianStrategyRuntime",
                runtimeReportJson.has("medianStrategyRuntime"));
        assertFalse("runtimeReport should NOT contain strategyRuntimes", runtimeReportJson.has("strategyRuntimes"));
    }

    @Test
    public void toStringHandlesEmptyTradingStatements() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(5, 6, 7).build();

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(), numOf(1));

        String jsonString = result.toString();

        assertNotNull("toString() should not return null", jsonString);
        assertFalse("toString() should return non-empty JSON", jsonString.isEmpty());

        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

        assertEquals("tradingStatementsCount should be 0 for empty list", 0,
                json.get("tradingStatementsCount").getAsInt());
        assertTrue("JSON should contain runtimeReport", json.has("runtimeReport"));
    }

    @Test
    public void getTopStrategiesWithSingleCriterionSortsCorrectly() {
        // Create a bar series with price movement
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 115, 120, 125, 130, 135, 140, 145)
                .build();

        // Strategy 1: Buy at 0, sell at 5 (profit from 100 to 125)
        Strategy strategy1 = new BaseStrategy("Strategy1", new FixedRule(0), new FixedRule(5));

        // Strategy 2: Buy at 2, sell at 7 (profit from 110 to 135)
        Strategy strategy2 = new BaseStrategy("Strategy2", new FixedRule(2), new FixedRule(7));

        // Strategy 3: Buy at 4, sell at 9 (profit from 120 to 145)
        Strategy strategy3 = new BaseStrategy("Strategy3", new FixedRule(4), new FixedRule(9));

        List<Strategy> strategies = List.of(strategy1, strategy2, strategy3);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1));

        // Get top 2 strategies by net profit
        AnalysisCriterion netProfitCriterion = new NetProfitCriterion();
        List<TradingStatement> topStrategies = result.getTopStrategies(2, netProfitCriterion);

        assertEquals("Should return 2 strategies", 2, topStrategies.size());

        // Verify the strategies are sorted by profit (strategy3 should be best, then
        // strategy2)
        Num profit1 = netProfitCriterion.calculate(result.barSeries(), topStrategies.get(0).getTradingRecord());
        Num profit2 = netProfitCriterion.calculate(result.barSeries(), topStrategies.get(1).getTradingRecord());
        assertTrue("First strategy should have better or equal profit than second",
                netProfitCriterion.betterThan(profit1, profit2) || profit1.equals(profit2));
    }

    @Test
    public void getTopStrategiesWithMultipleCriteriaSortsByPriorityAndTieBreaks() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 115, 120, 125, 130, 135, 140, 145)
                .build();

        // Create strategies with different trading patterns
        Strategy strategy1 = new BaseStrategy("Strategy1", new FixedRule(0, 5), new FixedRule(2, 7));
        Strategy strategy2 = new BaseStrategy("Strategy2", new FixedRule(1, 6), new FixedRule(3, 8));
        Strategy strategy3 = new BaseStrategy("Strategy3", new FixedRule(2), new FixedRule(7));

        List<Strategy> strategies = List.of(strategy1, strategy2, strategy3);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1));

        // Sort by number of positions first, then by expectancy for ties
        AnalysisCriterion positionsCriterion = new NumberOfPositionsCriterion();
        AnalysisCriterion expectancyCriterion = new ExpectancyCriterion();
        List<TradingStatement> topStrategies = result.getTopStrategies(3, positionsCriterion, expectancyCriterion);

        assertEquals("Should return all 3 strategies", 3, topStrategies.size());

        // Verify ordering by primary criterion
        for (int i = 0; i < topStrategies.size() - 1; i++) {
            Num positions1 = positionsCriterion.calculate(result.barSeries(), topStrategies.get(i).getTradingRecord());
            Num positions2 = positionsCriterion.calculate(result.barSeries(),
                    topStrategies.get(i + 1).getTradingRecord());

            // First criterion should be better or equal
            assertTrue("Strategies should be sorted by primary criterion",
                    positionsCriterion.betterThan(positions1, positions2) || positions1.equals(positions2));

            // If equal on first criterion, second criterion should be better or equal
            if (positions1.equals(positions2)) {
                Num expectancy1 = expectancyCriterion.calculate(result.barSeries(),
                        topStrategies.get(i).getTradingRecord());
                Num expectancy2 = expectancyCriterion.calculate(result.barSeries(),
                        topStrategies.get(i + 1).getTradingRecord());
                assertTrue("Strategies with equal primary criterion should be sorted by secondary criterion",
                        expectancyCriterion.betterThan(expectancy1, expectancy2) || expectancy1.equals(expectancy2));
            }
        }
    }

    @Test
    public void getTopStrategiesRespectsLimit() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120, 130, 140).build();

        List<Strategy> strategies = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            strategies.add(new BaseStrategy("Strategy" + i, new FixedRule(0), new FixedRule(2)));
        }

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1));

        AnalysisCriterion criterion = new NetProfitCriterion();
        List<TradingStatement> topStrategies = result.getTopStrategies(5, criterion);

        assertEquals("Should return only 5 strategies even though 10 were provided", 5, topStrategies.size());
    }

    @Test
    public void getTopStrategiesWithLimitLargerThanResultsReturnsAll() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120).build();

        Strategy strategy1 = new BaseStrategy("Strategy1", new FixedRule(0), new FixedRule(1));
        Strategy strategy2 = new BaseStrategy("Strategy2", new FixedRule(0), new FixedRule(2));

        List<Strategy> strategies = List.of(strategy1, strategy2);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1));

        AnalysisCriterion criterion = new NetProfitCriterion();
        List<TradingStatement> topStrategies = result.getTopStrategies(100, criterion);

        assertEquals("Should return all available strategies when limit exceeds count", 2, topStrategies.size());
    }

    @Test
    public void getTopStrategiesWithZeroLimitReturnsEmpty() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120).build();

        Strategy strategy = new BaseStrategy("Strategy", new FixedRule(0), new FixedRule(1));

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), numOf(1));

        AnalysisCriterion criterion = new NetProfitCriterion();
        List<TradingStatement> topStrategies = result.getTopStrategies(0, criterion);

        assertTrue("Should return empty list when limit is 0", topStrategies.isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void getTopStrategiesThrowsExceptionWhenCriteriaVarargsIsNull() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();

        Strategy strategy = new BaseStrategy("Strategy", new FixedRule(0), new FixedRule(1));

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), numOf(1));

        AnalysisCriterion[] nullCriteria = null;
        result.getTopStrategies(1, nullCriteria);
    }

    @Test(expected = NullPointerException.class)
    public void getTopStrategiesThrowsExceptionWhenCriteriaListIsNull() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();

        Strategy strategy = new BaseStrategy("Strategy", new FixedRule(0), new FixedRule(1));

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), numOf(1));

        List<AnalysisCriterion> nullCriteria = null;
        result.getTopStrategies(1, nullCriteria);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getTopStrategiesThrowsExceptionWhenCriteriaIsEmpty() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();

        Strategy strategy = new BaseStrategy("Strategy", new FixedRule(0), new FixedRule(1));

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), numOf(1));

        result.getTopStrategies(1, new ArrayList<>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getTopStrategiesThrowsExceptionWhenLimitIsNegative() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();

        Strategy strategy = new BaseStrategy("Strategy", new FixedRule(0), new FixedRule(1));

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), numOf(1));

        result.getTopStrategies(-1, new NetProfitCriterion());
    }

    @Test
    public void getTopStrategiesVarargsAndListMethodsProduceSameResults() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 115, 120, 125, 130, 135)
                .build();

        Strategy strategy1 = new BaseStrategy("Strategy1", new FixedRule(0), new FixedRule(4));
        Strategy strategy2 = new BaseStrategy("Strategy2", new FixedRule(1), new FixedRule(5));
        Strategy strategy3 = new BaseStrategy("Strategy3", new FixedRule(2), new FixedRule(6));

        List<Strategy> strategies = List.of(strategy1, strategy2, strategy3);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1));

        AnalysisCriterion netProfitCriterion = new NetProfitCriterion();
        AnalysisCriterion expectancyCriterion = new ExpectancyCriterion();

        // Call with varargs
        List<TradingStatement> varargsResult = result.getTopStrategies(2, netProfitCriterion, expectancyCriterion);

        // Call with List
        List<TradingStatement> listResult = result.getTopStrategies(2,
                Arrays.asList(netProfitCriterion, expectancyCriterion));

        assertEquals("Varargs and List methods should return same number of results", varargsResult.size(),
                listResult.size());

        // Verify same strategies in same order
        for (int i = 0; i < varargsResult.size(); i++) {
            assertEquals("Varargs and List methods should return same strategies in same order",
                    varargsResult.get(i).getStrategy().getName(), listResult.get(i).getStrategy().getName());
        }
    }

    @Test
    public void getTopStrategiesHandlesEmptyTradingStatements() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(), numOf(1));

        AnalysisCriterion criterion = new NetProfitCriterion();
        List<TradingStatement> topStrategies = result.getTopStrategies(10, criterion);

        assertTrue("Should return empty list when no trading statements exist", topStrategies.isEmpty());
    }

    @Test
    public void getTopStrategiesStoresCriterionScores() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 115, 120, 125, 130, 135, 140, 145)
                .build();

        Strategy strategy1 = new BaseStrategy("Strategy1", new FixedRule(0), new FixedRule(5));
        Strategy strategy2 = new BaseStrategy("Strategy2", new FixedRule(2), new FixedRule(7));
        Strategy strategy3 = new BaseStrategy("Strategy3", new FixedRule(4), new FixedRule(9));

        List<Strategy> strategies = List.of(strategy1, strategy2, strategy3);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1));

        AnalysisCriterion netProfitCriterion = new NetProfitCriterion();
        AnalysisCriterion expectancyCriterion = new ExpectancyCriterion();
        List<TradingStatement> topStrategies = result.getTopStrategies(3, netProfitCriterion, expectancyCriterion);

        assertEquals("Should return all 3 strategies", 3, topStrategies.size());

        // Verify that criterion scores are stored and accessible
        for (TradingStatement statement : topStrategies) {
            // Check that scores are available via getCriterionScore
            assertTrue("Net profit score should be available",
                    statement.getCriterionScore(netProfitCriterion).isPresent());
            assertTrue("Expectancy score should be available",
                    statement.getCriterionScore(expectancyCriterion).isPresent());

            // Verify the scores match what we would calculate
            Num storedNetProfit = statement.getCriterionScore(netProfitCriterion).get();
            Num calculatedNetProfit = netProfitCriterion.calculate(result.barSeries(), statement.getTradingRecord());
            assertEquals("Stored net profit should match calculated value", storedNetProfit, calculatedNetProfit);

            Num storedExpectancy = statement.getCriterionScore(expectancyCriterion).get();
            Num calculatedExpectancy = expectancyCriterion.calculate(result.barSeries(), statement.getTradingRecord());
            assertEquals("Stored expectancy should match calculated value", storedExpectancy, calculatedExpectancy);

            // Check that all scores are available via getCriterionScores
            var allScores = statement.getCriterionScores();
            assertEquals("Should have 2 criterion scores stored", 2, allScores.size());
            assertTrue("Should contain net profit criterion", allScores.containsKey(netProfitCriterion));
            assertTrue("Should contain expectancy criterion", allScores.containsKey(expectancyCriterion));
        }
    }
}
