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
import org.ta4j.core.EvaluatedRule.JoinedResult;
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
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.AbstractEvaluatedRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

/**
 * Demonstrates {@link EvaluatedRule}: the same rules run in a backtest and are
 * then evaluated directly to inspect their snapshots. The RSI-based score is a
 * heuristic, not a calibrated probability.
 */
public class EvaluatedRuleExample {

    private static final Logger LOG = LogManager.getLogger(EvaluatedRuleExample.class);

    public enum ScoreKind {
        HEURISTIC_SCORE, CALIBRATED_PROBABILITY
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

        public ConfidenceEvaluation {
            Objects.requireNonNull(score, "score");
            Objects.requireNonNull(threshold, "threshold");
            Objects.requireNonNull(kind, "kind");
        }

        public boolean isAvailable() {
            return !score.isNaN();
        }
    }

    /**
     * Satisfied when an available score is at least the threshold.
     */
    public static final class ConfidenceGateRule extends AbstractEvaluatedRule<ConfidenceEvaluation> {

        private final Indicator<Num> score;
        private final Num threshold;
        private final ScoreKind kind;

        /**
         * @throws IllegalArgumentException if the threshold is not finite, or outside
         *                                  {@code [0, 1]} for a calibrated probability
         */
        public ConfidenceGateRule(Indicator<Num> score, Number threshold, ScoreKind kind) {
            this.score = Objects.requireNonNull(score, "score");
            this.kind = Objects.requireNonNull(kind, "kind");
            NumFactory numFactory = score.getBarSeries().numFactory();
            this.threshold = numFactory.numOf(Objects.requireNonNull(threshold, "threshold"));
            if (!Num.isFinite(this.threshold)) {
                throw new IllegalArgumentException("threshold must be finite: " + threshold);
            }
            if (kind == ScoreKind.CALIBRATED_PROBABILITY
                    && (this.threshold.isNegative() || this.threshold.isGreaterThan(numFactory.one()))) {
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
     * Caller-defined payload joining two different evaluation types.
     *
     * @param confidence the captured score observations
     * @param exposure   the captured trading-record observations
     * @since 0.29.1
     */
    public record EntryContext(ConfidenceEvaluation confidence, ExposureEvaluation exposure) {
    }

    /**
     * Satisfied while flat and fewer than {@code maxPositions} positions have been
     * closed.
     */
    public static final class FlatWithTradeBudgetRule extends AbstractEvaluatedRule<ExposureEvaluation> {

        private final int maxPositions;

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

        ConfidenceGateRule confidenceGate = new ConfidenceGateRule(heuristicScore, 0.6, ScoreKind.HEURISTIC_SCORE);
        confidenceGate.setName("RSI score >= 0.6");
        FlatWithTradeBudgetRule tradeBudget = new FlatWithTradeBudgetRule(5);
        tradeBudget.setName("Flat with trade budget");

        EvaluatedRule<JoinedResult<EntryContext>> entryRule = confidenceGate.and(tradeBudget, EntryContext::new);
        entryRule.setName("Confidence with trade budget");
        Rule exitRule = new UnderIndicatorRule(heuristicScore, 0.4);
        Strategy strategy = new BaseStrategy("Evaluated confidence gate", entryRule, exitRule);
        TradingRecord tradingRecord = new BarSeriesManager(series).run(strategy);
        LOG.info("Backtest closed {} positions (budget {})", tradingRecord.getPositionCount(), 5);

        int endIndex = series.getEndIndex();
        JoinedResult<EntryContext> entry = entryRule.evaluate(endIndex, tradingRecord);
        ConfidenceEvaluation confidence = entry.value().confidence();
        LOG.info("{} at {}: available={}, score={}, threshold={}, kind={}, satisfied={}", confidenceGate.getName(),
                endIndex, confidence.isAvailable(), confidence.score(), confidence.threshold(), confidence.kind(),
                confidenceGate.toBoolean(confidence));

        ExposureEvaluation exposure = entry.value().exposure();
        LOG.info("{} at {}: flat={}, closedPositions={}/{}, satisfied={}", tradeBudget.getName(), endIndex,
                exposure.flat(), exposure.closedPositions(), exposure.maxPositions(), tradeBudget.toBoolean(exposure));
        LOG.info("{} at {}: satisfied={}", entryRule.getName(), endIndex, entryRule.toBoolean(entry));

        ConfidenceEvaluation warmUp = confidenceGate.evaluate(series.getBeginIndex());
        LOG.info("Warm-up bar {}: available={}, satisfied={}", warmUp.index(), warmUp.isAvailable(),
                confidenceGate.toBoolean(warmUp));
    }
}
