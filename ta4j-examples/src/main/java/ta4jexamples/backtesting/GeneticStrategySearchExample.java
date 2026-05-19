/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.TradingStatementExecutionResult.WeightedCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.optimization.ga.GeneticStrategyTuner;
import org.ta4j.core.optimization.ga.ParameterDomain;
import org.ta4j.core.optimization.ga.StrategyChromosomeCodec;
import org.ta4j.core.optimization.ga.StrategyFitnessEvaluator;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.StopLossRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

/**
 * Example demonstrating seeded genetic search over an explicit SMA strategy
 * factory.
 *
 * <p>
 * The search space stays intentionally small and reviewable:
 * <ul>
 * <li>numeric genes: short SMA window and long-window gap</li>
 * <li>enum-like gene: trend-following vs mean-reversion SMA interpretation</li>
 * <li>constrained boolean gene: optional stop-loss protection</li>
 * </ul>
 *
 * @since 0.22.7
 */
public class GeneticStrategySearchExample {

    private static final Logger LOG = LogManager.getLogger(GeneticStrategySearchExample.class);
    private static final int DEFAULT_TOP_CANDIDATES = 3;
    private static final GeneticStrategyTuner.Settings DEFAULT_SETTINGS = new GeneticStrategyTuner.Settings(14, 10, 5,
            2, 0.7, 0.35, 2, 7L);

    public static void main(String[] args) {
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();
        SearchRun run = runSearch(series);
        LOG.debug(renderReport(run));
    }

    static SearchRun runSearch(BarSeries series) {
        Objects.requireNonNull(series, "series");

        BacktestExecutor executor = new BacktestExecutor(series);
        StrategyChromosomeCodec<SmaSearchCandidate> codec = createCodec();
        StrategyFitnessEvaluator<SmaSearchCandidate> evaluator = candidate -> scoreCandidate(series, executor,
                candidate);
        GeneticStrategyTuner<SmaSearchCandidate> tuner = new GeneticStrategyTuner<>(codec, evaluator, DEFAULT_SETTINGS);

        GeneticStrategyTuner.SearchResult<SmaSearchCandidate> searchResult = tuner.tune();
        List<Strategy> strategies = searchResult.topCandidates()
                .stream()
                .map(candidate -> createStrategy(series, candidate.context()))
                .toList();

        Num amount = series.numFactory().numOf(50);
        BacktestExecutionResult rankedCandidates = executor.executeWithRuntimeReport(strategies, amount,
                Trade.TradeType.BUY);
        List<TradingStatement> topStatements = selectTopStrategies(rankedCandidates, DEFAULT_TOP_CANDIDATES);
        return new SearchRun(searchResult, topStatements);
    }

    static List<TradingStatement> selectTopStrategies(BacktestExecutionResult result, int limit) {
        Objects.requireNonNull(result, "result");
        return result.getTopStrategiesWeighted(limit, WeightedCriterion.of(new NetProfitCriterion(), 7.0),
                WeightedCriterion.of(new ReturnOverMaxDrawdownCriterion(), 3.0));
    }

    private static StrategyChromosomeCodec<SmaSearchCandidate> createCodec() {
        return new StrategyChromosomeCodec<>(List.of(ParameterDomain.integerRange("shortBarCount", 5, 20, 5),
                ParameterDomain.integerRange("longBarGap", 10, 30, 5),
                ParameterDomain.ofValues("signalMode",
                        List.of(SmaSignalMode.TREND_FOLLOWING, SmaSignalMode.MEAN_REVERSION)),
                ParameterDomain.constrainedBoolean("stopLossEnabled", false, true)), values -> {
                    int shortBarCount = values.get("shortBarCount", Integer.class);
                    int longBarGap = values.get("longBarGap", Integer.class);
                    return new SmaSearchCandidate(shortBarCount, shortBarCount + longBarGap,
                            values.get("signalMode", SmaSignalMode.class),
                            values.get("stopLossEnabled", Boolean.class));
                });
    }

    private static Num scoreCandidate(BarSeries series, BacktestExecutor executor, SmaSearchCandidate candidate) {
        Strategy strategy = createStrategy(series, candidate);
        Num amount = series.numFactory().numOf(50);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), amount,
                Trade.TradeType.BUY);
        return new NetProfitCriterion().calculate(series, result.tradingStatements().get(0).getTradingRecord());
    }

    private static Strategy createStrategy(BarSeries series, SmaSearchCandidate candidate) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(closePrice, candidate.shortBarCount());
        SMAIndicator longSma = new SMAIndicator(closePrice, candidate.longBarCount());

        Rule entryRule;
        Rule exitRule;
        if (candidate.signalMode() == SmaSignalMode.TREND_FOLLOWING) {
            entryRule = new OverIndicatorRule(shortSma, longSma);
            exitRule = new UnderIndicatorRule(shortSma, longSma);
        } else {
            entryRule = new UnderIndicatorRule(shortSma, longSma);
            exitRule = new OverIndicatorRule(shortSma, longSma);
        }
        if (candidate.stopLossEnabled()) {
            exitRule = exitRule.or(new StopLossRule(closePrice, 3.0));
        }
        return new BaseStrategy(candidate.strategyName(), entryRule, exitRule);
    }

    private static String renderReport(SearchRun run) {
        StringBuilder builder = new StringBuilder();
        builder.append(System.lineSeparator())
                .append("######### Genetic strategy search #########")
                .append(System.lineSeparator())
                .append("evaluated unique candidates: ")
                .append(run.searchResult().uniqueCandidateCount())
                .append(System.lineSeparator())
                .append("top GA candidates:")
                .append(System.lineSeparator());

        for (GeneticStrategyTuner.CandidateResult<SmaSearchCandidate> candidate : run.searchResult().topCandidates()) {
            builder.append("- ")
                    .append(candidate.context().strategyName())
                    .append(" fitness=")
                    .append(candidate.fitnessScore())
                    .append(" params=")
                    .append(candidate.parameters().toStableId())
                    .append(System.lineSeparator());
        }

        builder.append("top weighted backtest statements:").append(System.lineSeparator());
        for (TradingStatement statement : run.topStatements()) {
            builder.append("- ").append(statement.getStrategy().getName()).append(System.lineSeparator());
        }
        return builder.toString();
    }

    enum SmaSignalMode {
        TREND_FOLLOWING, MEAN_REVERSION
    }

    record SmaSearchCandidate(int shortBarCount, int longBarCount, SmaSignalMode signalMode, boolean stopLossEnabled) {

        String strategyName() {
            return "GA SMA [" + signalMode + ", short=" + shortBarCount + ", long=" + longBarCount + ", stopLoss="
                    + stopLossEnabled + "]";
        }
    }

    record SearchRun(GeneticStrategyTuner.SearchResult<SmaSearchCandidate> searchResult,
            List<TradingStatement> topStatements) {
    }
}
