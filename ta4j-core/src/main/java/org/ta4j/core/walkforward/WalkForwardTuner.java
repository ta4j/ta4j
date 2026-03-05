/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Generic candidate tuner built on top of {@link WalkForwardEngine}.
 *
 * <p>
 * The tuner evaluates candidates in batches, applies optional probability
 * calibration, scores each candidate using a pluggable objective, and keeps
 * only the top-k entries.
 *
 * @param <C> candidate context type
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
public final class WalkForwardTuner<C, P, O> {

    private final WalkForwardEngine<C, P, O> engine;
    private final WalkForwardObjective objective;
    private final int keepTopK;
    private final int batchSize;
    private final CalibrationMode calibrationMode;
    private final CalibrationGate calibrationGate;
    private final int calibrationRank;
    private final Function<O, Num> observedProbabilityExtractor;

    /**
     * Creates a tuner with calibration disabled.
     *
     * @param engine    walk-forward engine
     * @param objective objective scorer
     * @param keepTopK  number of top entries to retain
     * @param batchSize candidate batch size
     * @since 0.22.4
     */
    public WalkForwardTuner(WalkForwardEngine<C, P, O> engine, WalkForwardObjective objective, int keepTopK,
            int batchSize) {
        this(engine, objective, keepTopK, batchSize, CalibrationMode.NONE, CalibrationGate.defaultGate(), 1,
                ignored -> NaN.NaN);
    }

    /**
     * Creates a fully configured tuner.
     *
     * @param engine                       walk-forward engine
     * @param objective                    objective scorer
     * @param keepTopK                     number of top entries to retain
     * @param batchSize                    candidate batch size
     * @param calibrationMode              calibration strategy
     * @param calibrationGate              isotonic challenger gate
     * @param calibrationRank              prediction rank used for calibration
     * @param observedProbabilityExtractor outcome-to-probability extractor in
     *                                     {@code [0,1]}
     * @since 0.22.4
     */
    public WalkForwardTuner(WalkForwardEngine<C, P, O> engine, WalkForwardObjective objective, int keepTopK,
            int batchSize, CalibrationMode calibrationMode, CalibrationGate calibrationGate, int calibrationRank,
            Function<O, Num> observedProbabilityExtractor) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.objective = Objects.requireNonNull(objective, "objective");
        if (keepTopK <= 0) {
            throw new IllegalArgumentException("keepTopK must be > 0");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
        if (calibrationRank <= 0) {
            throw new IllegalArgumentException("calibrationRank must be > 0");
        }
        this.keepTopK = keepTopK;
        this.batchSize = batchSize;
        this.calibrationMode = Objects.requireNonNull(calibrationMode, "calibrationMode");
        this.calibrationGate = Objects.requireNonNull(calibrationGate, "calibrationGate");
        this.calibrationRank = calibrationRank;
        this.observedProbabilityExtractor = Objects.requireNonNull(observedProbabilityExtractor,
                "observedProbabilityExtractor");
    }

    /**
     * Tunes and ranks candidates.
     *
     * @param series     input series
     * @param candidates candidate list
     * @param config     run configuration
     * @return ranked leaderboard
     * @since 0.22.4
     */
    public WalkForwardLeaderboard<C> tune(BarSeries series, List<WalkForwardCandidate<C>> candidates,
            WalkForwardConfig config) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(config, "config");

        if (candidates.isEmpty()) {
            return new WalkForwardLeaderboard<>(List.of(), 0, 0, config.primaryHorizonBars());
        }

        Comparator<WalkForwardLeaderboard.Entry<C>> comparator = (left,
                right) -> compareScores(left.objectiveScore().totalScore(), right.objectiveScore().totalScore());
        Comparator<WalkForwardLeaderboard.Entry<C>> descending = comparator.reversed();

        PriorityQueue<WalkForwardLeaderboard.Entry<C>> topEntries = new PriorityQueue<>(keepTopK + 1, comparator);

        int evaluated = 0;
        for (int batchStart = 0; batchStart < candidates.size(); batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, candidates.size());
            for (int i = batchStart; i < batchEnd; i++) {
                WalkForwardCandidate<C> candidate = candidates.get(i);
                WalkForwardRunResult<P, O> runResult = engine.run(series, candidate.context(), config, candidate.id(),
                        Map.of("batchIndex", String.valueOf(batchStart / batchSize)));

                MetricBundle metricBundle = selectMetrics(runResult, config.primaryHorizonBars());
                CalibrationSelection calibrationSelection = applyCalibrationIfEnabled(runResult,
                        config.primaryHorizonBars(), metricBundle.globalMetrics, metricBundle.foldMetrics);

                WalkForwardObjective.Score objectiveScore = objective.evaluate(metricBundle.globalMetrics,
                        metricBundle.foldMetrics);

                WalkForwardLeaderboard.Entry<C> entry = new WalkForwardLeaderboard.Entry<>(candidate, objectiveScore,
                        metricBundle.globalMetrics, calibrationSelection, runResult);
                topEntries.offer(entry);
                if (topEntries.size() > keepTopK) {
                    topEntries.poll();
                }
                evaluated++;
            }
        }

        List<WalkForwardLeaderboard.Entry<C>> ranked = new ArrayList<>(topEntries);
        ranked.sort(descending);
        return new WalkForwardLeaderboard<>(ranked, evaluated, ranked.size(), config.primaryHorizonBars());
    }

    private static int compareScores(Num left, Num right) {
        double leftValue = left == null ? Double.NaN : left.doubleValue();
        double rightValue = right == null ? Double.NaN : right.doubleValue();
        boolean leftNaN = Double.isNaN(leftValue);
        boolean rightNaN = Double.isNaN(rightValue);
        if (leftNaN && rightNaN) {
            return 0;
        }
        if (leftNaN) {
            return -1;
        }
        if (rightNaN) {
            return 1;
        }
        return Double.compare(leftValue, rightValue);
    }

    private MetricBundle selectMetrics(WalkForwardRunResult<P, O> runResult, int primaryHorizonBars) {
        Map<String, Num> global = new HashMap<>(runResult.globalMetricsForHorizon(primaryHorizonBars));
        Map<String, Map<String, Num>> fold = new HashMap<>();
        for (Map.Entry<String, Map<String, Num>> entry : runResult.foldMetricsForHorizon(primaryHorizonBars)
                .entrySet()) {
            fold.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return new MetricBundle(global, fold);
    }

    private CalibrationSelection applyCalibrationIfEnabled(WalkForwardRunResult<P, O> runResult, int primaryHorizonBars,
            Map<String, Num> globalMetrics, Map<String, Map<String, Num>> foldMetrics) {
        if (calibrationMode == CalibrationMode.NONE) {
            return CalibrationSelection.none();
        }

        List<WalkForwardObservation<P, O>> rows = runResult.observationsByHorizon()
                .getOrDefault(primaryHorizonBars, List.of());
        NumFactory factory = resolveFactory(runResult, rows);

        List<Num> predicted = new ArrayList<>();
        List<Num> observed = new ArrayList<>();
        Map<String, List<Num>> foldPredicted = new HashMap<>();
        Map<String, List<Num>> foldObserved = new HashMap<>();

        for (WalkForwardObservation<P, O> row : rows) {
            if (row.prediction().rank() != calibrationRank) {
                continue;
            }
            Num p = WalkForwardMetric.normalizeAndClamp01(row.prediction().probability(), factory);
            Num y = WalkForwardMetric.normalizeAndClamp01(observedProbabilityExtractor.apply(row.realizedOutcome()),
                    factory);
            predicted.add(p);
            observed.add(y);
            foldPredicted.computeIfAbsent(row.foldId(), ignored -> new ArrayList<>()).add(p);
            foldObserved.computeIfAbsent(row.foldId(), ignored -> new ArrayList<>()).add(y);
        }

        if (predicted.isEmpty()) {
            return new CalibrationSelection("none", 0, NaN.NaN, NaN.NaN, NaN.NaN,
                    "no rank-" + calibrationRank + " samples available");
        }

        CalibrationScores rawScores = CalibrationScores.from(predicted, observed, foldPredicted, foldObserved, null,
                factory);

        ProbabilityCalibrator platt = new PlattCalibrator(factory);
        platt.fit(predicted, observed);
        CalibrationScores plattScores = CalibrationScores.from(predicted, observed, foldPredicted, foldObserved,
                platt::calibrate, factory);

        CalibrationScores chosen = plattScores;
        String chosenName = "platt";
        String reason = "Platt calibration selected by default";
        Num isotonicEce = NaN.NaN;

        if (calibrationMode == CalibrationMode.PLATT_WITH_ISOTONIC_CHALLENGER
                && predicted.size() >= calibrationGate.minimumSamples()) {
            ProbabilityCalibrator isotonic = new IsotonicCalibrator(factory);
            isotonic.fit(predicted, observed);
            CalibrationScores isotonicScores = CalibrationScores.from(predicted, observed, foldPredicted, foldObserved,
                    isotonic::calibrate, factory);
            isotonicEce = isotonicScores.ece;

            Num minimumEceImprovement = WalkForwardMetric.normalize(calibrationGate.minimumEceImprovement(), factory);
            Num maximumVarianceIncrease = WalkForwardMetric.normalize(calibrationGate.maximumVarianceIncrease(),
                    factory);

            boolean eceImproved = plattScores.ece.minus(isotonicScores.ece).isGreaterThanOrEqual(minimumEceImprovement);
            boolean varianceAllowed = isotonicScores.foldEceVariance.minus(plattScores.foldEceVariance)
                    .isLessThanOrEqual(maximumVarianceIncrease);
            if (eceImproved && varianceAllowed) {
                chosen = isotonicScores;
                chosenName = "isotonic";
                reason = "isotonic passed ECE and fold-variance gates";
            } else {
                reason = "isotonic rejected by gate";
            }
        }

        patchCalibrationMetrics(globalMetrics, chosen);
        for (Map<String, Num> foldMetricMap : foldMetrics.values()) {
            patchCalibrationMetrics(foldMetricMap, chosen);
        }

        return new CalibrationSelection(chosenName, predicted.size(), rawScores.ece, plattScores.ece, isotonicEce,
                reason);
    }

    private static NumFactory resolveFactory(WalkForwardRunResult<?, ?> runResult,
            List<? extends WalkForwardObservation<?, ?>> rows) {
        if (rows != null && !rows.isEmpty()) {
            Num probability = rows.getFirst().prediction().probability();
            if (!Num.isNaNOrNull(probability)) {
                return probability.getNumFactory();
            }
        }
        for (Map<String, Num> metricMap : runResult.globalMetricsByHorizon().values()) {
            for (Num metric : metricMap.values()) {
                if (!Num.isNaNOrNull(metric)) {
                    return metric.getNumFactory();
                }
            }
        }
        return DoubleNumFactory.getInstance();
    }

    private static void patchCalibrationMetrics(Map<String, Num> metricMap, CalibrationScores scores) {
        for (String key : new ArrayList<>(metricMap.keySet())) {
            String normalized = key.toLowerCase();
            if (normalized.contains("brier")) {
                metricMap.put(key, scores.brier);
            } else if (normalized.contains("logloss") || normalized.contains("log_loss")
                    || normalized.contains("log-loss")) {
                metricMap.put(key, scores.logLoss);
            } else if (normalized.contains("ece")) {
                metricMap.put(key, scores.ece);
            }
        }
    }

    private record MetricBundle(Map<String, Num> globalMetrics, Map<String, Map<String, Num>> foldMetrics) {
    }

    private record CalibrationScores(Num brier, Num logLoss, Num ece, Num foldEceVariance) {

        static CalibrationScores from(List<Num> predicted, List<Num> observed, Map<String, List<Num>> foldPred,
                Map<String, List<Num>> foldObs, Function<Num, Num> transformer, NumFactory factory) {
            Num brier = factory.zero();
            Num logLoss = factory.zero();
            Num ece = expectedCalibrationError(predicted, observed, transformer, factory);

            for (int i = 0; i < predicted.size(); i++) {
                Num rawPrediction = transformer == null ? predicted.get(i) : transformer.apply(predicted.get(i));
                Num p = WalkForwardMetric.clampOpen01(WalkForwardMetric.normalizeAndClamp01(rawPrediction, factory));
                Num y = WalkForwardMetric.normalizeAndClamp01(observed.get(i), factory);
                Num error = p.minus(y);
                brier = brier.plus(error.multipliedBy(error));
                Num one = factory.one();
                logLoss = logLoss
                        .plus(y.multipliedBy(p.log()).plus(one.minus(y).multipliedBy(one.minus(p).log())).negate());
            }
            brier = brier.dividedBy(factory.numOf(predicted.size()));
            logLoss = logLoss.dividedBy(factory.numOf(predicted.size()));

            List<Num> foldEces = new ArrayList<>();
            for (String foldId : foldPred.keySet()) {
                List<Num> foldPredictions = foldPred.get(foldId);
                List<Num> foldObserved = foldObs.getOrDefault(foldId, List.of());
                if (foldPredictions == null || foldObserved == null || foldPredictions.isEmpty()
                        || foldPredictions.size() != foldObserved.size()) {
                    continue;
                }
                foldEces.add(expectedCalibrationError(foldPredictions, foldObserved, transformer, factory));
            }

            Num variance = factory.zero();
            if (!foldEces.isEmpty()) {
                Num mean = factory.zero();
                for (Num foldEce : foldEces) {
                    mean = mean.plus(foldEce);
                }
                mean = mean.dividedBy(factory.numOf(foldEces.size()));
                for (Num foldEce : foldEces) {
                    Num delta = foldEce.minus(mean);
                    variance = variance.plus(delta.multipliedBy(delta));
                }
                variance = variance.dividedBy(factory.numOf(foldEces.size()));
            }

            return new CalibrationScores(brier, logLoss, ece, variance);
        }

        private static Num expectedCalibrationError(List<Num> predicted, List<Num> observed,
                Function<Num, Num> transformer, NumFactory factory) {
            int bins = 10;
            double[] predSums = new double[bins];
            double[] obsSums = new double[bins];
            int[] counts = new int[bins];

            for (int i = 0; i < predicted.size(); i++) {
                Num rawPrediction = transformer == null ? predicted.get(i) : transformer.apply(predicted.get(i));
                Num p = WalkForwardMetric.normalizeAndClamp01(rawPrediction, factory);
                Num y = WalkForwardMetric.normalizeAndClamp01(observed.get(i), factory);
                int index = Math.min(bins - 1, (int) Math.floor(p.doubleValue() * bins));
                predSums[index] += p.doubleValue();
                obsSums[index] += y.doubleValue();
                counts[index]++;
            }

            double ece = 0.0;
            for (int i = 0; i < bins; i++) {
                if (counts[i] == 0) {
                    continue;
                }
                double meanPred = predSums[i] / counts[i];
                double meanObs = obsSums[i] / counts[i];
                ece += (Math.abs(meanPred - meanObs) * counts[i]) / predicted.size();
            }
            return factory.numOf(ece);
        }
    }

    /**
     * Calibration modes for tuner evaluation.
     *
     * @since 0.22.4
     */
    public enum CalibrationMode {
        /** No probability calibration in tuning. */
        NONE,
        /** Fit and apply Platt calibration only. */
        PLATT,
        /** Fit Platt and evaluate isotonic as a gated challenger. */
        PLATT_WITH_ISOTONIC_CHALLENGER
    }

    /**
     * Gating thresholds for selecting isotonic calibration over Platt calibration.
     *
     * @param minimumSamples          minimum samples required to evaluate isotonic
     * @param minimumEceImprovement   minimum ECE improvement required to switch to
     *                                isotonic
     * @param maximumVarianceIncrease maximum allowed fold-variance increase versus
     *                                Platt
     * @since 0.22.4
     */
    public record CalibrationGate(int minimumSamples, Num minimumEceImprovement, Num maximumVarianceIncrease) {

        /**
         * Creates a validated calibration gate.
         */
        public CalibrationGate {
            if (minimumSamples <= 0) {
                throw new IllegalArgumentException("minimumSamples must be > 0");
            }
            Objects.requireNonNull(minimumEceImprovement, "minimumEceImprovement");
            Objects.requireNonNull(maximumVarianceIncrease, "maximumVarianceIncrease");
            if (minimumEceImprovement.isNegative() || minimumEceImprovement.isNaN()) {
                throw new IllegalArgumentException("minimumEceImprovement must be >= 0.0");
            }
            if (maximumVarianceIncrease.isNegative() || maximumVarianceIncrease.isNaN()) {
                throw new IllegalArgumentException("maximumVarianceIncrease must be >= 0.0");
            }
        }

        /**
         * @return default gate values
         * @since 0.22.4
         */
        public static CalibrationGate defaultGate() {
            NumFactory factory = DoubleNumFactory.getInstance();
            return new CalibrationGate(150, factory.numOf(0.005), factory.numOf(0.01));
        }
    }

    /**
     * Calibration selection summary for one candidate evaluation.
     *
     * @param selected    selected calibrator id ({@code none}, {@code platt},
     *                    {@code isotonic})
     * @param sampleCount number of samples used to fit/evaluate calibration
     * @param rawEce      raw expected calibration error
     * @param plattEce    Platt-calibrated expected calibration error
     * @param isotonicEce isotonic-calibrated expected calibration error
     * @param reason      selection rationale
     * @since 0.22.4
     */
    public record CalibrationSelection(String selected, int sampleCount, Num rawEce, Num plattEce, Num isotonicEce,
            String reason) {

        /**
         * Creates a validated calibration selection.
         */
        public CalibrationSelection {
            Objects.requireNonNull(selected, "selected");
            Objects.requireNonNull(rawEce, "rawEce");
            Objects.requireNonNull(plattEce, "plattEce");
            Objects.requireNonNull(isotonicEce, "isotonicEce");
            Objects.requireNonNull(reason, "reason");
        }

        /**
         * @return selection for no-calibration mode
         * @since 0.22.4
         */
        public static CalibrationSelection none() {
            return new CalibrationSelection("none", 0, NaN.NaN, NaN.NaN, NaN.NaN, "calibration disabled");
        }
    }

    private interface ProbabilityCalibrator {

        void fit(List<Num> predictedProbabilities, List<Num> observedProbabilities);

        Num calibrate(Num rawProbability);
    }

    private static final class PlattCalibrator implements ProbabilityCalibrator {

        private final NumFactory factory;
        private final double epsilon;
        private double a;
        private double b;

        private PlattCalibrator(NumFactory factory) {
            this.factory = factory;
            this.epsilon = factory.epsilon().doubleValue();
            this.a = 1.0;
            this.b = 0.0;
        }

        @Override
        public void fit(List<Num> predictedProbabilities, List<Num> observedProbabilities) {
            if (predictedProbabilities == null || observedProbabilities == null
                    || predictedProbabilities.size() != observedProbabilities.size()
                    || predictedProbabilities.isEmpty()) {
                return;
            }

            double learningRate = 0.05;
            int iterations = 200;

            for (int iteration = 0; iteration < iterations; iteration++) {
                double gradientA = 0.0;
                double gradientB = 0.0;

                for (int i = 0; i < predictedProbabilities.size(); i++) {
                    double raw = clampAsDouble(predictedProbabilities.get(i));
                    double observed = clampAsDouble(observedProbabilities.get(i));
                    double logit = Math.log(raw / Math.max(epsilon, 1.0 - raw));
                    double probability = sigmoid((a * logit) + b);
                    double error = probability - observed;
                    gradientA += error * logit;
                    gradientB += error;
                }

                gradientA /= predictedProbabilities.size();
                gradientB /= predictedProbabilities.size();
                a -= learningRate * gradientA;
                b -= learningRate * gradientB;
            }
        }

        @Override
        public Num calibrate(Num rawProbability) {
            double raw = clampAsDouble(rawProbability);
            double logit = Math.log(raw / Math.max(epsilon, 1.0 - raw));
            return factory.numOf(clampAsDouble(factory.numOf(sigmoid((a * logit) + b))));
        }

        private double clampAsDouble(Num value) {
            Num normalized = WalkForwardMetric.normalizeAndClamp01(value, factory);
            double primitive = normalized.doubleValue();
            return Math.max(epsilon, Math.min(1.0 - epsilon, primitive));
        }

        private static double sigmoid(double value) {
            return 1.0 / (1.0 + Math.exp(-value));
        }
    }

    private static final class IsotonicCalibrator implements ProbabilityCalibrator {

        private final NumFactory factory;
        private List<Double> thresholds = List.of(0.0, 1.0);
        private List<Double> values = List.of(0.0, 1.0);

        private IsotonicCalibrator(NumFactory factory) {
            this.factory = factory;
        }

        @Override
        public void fit(List<Num> predictedProbabilities, List<Num> observedProbabilities) {
            if (predictedProbabilities == null || observedProbabilities == null
                    || predictedProbabilities.size() != observedProbabilities.size()
                    || predictedProbabilities.isEmpty()) {
                return;
            }

            List<CalibrationPoint> points = new ArrayList<>(predictedProbabilities.size());
            for (int i = 0; i < predictedProbabilities.size(); i++) {
                points.add(new CalibrationPoint(clamp(predictedProbabilities.get(i)),
                        clamp(observedProbabilities.get(i))));
            }
            points.sort(Comparator.comparingDouble(CalibrationPoint::predicted));

            List<Block> blocks = new ArrayList<>();
            for (CalibrationPoint point : points) {
                blocks.add(new Block(point.predicted(), point.predicted(), point.observed(), 1));
                while (blocks.size() >= 2) {
                    Block right = blocks.get(blocks.size() - 1);
                    Block left = blocks.get(blocks.size() - 2);
                    if (left.mean() <= right.mean()) {
                        break;
                    }
                    Block merged = left.merge(right);
                    blocks.remove(blocks.size() - 1);
                    blocks.remove(blocks.size() - 1);
                    blocks.add(merged);
                }
            }

            List<Double> newThresholds = new ArrayList<>();
            List<Double> newValues = new ArrayList<>();
            for (Block block : blocks) {
                newThresholds.add(block.maxPredicted());
                newValues.add(clamp(block.mean()));
            }

            if (!newThresholds.isEmpty()) {
                this.thresholds = List.copyOf(newThresholds);
                this.values = List.copyOf(newValues);
            }
        }

        @Override
        public Num calibrate(Num rawProbability) {
            double p = clamp(rawProbability);
            for (int i = 0; i < thresholds.size(); i++) {
                if (p <= thresholds.get(i)) {
                    return factory.numOf(values.get(i));
                }
            }
            return factory.numOf(values.get(values.size() - 1));
        }

        private double clamp(Num value) {
            return WalkForwardMetric.normalizeAndClamp01(value, factory).doubleValue();
        }

        private double clamp(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return 0.5;
            }
            if (value < 0.0) {
                return 0.0;
            }
            if (value > 1.0) {
                return 1.0;
            }
            return value;
        }

        private record CalibrationPoint(double predicted, double observed) {
        }

        private record Block(double minPredicted, double maxPredicted, double observedSum, int weight) {

            double mean() {
                return observedSum / weight;
            }

            Block merge(Block other) {
                return new Block(Math.min(minPredicted, other.minPredicted), Math.max(maxPredicted, other.maxPredicted),
                        observedSum + other.observedSum, weight + other.weight);
            }
        }
    }
}
