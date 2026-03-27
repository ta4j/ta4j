/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.walkforward;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.StrategyWalkForwardExecutionResult;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.walkforward.WalkForwardConfig;

import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;
import ta4jexamples.strategies.CCICorrectionStrategy;
import ta4jexamples.strategies.GlobalExtremaStrategy;
import ta4jexamples.strategies.MovingMomentumStrategy;
import ta4jexamples.strategies.RSI2Strategy;

/**
 * Walk-forward example using ta4j-core walk-forward APIs.
 *
 * <p>
 * This example evaluates several strategies on one {@link BarSeries} using
 * {@link BarSeriesManager#runWalkForward(Strategy, org.ta4j.core.Trade.TradeType, Num, WalkForwardConfig)}
 * and ranks them by average out-of-sample gross return. It then demonstrates
 * the symmetric one-call API through
 * {@link BacktestExecutor#executeWithWalkForward(Strategy, WalkForwardConfig)}.
 * </p>
 *
 * <p>
 * The walk-forward configuration is generated from the series using
 * {@link WalkForwardConfig#defaultConfig(BarSeries)}.
 * </p>
 *
 * @since 0.22.4
 * @see <a href="http://en.wikipedia.org/wiki/Walk_forward_optimization">
 *      http://en.wikipedia.org/wiki/Walk_forward_optimization</a>
 */
public class WalkForward {

    private static final Logger LOG = LogManager.getLogger(WalkForward.class);

    /**
     * @param series the bar series
     * @return a map (key: strategy, value: name) of trading strategies
     */
    public static Map<Strategy, String> buildStrategiesMap(BarSeries series) {
        LinkedHashMap<Strategy, String> strategies = new LinkedHashMap<>();
        strategies.put(CCICorrectionStrategy.buildStrategy(series), "CCI Correction");
        strategies.put(GlobalExtremaStrategy.buildStrategy(series), "Global Extrema");
        strategies.put(MovingMomentumStrategy.buildStrategy(series), "Moving Momentum");
        strategies.put(RSI2Strategy.buildStrategy(series), "RSI-2");
        return strategies;
    }

    private static Num average(List<Num> values, Num fallback) {
        if (values.isEmpty()) {
            return fallback;
        }
        Num sum = values.getFirst().getNumFactory().zero();
        for (Num value : values) {
            sum = sum.plus(value);
        }
        return sum.dividedBy(values.getFirst().getNumFactory().numOf(values.size()));
    }

    private static Strategy chooseBest(Map<Strategy, Num> strategyScores, AnalysisCriterion criterion) {
        Strategy bestStrategy = null;
        Num bestScore = null;
        for (Map.Entry<Strategy, Num> entry : strategyScores.entrySet()) {
            if (bestStrategy == null) {
                bestStrategy = entry.getKey();
                bestScore = entry.getValue();
                continue;
            }
            Num candidateScore = entry.getValue();
            if (bestScore != null && criterion.betterThan(candidateScore, bestScore)) {
                bestStrategy = entry.getKey();
                bestScore = candidateScore;
            }
        }
        return bestStrategy;
    }

    public static void main(String[] args) {
        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();
        WalkForwardConfig config = WalkForwardConfig.defaultConfig(series);
        Map<Strategy, String> strategies = buildStrategiesMap(series);
        AnalysisCriterion returnCriterion = new GrossReturnCriterion();
        BarSeriesManager manager = new BarSeriesManager(series);

        LOG.info("Running walk-forward on {} bars with config hash {}", series.getBarCount(), config.configHash());

        Map<Strategy, Num> strategyOutOfSampleScores = new LinkedHashMap<>();
        for (Map.Entry<Strategy, String> entry : strategies.entrySet()) {
            Strategy strategy = entry.getKey();
            String strategyName = entry.getValue();
            StrategyWalkForwardExecutionResult walkForwardResult = manager.runWalkForward(strategy, config);
            List<Num> outOfSampleScores = walkForwardResult.outOfSampleCriterionValues(returnCriterion);
            Num averageOutOfSampleScore = average(outOfSampleScores, series.numFactory().one());
            strategyOutOfSampleScores.put(strategy, averageOutOfSampleScore);

            LOG.info("{} -> avg OOS gross return: {} (folds={}, holdoutPresent={})", strategyName,
                    averageOutOfSampleScore, walkForwardResult.folds().size(),
                    walkForwardResult.holdoutCriterionValue(returnCriterion).isPresent());
        }

        Strategy bestStrategy = chooseBest(strategyOutOfSampleScores, returnCriterion);
        if (bestStrategy == null) {
            LOG.warn("No best strategy selected from walk-forward results.");
            return;
        }

        String bestStrategyName = strategies.get(bestStrategy);
        LOG.info("Best walk-forward strategy by avg OOS gross return: {}", bestStrategyName);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutor.BacktestAndWalkForwardResult combined = executor.executeWithWalkForward(bestStrategy, config);
        Num backtestGrossReturn = returnCriterion.calculate(series,
                combined.backtest().tradingStatements().getFirst().getTradingRecord());
        Num combinedOutOfSampleAverage = average(combined.walkForward().outOfSampleCriterionValues(returnCriterion),
                series.numFactory().one());
        LOG.info("Combined run for {} -> backtest gross return={}, walk-forward avg OOS gross return={}",
                bestStrategyName, backtestGrossReturn, combinedOutOfSampleAverage);
    }
}
