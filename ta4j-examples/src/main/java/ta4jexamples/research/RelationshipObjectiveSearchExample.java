/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.research;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedBooleanIndicator;
import org.ta4j.core.indicators.statistics.event.EventSynchronizationIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch;
import org.ta4j.core.research.ParameterResearch.ObjectiveEvaluation;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.RankedCandidate;
import org.ta4j.core.research.ParameterResearch.ResearchWindow;
import org.ta4j.core.research.ParameterResearch.SearchPlan;

import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

/**
 * Example parameter research workflow over a relationship objective.
 *
 * <p>
 * The example tunes the lookback of a momentum zero-cross signal so its events
 * best synchronize with short-horizon rally events (close up at least
 * {@value #RISE_THRESHOLD_PERCENT}% over the next {@value #FORECAST_BARS}
 * bars). Candidates are scored with the terminal-window F1 of an
 * {@link EventSynchronizationIndicator}, then the top training candidates are
 * rebuilt and rescored on a holdout window.
 * </p>
 *
 * <p>
 * Every indicator is constructed from the evaluation window only, so training
 * scores never read holdout bars; switching engines is a one-line change in
 * {@link #main(String[])} between {@code SearchPlan.grid(...)},
 * {@code SearchPlan.genetic(...)}, and {@code SearchPlan.particleSwarm(...)}.
 * </p>
 */
public class RelationshipObjectiveSearchExample {

    private static final Logger LOG = LogManager.getLogger(RelationshipObjectiveSearchExample.class);
    private static final String LOOKBACK = "lookback";
    private static final String F1 = "F1";
    private static final String PRECISION = "Precision";
    private static final String RECALL = "Recall";
    private static final int FORECAST_BARS = 5;
    private static final double RISE_THRESHOLD_PERCENT = 1.5;
    private static final int SYNC_WINDOW_BARS = 120;
    private static final int TOLERANCE_BARS = 2;
    private static final int DEFAULT_VALIDATION_BARS = 63;
    private static final int DEFAULT_TOP_CANDIDATES = 3;

    public static void main(String[] args) {
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();
        ParameterResearchReport report = runRelationshipResearch(series, SearchPlan.grid(29), DEFAULT_VALIDATION_BARS);

        LOG.info(System.lineSeparator() + formatResearchNarrative(report, DEFAULT_TOP_CANDIDATES));
    }

    /**
     * Runs the momentum/rally synchronization parameter research workflow.
     *
     * <p>
     * The search plan is the single engine switch: exhaustive
     * {@code SearchPlan.grid(29)}, seeded {@code SearchPlan.genetic(29, 42L)}, or
     * seeded {@code SearchPlan.particleSwarm(29, 42L)}.
     * </p>
     *
     * @param series         full series
     * @param plan           search engine and evaluation budget
     * @param validationBars final bars held out for validation
     * @return structured research report
     */
    static ParameterResearchReport runRelationshipResearch(BarSeries series, SearchPlan plan, int validationBars) {
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(plan, "plan cannot be null");
        return ParameterResearch.builder(series)
                .integer(LOOKBACK, 2, 30)
                .candidate(
                        (window, parameters) -> momentumCrossUpEvents(window.series(), parameters.intValue(LOOKBACK)))
                .maximize(RelationshipObjectiveSearchExample::scoreSynchronization)
                .search(plan)
                .holdoutBarCount(validationBars)
                .topK(DEFAULT_TOP_CANDIDATES)
                .run();
    }

    /**
     * Builds momentum zero-cross-up events for one evaluation window.
     *
     * @param series   window series (never the full dataset)
     * @param lookback momentum lookback in bars
     * @return event indicator that is true on bars where momentum over
     *         {@code lookback} bars crosses above zero
     */
    static Indicator<Boolean> momentumCrossUpEvents(BarSeries series, int lookback) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int begin = series.getBeginIndex();
        Boolean[] events = new Boolean[series.getBarCount()];
        for (int i = 0; i < events.length; i++) {
            int index = begin + i;
            if (index - lookback - 1 < begin) {
                events[i] = false;
                continue;
            }
            Num momentum = close.getValue(index).minus(close.getValue(index - lookback));
            Num previousMomentum = close.getValue(index - 1).minus(close.getValue(index - lookback - 1));
            events[i] = momentum.isPositive() && !previousMomentum.isPositive();
        }
        return new FixedBooleanIndicator(series, events);
    }

    /**
     * Scores a predicted event stream against rally events on the same window. The
     * reference reads the window only, so no holdout information leaks into
     * training scores.
     */
    private static ObjectiveEvaluation scoreSynchronization(Indicator<Boolean> predicted, ResearchWindow window) {
        BarSeries series = window.series();
        Indicator<Boolean> reference = rallyAheadEvents(series);
        int syncWindowBars = Math.min(SYNC_WINDOW_BARS, series.getBarCount());
        EventSynchronizationIndicator synchronization = new EventSynchronizationIndicator(predicted, reference,
                syncWindowBars, TOLERANCE_BARS);
        EventSynchronizationIndicator.Result result = synchronization.getResult(series.getEndIndex());
        return ObjectiveEvaluation.of(result.f1Score(), Map.of(PRECISION, result.precision(), RECALL, result.recall()));
    }

    private static Indicator<Boolean> rallyAheadEvents(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        double threshold = RISE_THRESHOLD_PERCENT / 100.0;
        Boolean[] events = new Boolean[series.getBarCount()];
        for (int i = 0; i < events.length; i++) {
            int index = begin + i;
            events[i] = index + FORECAST_BARS <= end && close.getValue(index + FORECAST_BARS)
                    .minus(close.getValue(index))
                    .dividedBy(close.getValue(index))
                    .doubleValue() > threshold;
        }
        return new FixedBooleanIndicator(series, events);
    }

    static String formatResearchNarrative(ParameterResearchReport report, int maxRows) {
        Objects.requireNonNull(report, "report cannot be null");
        StringBuilder builder = new StringBuilder();
        builder.append("Momentum/rally synchronization parameter research")
                .append(System.lineSeparator())
                .append("Candidate space: ")
                .append(report.counts().proposed())
                .append(" proposals, ")
                .append(report.counts().rejected())
                .append(" rejected, ")
                .append(report.counts().attempted())
                .append(" evaluated (")
                .append(report.terminationReason())
                .append(")")
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
                    .append(" ")
                    .append(F1)
                    .append("=")
                    .append(score)
                    .append(" ")
                    .append(PRECISION)
                    .append("=")
                    .append(metrics.get(PRECISION))
                    .append(" ")
                    .append(RECALL)
                    .append("=")
                    .append(metrics.get(RECALL))
                    .append(System.lineSeparator());
        }
    }

    private static String holdoutTakeaway(ParameterResearchReport report) {
        if (report.trainingLeaderboard().isEmpty()) {
            return "No valid training candidates were produced; inspect the failed evaluations before trusting this "
                    + "run.";
        }
        if (report.holdoutLeaderboard().isEmpty()) {
            return "No holdout scores were produced; increase validationBars before trusting the selection.";
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
            return "The selected training candidate did not rank on the holdout; inspect failed evaluations before "
                    + "promoting a lookback.";
        }
        if (holdoutWinner.candidateId().equals(selected.candidateId())) {
            return "The selected training lookback also led the holdout window (" + selected.candidateId()
                    + "), so this run shows stable in-sample and out-of-sample synchronization.";
        }
        return "The selected training lookback ranked #" + selectedOnHoldout.holdoutRank() + " on holdout; the "
                + "holdout winner was " + holdoutWinner.candidateId()
                + ". Treat the training winner as a candidate for more validation, not a finished signal.";
    }
}
