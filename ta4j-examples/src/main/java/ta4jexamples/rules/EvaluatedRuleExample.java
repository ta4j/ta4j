/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.EvaluatedRule;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractEvaluatedRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

/**
 * Demonstrates {@link EvaluatedRule}: rules that return a typed, immutable
 * evaluation snapshot plus an explicit boolean projection of that snapshot.
 *
 * <p>
 * The same rule instances are used twice:
 * <ol>
 * <li>as ordinary {@link Rule}s inside a {@link BaseStrategy} backtest, where
 * the inherited boolean bridge evaluates once and projects once;</li>
 * <li>as rich rules, where the caller evaluates once, inspects the snapshot,
 * and projects that exact snapshot with {@code toBoolean(...)}.</li>
 * </ol>
 *
 * <p>
 * {@link ConfidenceGateRule} gates on a score. The score used here is RSI /
 * 100: a synthetic <em>heuristic score</em>, not a calibrated probability.
 * {@link FlatWithTradeBudgetRule} shows a non-confidence result that captures
 * trading-record observations.
 */
public class EvaluatedRuleExample {

    private static final Logger LOG = LogManager.getLogger(EvaluatedRuleExample.class);

    /** How a confidence score should be interpreted. */
    public enum ScoreKind {
        /** A ranking/heuristic score; comparable to a threshold, not a probability. */
        HEURISTIC_SCORE,
        /** A probability that was calibrated against observed outcomes. */
        CALIBRATED_PROBABILITY
    }

    /**
     * Immutable confidence snapshot.
     *
     * @param index     evaluated bar index
     * @param score     score at {@code index}; {@link NaN#NaN} when unavailable
     * @param threshold inclusive threshold configured on the rule
     * @param kind      score interpretation
     */
    public record ConfidenceEvaluation(int index, Num score, Num threshold, ScoreKind kind) {

        /**
         * Validates the snapshot components.
         */
        public ConfidenceEvaluation {
            Objects.requireNonNull(score, "score");
            Objects.requireNonNull(threshold, "threshold");
            Objects.requireNonNull(kind, "kind");
        }

        /**
         * @return {@code true} when a score was observed; a valid zero score is
         *         available
         */
        public boolean isAvailable() {
            return !score.isNaN();
        }
    }

    /**
     * Satisfied when an available score is greater than or equal to the configured
     * threshold. Warm-up bars and NaN scores are unavailable and never satisfy the
     * gate.
     */
    public static final class ConfidenceGateRule extends AbstractEvaluatedRule<ConfidenceEvaluation> {

        private final Indicator<Num> score;
        private final Num threshold;
        private final ScoreKind kind;

        /**
         * @param score     score indicator
         * @param threshold inclusive threshold, converted to the score series'
         *                  {@link org.ta4j.core.num.NumFactory}; must be within
         *                  {@code [0, 1]} for {@link ScoreKind#CALIBRATED_PROBABILITY}
         * @param kind      score interpretation
         * @throws IllegalArgumentException if the threshold is not finite in either
         *                                  factory, or outside {@code [0, 1]} for a
         *                                  calibrated probability
         */
        public ConfidenceGateRule(Indicator<Num> score, Num threshold, ScoreKind kind) {
            this.score = Objects.requireNonNull(score, "score");
            this.kind = Objects.requireNonNull(kind, "kind");
            if (!Num.isFinite(Objects.requireNonNull(threshold, "threshold"))) {
                throw new IllegalArgumentException("threshold must be finite: " + threshold);
            }
            // Mixed Num types throw on comparison, so store the threshold in the score's
            // factory.
            this.threshold = score.getBarSeries().numFactory().numOf(threshold.bigDecimalValue());
            if (!Num.isFinite(this.threshold)) {
                throw new IllegalArgumentException("threshold cannot be represented by the score NumFactory");
            }
            if (kind == ScoreKind.CALIBRATED_PROBABILITY && (this.threshold.isNegative()
                    || this.threshold.isGreaterThan(this.threshold.getNumFactory().one()))) {
                throw new IllegalArgumentException("probability threshold must be within [0, 1]: " + threshold);
            }
        }

        @Override
        public ConfidenceEvaluation evaluate(int index, TradingRecord tradingRecord) {
            BarSeries series = score.getBarSeries();
            // long arithmetic: begin index + unstable bars can exceed Integer.MAX_VALUE.
            boolean warmingUp = index < (long) series.getBeginIndex() + score.getCountOfUnstableBars();
            Num value = warmingUp ? NaN.NaN : score.getValue(index);
            return new ConfidenceEvaluation(index, value, threshold, kind);
        }

        @Override
        public boolean toBoolean(ConfidenceEvaluation result) {
            return result.isAvailable() && result.score().isGreaterThanOrEqual(result.threshold());
        }
    }

    /**
     * Immutable trading-record snapshot.
     *
     * @param index           evaluated bar index
     * @param flat            {@code true} when no position was open
     * @param closedPositions closed positions observed at evaluation time
     * @param maxPositions    configured position budget
     */
    public record ExposureEvaluation(int index, boolean flat, int closedPositions, int maxPositions) {
    }

    /**
     * Satisfied while flat and fewer than {@code maxPositions} positions have been
     * closed. The snapshot copies record observations, so projecting an old
     * snapshot never rereads the live record.
     */
    public static final class FlatWithTradeBudgetRule extends AbstractEvaluatedRule<ExposureEvaluation> {

        private final int maxPositions;

        /**
         * @param maxPositions maximum number of closed positions; must be positive
         * @throws IllegalArgumentException if {@code maxPositions <= 0}
         */
        public FlatWithTradeBudgetRule(int maxPositions) {
            if (maxPositions <= 0) {
                throw new IllegalArgumentException("maxPositions must be positive: " + maxPositions);
            }
            this.maxPositions = maxPositions;
        }

        @Override
        public ExposureEvaluation evaluate(int index, TradingRecord tradingRecord) {
            boolean flat = tradingRecord == null || tradingRecord.isClosed();
            int closedPositions = tradingRecord == null ? 0 : tradingRecord.getPositionCount();
            return new ExposureEvaluation(index, flat, closedPositions, maxPositions);
        }

        @Override
        public boolean toBoolean(ExposureEvaluation result) {
            return result.flat() && result.closedPositions() < result.maxPositions();
        }
    }

    public static void main(String[] args) {
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        NumericIndicator heuristicScore = NumericIndicator.of(new RSIIndicator(close, 14)).dividedBy(100);

        ConfidenceGateRule confidenceGate = new ConfidenceGateRule(heuristicScore, series.numFactory().numOf(0.6),
                ScoreKind.HEURISTIC_SCORE);
        confidenceGate.setName("RSI score >= 0.6");
        FlatWithTradeBudgetRule tradeBudget = new FlatWithTradeBudgetRule(5);
        tradeBudget.setName("Flat with trade budget");

        // Evaluated rules are ordinary rules: compose and backtest them directly.
        Rule entryRule = confidenceGate.and(tradeBudget);
        Rule exitRule = new UnderIndicatorRule(heuristicScore, 0.4);
        Strategy strategy = new BaseStrategy("Evaluated confidence gate", entryRule, exitRule);
        TradingRecord tradingRecord = new BarSeriesManager(series).run(strategy);
        LOG.info("Backtest closed {} positions (budget {})", tradingRecord.getPositionCount(), 5);

        // Rich path: evaluate once, inspect the snapshot, then project that exact
        // snapshot.
        int endIndex = series.getEndIndex();
        ConfidenceEvaluation confidence = confidenceGate.evaluate(endIndex);
        LOG.info("{} at {}: available={}, score={}, threshold={}, kind={}, satisfied={}", confidenceGate.getName(),
                endIndex, confidence.isAvailable(), confidence.score(), confidence.threshold(), confidence.kind(),
                confidenceGate.toBoolean(confidence));

        ExposureEvaluation exposure = tradeBudget.evaluate(endIndex, tradingRecord);
        LOG.info("{} at {}: flat={}, closedPositions={}/{}, satisfied={}", tradeBudget.getName(), endIndex,
                exposure.flat(), exposure.closedPositions(), exposure.maxPositions(), tradeBudget.toBoolean(exposure));

        ConfidenceEvaluation warmUp = confidenceGate.evaluate(series.getBeginIndex());
        LOG.info("Warm-up bar {}: available={}, satisfied={}", warmUp.index(), warmUp.isAvailable(),
                confidenceGate.toBoolean(warmUp));
    }
}
