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
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.TradingStatementExecutionResult.RankingProfile;
import org.ta4j.core.backtest.TradingStatementExecutionResult.WeightedCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch;
import org.ta4j.core.research.ParameterResearch.CandidateScore;
import org.ta4j.core.research.ParameterResearch.ParameterDomain;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.ParameterSet;
import org.ta4j.core.research.ParameterResearch.PruningPolicy;
import org.ta4j.core.research.ParameterResearch.ResearchConfig;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.StopLossRule;

import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

/**
 * Example parameter research workflow for a simple SMA trend strategy.
 *
 * <p>
 * The example searches fast/slow SMA periods plus stop-loss percentages,
 * rejects invalid fast/slow combinations, prunes exact duplicate trading
 * records, ranks representatives by a weighted objective, and checks whether
 * the selected training candidates survive a holdout window.
 * </p>
 */
public class SimpleMovingAverageRangeBacktest {

    private static final Logger LOG = LogManager.getLogger(SimpleMovingAverageRangeBacktest.class);
    private static final String FAST_BAR_COUNT = "fastBarCount";
    private static final String SLOW_BAR_COUNT = "slowBarCount";
    private static final String STOP_LOSS_PERCENTAGE = "stopLossPercentage";
    private static final int DEFAULT_TOP_STRATEGIES = 3;
    private static final int DEFAULT_VALIDATION_BARS = 63;
    private static final int DEFAULT_REPORT_ROWS = 5;

    public static void main(String[] args) {
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();
        ParameterResearchReport report = runSmaResearch(series, DEFAULT_VALIDATION_BARS);

        LOG.info(System.lineSeparator() + formatResearchNarrative(report, DEFAULT_REPORT_ROWS));
    }

    /**
     * Runs the default SMA trend parameter research workflow.
     *
     * @param series         full series
     * @param validationBars final bars held out for validation
     * @return structured research report
     */
    static ParameterResearchReport runSmaResearch(BarSeries series, int validationBars) {
        return runSmaResearch(series, PruningPolicy.EXACT_TRADING_RECORD, validationBars);
    }

    /**
     * Runs the SMA trend parameter research workflow with a custom pruning policy.
     *
     * @param series         full series
     * @param pruningPolicy  pruning policy for representative selection
     * @param validationBars final bars held out for validation
     * @return structured research report
     */
    static ParameterResearchReport runSmaResearch(BarSeries series, PruningPolicy pruningPolicy, int validationBars) {
        Objects.requireNonNull(series, "series cannot be null");
        ResearchConfig config = ResearchConfig
                .holdout(validationBars, weightedRankingProfile(), series.numFactory().numOf(50),
                        DEFAULT_TOP_STRATEGIES)
                .withPruningPolicy(pruningPolicy);
        return ParameterResearch.run(series, smaParameterDomains(),
                SimpleMovingAverageRangeBacktest::validateParameters,
                SimpleMovingAverageRangeBacktest::createSmaStrategy, config);
    }

    /**
     * Materializes an SMA trend strategy from a parameter research candidate.
     *
     * @param series     bar series used by the strategy
     * @param parameters normalized parameter set
     * @return SMA trend strategy with unstable bars set from the slower SMA
     */
    static Strategy createSmaStrategy(BarSeries series, ParameterSet parameters) {
        return createSmaStrategy(series, parameters.intValue(FAST_BAR_COUNT), parameters.intValue(SLOW_BAR_COUNT),
                parameters.intValue(STOP_LOSS_PERCENTAGE));
    }

    static String formatResearchNarrative(ParameterResearchReport report, int maxRows) {
        Objects.requireNonNull(report, "report cannot be null");
        StringBuilder builder = new StringBuilder();
        builder.append("SMA trend parameter research")
                .append(System.lineSeparator())
                .append(report.formatSummary(maxRows))
                .append(System.lineSeparator())
                .append("Takeaway: ")
                .append(holdoutTakeaway(report));
        return builder.toString();
    }

    private static List<ParameterDomain> smaParameterDomains() {
        return List.of(ParameterDomain.periodRange(FAST_BAR_COUNT, 5, 35, 10),
                ParameterDomain.periodRange(SLOW_BAR_COUNT, 10, 50, 10),
                ParameterDomain.values(STOP_LOSS_PERCENTAGE, List.of(3, 6, 9)));
    }

    private static void validateParameters(ParameterSet parameters) {
        int fastBarCount = parameters.intValue(FAST_BAR_COUNT);
        int slowBarCount = parameters.intValue(SLOW_BAR_COUNT);
        if (fastBarCount >= slowBarCount) {
            throw new IllegalArgumentException(FAST_BAR_COUNT + " must be lower than " + SLOW_BAR_COUNT);
        }
    }

    private static RankingProfile weightedRankingProfile() {
        return RankingProfile.weighted(WeightedCriterion.of(new NetProfitCriterion(), 7.0),
                WeightedCriterion.of(new ReturnOverMaxDrawdownCriterion(), 3.0));
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
        if (report.validationScores().isEmpty()) {
            return "No holdout scores were produced; increase validationBarCount before trusting the selection.";
        }

        CandidateScore validationWinner = report.validationScores().getFirst();
        CandidateScore selectedValidation = scoreFor(report.validationScores(), report.selectedTopCandidateId());
        if (selectedValidation == null) {
            return "The selected training candidate did not evaluate on the holdout; inspect invalid candidates before "
                    + "promoting a parameter set.";
        }
        if (validationWinner.candidateId().equals(report.selectedTopCandidateId())) {
            return "The selected training candidate also led the holdout window (" + validationWinner.candidateId()
                    + "), so this run shows stable in-sample and out-of-sample ranking.";
        }
        return "The selected training candidate ranked #" + selectedValidation.rank() + " on holdout; the holdout "
                + "winner was " + validationWinner.candidateId()
                + ". Treat the training winner as a candidate for more validation, not a finished strategy.";
    }

    private static CandidateScore scoreFor(List<CandidateScore> scores, String candidateId) {
        for (CandidateScore score : scores) {
            if (score.candidateId().equals(candidateId)) {
                return score;
            }
        }
        return null;
    }
}
