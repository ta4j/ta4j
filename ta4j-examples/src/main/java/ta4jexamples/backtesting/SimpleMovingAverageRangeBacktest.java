/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.research.ParameterResearch;
import org.ta4j.core.research.ParameterResearch.ObjectiveEvaluation;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.ParameterSet;
import org.ta4j.core.research.ParameterResearch.RankedCandidate;
import org.ta4j.core.research.ParameterResearch.ResearchWindow;
import org.ta4j.core.research.ParameterResearch.SearchPlan;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.StopLossRule;

import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

/**
 * Example parameter research workflow for a simple SMA trend strategy.
 *
 * <p>
 * The example searches fast/slow SMA periods plus stop-loss percentages,
 * rejects invalid fast/slow combinations, ranks candidates by a weighted
 * objective over net return and return-over-max-drawdown, and checks whether
 * the selected training candidates survive a holdout window.
 * </p>
 */
public class SimpleMovingAverageRangeBacktest {

    private static final Logger LOG = LogManager.getLogger(SimpleMovingAverageRangeBacktest.class);
    private static final String FAST_BAR_COUNT = "fastBarCount";
    private static final String SLOW_BAR_COUNT = "slowBarCount";
    private static final String STOP_LOSS_PERCENTAGE = "stopLossPercentage";
    private static final String NET_PROFIT = "Net Profit";
    private static final String RETURN_OVER_MAX_DRAWDOWN = "Return Over Max Drawdown";
    private static final int DEFAULT_TOP_STRATEGIES = 3;
    private static final int DEFAULT_VALIDATION_BARS = 63;
    private static final int DEFAULT_REPORT_ROWS = 5;
    private static final double STARTING_CAPITAL = 50.0;

    public static void main(String[] args) {
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();
        ParameterResearchReport report = runSmaResearch(series, DEFAULT_VALIDATION_BARS);

        LOG.info(System.lineSeparator() + formatResearchNarrative(report, DEFAULT_REPORT_ROWS));
    }

    /**
     * Runs the SMA trend parameter research workflow.
     *
     * @param series         full series
     * @param validationBars final bars held out for validation
     * @return structured research report
     */
    static ParameterResearchReport runSmaResearch(BarSeries series, int validationBars) {
        Objects.requireNonNull(series, "series cannot be null");
        return ParameterResearch.builder(series)
                .integer(FAST_BAR_COUNT, 5, 35, 10)
                .integer(SLOW_BAR_COUNT, 10, 50, 10)
                .integer(STOP_LOSS_PERCENTAGE, 3, 9, 3)
                .validate(SimpleMovingAverageRangeBacktest::validateParameters)
                .candidate(SimpleMovingAverageRangeBacktest::createSmaStrategy)
                .maximize(SimpleMovingAverageRangeBacktest::scoreStrategy)
                .search(SearchPlan.grid(200))
                .holdoutBarCount(validationBars)
                .topK(DEFAULT_TOP_STRATEGIES)
                .run();
    }

    /**
     * Materializes an SMA trend strategy from a parameter research candidate.
     *
     * @param window     evaluation window the strategy is restricted to
     * @param parameters normalized parameter set
     * @return SMA trend strategy with unstable bars set from the slower SMA
     */
    static Strategy createSmaStrategy(ResearchWindow window, ParameterSet parameters) {
        return createSmaStrategy(window.series(), parameters.intValue(FAST_BAR_COUNT),
                parameters.intValue(SLOW_BAR_COUNT), parameters.intValue(STOP_LOSS_PERCENTAGE));
    }

    static String formatResearchNarrative(ParameterResearchReport report, int maxRows) {
        Objects.requireNonNull(report, "report cannot be null");
        StringBuilder builder = new StringBuilder();
        builder.append("SMA trend parameter research")
                .append(System.lineSeparator())
                .append("Candidate space: ")
                .append(report.counts().proposed())
                .append(" proposals, ")
                .append(report.counts().rejected())
                .append(" rejected, ")
                .append(report.counts().attempted())
                .append(" evaluated")
                .append(System.lineSeparator())
                .append("Training top candidates:")
                .append(System.lineSeparator());
        appendRows(builder, report.trainingLeaderboard(), true, maxRows);
        builder.append("Validation top candidates:").append(System.lineSeparator());
        appendRows(builder, report.holdoutLeaderboard(), false, maxRows);
        builder.append("Takeaway: ").append(holdoutTakeaway(report));
        return builder.toString();
    }

    private static void appendRows(StringBuilder builder, List<RankedCandidate> leaderboard, boolean training,
            int maxRows) {
        if (leaderboard.isEmpty()) {
            builder.append("  (none)").append(System.lineSeparator());
            return;
        }
        int rows = Math.min(maxRows, leaderboard.size());
        for (int i = 0; i < rows; i++) {
            RankedCandidate candidate = leaderboard.get(i);
            Num score = training ? candidate.trainingScore() : candidate.holdoutScore();
            Map<String, Num> metrics = training ? candidate.trainingMetrics() : candidate.holdoutMetrics();
            builder.append("  #")
                    .append(i + 1)
                    .append(" ")
                    .append(candidate.candidateId())
                    .append(" score=")
                    .append(score)
                    .append(" ")
                    .append(NET_PROFIT)
                    .append("=")
                    .append(metrics.get(NET_PROFIT))
                    .append(" ")
                    .append(RETURN_OVER_MAX_DRAWDOWN)
                    .append("=")
                    .append(metrics.get(RETURN_OVER_MAX_DRAWDOWN))
                    .append(System.lineSeparator());
        }
    }

    private static void validateParameters(ParameterSet parameters) {
        int fastBarCount = parameters.intValue(FAST_BAR_COUNT);
        int slowBarCount = parameters.intValue(SLOW_BAR_COUNT);
        if (fastBarCount >= slowBarCount) {
            throw new IllegalArgumentException(FAST_BAR_COUNT + " must be lower than " + SLOW_BAR_COUNT);
        }
    }

    private static ObjectiveEvaluation scoreStrategy(Strategy strategy, ResearchWindow window) {
        BarSeries series = window.series();
        BacktestExecutor executor = new BacktestExecutor(series);
        TradingStatement statement = executor
                .execute(List.of(strategy), series.numFactory().numOf(STARTING_CAPITAL), Trade.TradeType.BUY)
                .getFirst();
        Num netProfit = new NetProfitCriterion().calculate(series, statement.getTradingRecord());
        Num returnOverDrawdown = new ReturnOverMaxDrawdownCriterion().calculate(series, statement.getTradingRecord());
        if (!Num.isFinite(returnOverDrawdown)) {
            // No trades or no drawdown: neutral ratio instead of a failed evaluation.
            returnOverDrawdown = series.numFactory().zero();
        }
        // Score the dimensionless net return (cost-adjusted, scale-invariant
        // across price levels) so the 7:3 weighting mixes unit-consistent
        // terms; reported metrics stay raw.
        Num netReturn = new NetReturnCriterion(ReturnRepresentation.DECIMAL)
                .calculate(series, statement.getTradingRecord());
        if (!Num.isFinite(netReturn)) {
            // No closed trades: neutral return instead of a failed evaluation.
            netReturn = series.numFactory().zero();
        }
        Num seven = series.numFactory().numOf(7);
        Num three = series.numFactory().numOf(3);
        Num score = netReturn.multipliedBy(seven).plus(returnOverDrawdown.multipliedBy(three));
        return ObjectiveEvaluation.of(score,
                Map.of(NET_PROFIT, netProfit, RETURN_OVER_MAX_DRAWDOWN, returnOverDrawdown));
    }

    private static Strategy createSmaStrategy(BarSeries series, int fastBarCount, int slowBarCount,
            int stopLossPercentage) {
        Objects.requireNonNull(series, "series cannot be null");
        if (fastBarCount <= 0 || slowBarCount <= 0) {
            throw new IllegalArgumentException("SMA periods must be positive");
        }
        if (fastBarCount >= slowBarCount) {
            throw new IllegalArgumentException(FAST_BAR_COUNT + " must be lower than " + SLOW_BAR_COUNT);
        }
        if (stopLossPercentage < 0) {
            throw new IllegalArgumentException(STOP_LOSS_PERCENTAGE + " must be >= 0");
        }

        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        SMAIndicator fastSma = new SMAIndicator(closePrice, fastBarCount);
        SMAIndicator slowSma = new SMAIndicator(closePrice, slowBarCount);
        Rule entryRule = new CrossedUpIndicatorRule(fastSma, slowSma);
        Rule exitRule = new CrossedDownIndicatorRule(fastSma, slowSma)
                .or(new StopLossRule(closePrice, stopLossPercentage));
        int unstableBars = Math.max(fastSma.getCountOfUnstableBars(), slowSma.getCountOfUnstableBars());

        return new BaseStrategy(
                "SmaTrend(fast=" + fastBarCount + ",slow=" + slowBarCount + ",stop=" + stopLossPercentage + "%)",
                entryRule, exitRule, unstableBars);
    }

    private static String holdoutTakeaway(ParameterResearchReport report) {
        if (report.trainingLeaderboard().isEmpty()) {
            return "No valid training candidates were produced; inspect the failed evaluations before trusting this "
                    + "run.";
        }
        if (report.holdoutLeaderboard().isEmpty()) {
            return "No holdout scores were produced; increase validationBarCount before trusting the selection.";
        }

        RankedCandidate selected = report.trainingLeaderboard().getFirst();
        RankedCandidate holdoutWinner = report.holdoutLeaderboard().getFirst();
        RankedCandidate selectedOnHoldout = null;
        for (RankedCandidate candidate : report.holdoutLeaderboard()) {
            if (candidate.candidateId().equals(selected.candidateId())) {
                selectedOnHoldout = candidate;
                break;
            }
        }
        if (selectedOnHoldout == null) {
            return "The selected training candidate did not rank on the holdout; inspect invalid candidates before "
                    + "promoting a parameter set.";
        }
        if (holdoutWinner.candidateId().equals(selected.candidateId())) {
            return "The selected training candidate also led the holdout window (" + selected.candidateId()
                    + "), so this run shows stable in-sample and out-of-sample ranking.";
        }
        return "The selected training candidate ranked #" + selectedOnHoldout.holdoutRank() + " on holdout; the "
                + "holdout winner was " + holdoutWinner.candidateId()
                + ". Treat the training winner as a candidate for more validation, not a finished strategy.";
    }
}
