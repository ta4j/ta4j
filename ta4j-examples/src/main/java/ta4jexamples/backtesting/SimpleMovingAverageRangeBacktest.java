/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.TradingStatementExecutionResult.RankingProfile;
import org.ta4j.core.backtest.TradingStatementExecutionResult.WeightedCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch;
import org.ta4j.core.research.ParameterResearch.ParameterDomain;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.ParameterSet;
import org.ta4j.core.research.ParameterResearch.PruningPolicy;
import org.ta4j.core.research.ParameterResearch.ResearchConfig;
import org.ta4j.core.reports.BasePerformanceReport;
import org.ta4j.core.reports.PositionStatsReport;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs a simple moving-average parameter sweep and then highlights the best
 * candidates with weighted, normalized strategy ranking.
 *
 * <p>
 * The example keeps the strategy definitions intentionally simple, then uses a
 * composite score that favors net profit while still rewarding smoother equity
 * curves through return-over-max-drawdown. That makes the shortlist more
 * realistic than sorting on a single raw metric alone.
 * </p>
 */
public class SimpleMovingAverageRangeBacktest {

    private static final Logger LOG = LogManager.getLogger(SimpleMovingAverageRangeBacktest.class);
    private static final int DEFAULT_TOP_STRATEGIES = 3;
    private static final int DEFAULT_START = 3;
    private static final int DEFAULT_STOP = 50;
    private static final int DEFAULT_STEP = 5;
    private static final int DEFAULT_VALIDATION_BARS = 63;

    public static void main(String[] args) {
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();

        ParameterResearchReport baselineReport = runSmaResearch(series, PruningPolicy.NONE, DEFAULT_VALIDATION_BARS);
        ParameterResearchReport prunedReport = runSmaResearch(series, PruningPolicy.EXACT_TRADING_RECORD,
                DEFAULT_VALIDATION_BARS);

        LOG.debug("Baseline SMA parameter research{}", System.lineSeparator() + baselineReport.formatSummary());
        LOG.debug("Exact-record pruned SMA parameter research{}",
                System.lineSeparator() + prunedReport.formatSummary());

        final List<Strategy> strategies = smaBarCounts().stream()
                .map(barCount -> createSmaStrategy(series, barCount))
                .toList();
        BacktestExecutor backtestExecutor = new BacktestExecutor(series);
        BacktestExecutionResult result = backtestExecutor.executeWithRuntimeReport(strategies, DecimalNum.valueOf(50),
                Trade.TradeType.BUY);
        List<TradingStatement> tradingStatements = selectTopStrategies(result, DEFAULT_TOP_STRATEGIES);

        LOG.debug("Top {} weighted SMA strategies (7 parts net profit, 3 parts return over max drawdown)",
                tradingStatements.size());
        LOG.debug(printReport(tradingStatements));
    }

    /**
     * Selects the top strategies for this example using weighted, normalized
     * ranking.
     *
     * @param result full backtest result for the SMA parameter sweep
     * @param limit  maximum number of strategies to keep
     * @return top strategies ordered by the example weighted criteria
     */
    static List<TradingStatement> selectTopStrategies(BacktestExecutionResult result, int limit) {
        Objects.requireNonNull(result, "result cannot be null");
        return result.getTopStrategiesWeighted(limit, weightedRankingProfile());
    }

    /**
     * Runs the SMA parameter research workflow.
     *
     * @param series         full series
     * @param pruningPolicy  pruning policy for representative selection
     * @param validationBars final bars held out for validation
     * @return structured research report
     */
    static ParameterResearchReport runSmaResearch(BarSeries series, PruningPolicy pruningPolicy, int validationBars) {
        Objects.requireNonNull(series, "series cannot be null");
        ResearchConfig config = ResearchConfig
                .holdout(0, validationBars, weightedRankingProfile(), series.numFactory().numOf(50),
                        DEFAULT_TOP_STRATEGIES)
                .withPruningPolicy(pruningPolicy);
        return ParameterResearch.run(series, smaBarCountDomain(), ParameterResearch.CandidateValidator.acceptAll(),
                SimpleMovingAverageRangeBacktest::createSmaStrategy, config);
    }

    /**
     * Materializes an SMA strategy from a parameter research candidate.
     *
     * @param series     bar series used by the strategy
     * @param parameters normalized parameter set
     * @return SMA strategy with unstable bars set from its bar count
     */
    static Strategy createSmaStrategy(BarSeries series, ParameterSet parameters) {
        return createSmaStrategy(series, parameters.intValue("barCount"));
    }

    private static List<ParameterDomain> smaBarCountDomain() {
        return List.of(ParameterDomain.integerRange("barCount", DEFAULT_START, DEFAULT_STOP, DEFAULT_STEP, 1,
                Integer.MAX_VALUE, true));
    }

    private static List<Integer> smaBarCounts() {
        List<Integer> barCounts = new ArrayList<>();
        for (int value = DEFAULT_START; value <= DEFAULT_STOP; value += DEFAULT_STEP) {
            barCounts.add(value);
        }
        return barCounts;
    }

    private static RankingProfile weightedRankingProfile() {
        return RankingProfile.weighted(WeightedCriterion.of(new NetProfitCriterion(), 7.0),
                WeightedCriterion.of(new ReturnOverMaxDrawdownCriterion(), 3.0));
    }

    private static Strategy createSmaStrategy(BarSeries series, int barCount) {
        Objects.requireNonNull(series, "series cannot be null");
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be positive");
        }
        int unstableBars = barCount - 1;
        return new BaseStrategy("Sma(" + barCount + ")", createEntryRule(series, barCount),
                createExitRule(series, barCount), unstableBars);
    }

    private static Rule createEntryRule(BarSeries series, int barCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, barCount);
        return new UnderIndicatorRule(sma, closePrice);
    }

    private static Rule createExitRule(BarSeries series, int barCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, barCount);
        return new OverIndicatorRule(sma, closePrice);
    }

    private static String printReport(List<TradingStatement> tradingStatements) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append(System.lineSeparator());
        for (TradingStatement statement : tradingStatements) {
            resultBuilder.append(printStatementReport(statement));
            resultBuilder.append(System.lineSeparator());
        }

        return resultBuilder.toString();
    }

    private static StringBuilder printStatementReport(TradingStatement statement) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("######### ")
                .append(statement.getStrategy().getName())
                .append(" #########")
                .append(System.lineSeparator())
                .append(printPerformanceReport(statement.getPerformanceReport()))
                .append(System.lineSeparator())
                .append(printPositionStats(statement.getPositionStatsReport()))
                .append(System.lineSeparator())
                .append("###########################");
        return resultBuilder;
    }

    private static StringBuilder printPerformanceReport(BasePerformanceReport report) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("--------- performance report ---------")
                .append(System.lineSeparator())
                .append("total loss: ")
                .append(report.totalLoss)
                .append(System.lineSeparator())
                .append("total profit: ")
                .append(report.totalProfit)
                .append(System.lineSeparator())
                .append("total profit loss: ")
                .append(report.totalProfitLoss)
                .append(System.lineSeparator())
                .append("total profit loss percentage: ")
                .append(report.totalProfitLossPercentage)
                .append(System.lineSeparator())
                .append("---------------------------");
        return resultBuilder;
    }

    private static StringBuilder printPositionStats(PositionStatsReport report) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("--------- trade statistics report ---------")
                .append(System.lineSeparator())
                .append("loss trade count: ")
                .append(report.getLossCount())
                .append(System.lineSeparator())
                .append("profit trade count: ")
                .append(report.getProfitCount())
                .append(System.lineSeparator())
                .append("break even trade count: ")
                .append(report.getBreakEvenCount())
                .append(System.lineSeparator())
                .append("---------------------------");
        return resultBuilder;
    }
}
