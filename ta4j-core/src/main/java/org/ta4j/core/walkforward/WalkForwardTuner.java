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
    private final Function<O, Double> observedProbabilityExtractor;

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
                ignored -> Double.NaN);
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
            Function<O, Double> observedProbabilityExtractor) {
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

        Comparator<WalkForwardLeaderboard.Entry<C>> comparator = Comparator
                .comparingDouble((WalkForwardLeaderboard.Entry<C> entry) -> entry.objectiveScore().totalScore())
                .reversed();

        PriorityQueue<WalkForwardLeaderboard.Entry<C>> topEntries = new PriorityQueue<>(keepTopK + 1,
                comparator.reversed());

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

                WalkForwardObjectiveScore objectiveScore = objective.evaluate(metricBundle.globalMetrics,
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
        ranked.sort(comparator);
        return new WalkForwardLeaderboard<>(ranked, evaluated, ranked.size(), config.primaryHorizonBars());
    }

    private MetricBundle selectMetrics(WalkForwardRunResult<P, O> runResult, int primaryHorizonBars) {
        Map<String, Double> global = new HashMap<>(runResult.globalMetricsForHorizon(primaryHorizonBars));
        Map<String, Map<String, Double>> fold = new HashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : runResult.foldMetricsForHorizon(primaryHorizonBars)
                .entrySet()) {
            fold.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return new MetricBundle(global, fold);
    }

    private CalibrationSelection applyCalibrationIfEnabled(WalkForwardRunResult<P, O> runResult, int primaryHorizonBars,
            Map<String, Double> globalMetrics, Map<String, Map<String, Double>> foldMetrics) {
        if (calibrationMode == CalibrationMode.NONE) {
            return CalibrationSelection.none();
        }

        List<WalkForwardObservation<P, O>> rows = runResult.observationsByHorizon()
                .getOrDefault(primaryHorizonBars, List.of());
        List<Double> predicted = new ArrayList<>();
        List<Double> observed = new ArrayList<>();
        Map<String, List<Double>> foldPredicted = new HashMap<>();
        Map<String, List<Double>> foldObserved = new HashMap<>();

        for (WalkForwardObservation<P, O> row : rows) {
            if (row.prediction().rank() != calibrationRank) {
                continue;
            }
            double p = WalkForwardMetricSupport.clamp01(row.prediction().probability());
            double y = WalkForwardMetricSupport.clamp01(observedProbabilityExtractor.apply(row.realizedOutcome()));
            predicted.add(p);
            observed.add(y);
            foldPredicted.computeIfAbsent(row.foldId(), ignored -> new ArrayList<>()).add(p);
            foldObserved.computeIfAbsent(row.foldId(), ignored -> new ArrayList<>()).add(y);
        }

        if (predicted.isEmpty()) {
            return new CalibrationSelection("none", 0, Double.NaN, Double.NaN, Double.NaN,
                    "no rank-" + calibrationRank + " samples available");
        }

        CalibrationScores rawScores = CalibrationScores.from(predicted, observed, foldPredicted, foldObserved, null);

        PlattCalibrator platt = new PlattCalibrator();
        platt.fit(predicted, observed);
        CalibrationScores plattScores = CalibrationScores.from(predicted, observed, foldPredicted, foldObserved,
                platt::calibrate);

        CalibrationScores chosen = plattScores;
        String chosenName = "platt";
        String reason = "Platt calibration selected by default";
        double isotonicEce = Double.NaN;

        if (calibrationMode == CalibrationMode.PLATT_WITH_ISOTONIC_CHALLENGER
                && predicted.size() >= calibrationGate.minimumSamples()) {
            IsotonicCalibrator isotonic = new IsotonicCalibrator();
            isotonic.fit(predicted, observed);
            CalibrationScores isotonicScores = CalibrationScores.from(predicted, observed, foldPredicted, foldObserved,
                    isotonic::calibrate);
            isotonicEce = isotonicScores.ece;

            boolean eceImproved = plattScores.ece - isotonicScores.ece >= calibrationGate.minimumEceImprovement();
            boolean varianceAllowed = isotonicScores.foldEceVariance - plattScores.foldEceVariance <= calibrationGate
                    .maximumVarianceIncrease();
            if (eceImproved && varianceAllowed) {
                chosen = isotonicScores;
                chosenName = "isotonic";
                reason = "isotonic passed ECE and fold-variance gates";
            } else {
                reason = "isotonic rejected by gate";
            }
        }

        patchCalibrationMetrics(globalMetrics, chosen);
        for (Map<String, Double> foldMetricMap : foldMetrics.values()) {
            patchCalibrationMetrics(foldMetricMap, chosen);
        }

        return new CalibrationSelection(chosenName, predicted.size(), rawScores.ece, plattScores.ece, isotonicEce,
                reason);
    }

    private static void patchCalibrationMetrics(Map<String, Double> metricMap, CalibrationScores scores) {
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

    private record MetricBundle(Map<String, Double> globalMetrics, Map<String, Map<String, Double>> foldMetrics) {
    }

    private record CalibrationScores(double brier, double logLoss, double ece, double foldEceVariance) {

        static CalibrationScores from(List<Double> predicted, List<Double> observed, Map<String, List<Double>> foldPred,
                Map<String, List<Double>> foldObs, Function<Double, Double> transformer) {
            double brier = 0.0;
            double logLoss = 0.0;
            double ece = expectedCalibrationError(predicted, observed, transformer);
            double epsilon = 1.0e-15;

            for (int i = 0; i < predicted.size(); i++) {
                double p = transformer == null ? predicted.get(i) : transformer.apply(predicted.get(i));
                double y = observed.get(i);
                p = Math.min(1.0 - epsilon, Math.max(epsilon, p));
                double error = p - y;
                brier += error * error;
                logLoss += -(y * Math.log(p) + (1.0 - y) * Math.log(1.0 - p));
            }
            brier /= predicted.size();
            logLoss /= predicted.size();

            List<Double> foldEces = new ArrayList<>();
            for (String foldId : foldPred.keySet()) {
                List<Double> foldPredictions = foldPred.get(foldId);
                List<Double> foldObserved = foldObs.getOrDefault(foldId, List.of());
                if (foldPredictions == null || foldObserved == null || foldPredictions.isEmpty()
                        || foldPredictions.size() != foldObserved.size()) {
                    continue;
                }
                foldEces.add(expectedCalibrationError(foldPredictions, foldObserved, transformer));
            }

            double variance = 0.0;
            if (!foldEces.isEmpty()) {
                double mean = 0.0;
                for (double foldEce : foldEces) {
                    mean += foldEce;
                }
                mean /= foldEces.size();
                for (double foldEce : foldEces) {
                    double delta = foldEce - mean;
                    variance += delta * delta;
                }
                variance /= foldEces.size();
            }

            return new CalibrationScores(brier, logLoss, ece, variance);
        }

        private static double expectedCalibrationError(List<Double> predicted, List<Double> observed,
                Function<Double, Double> transformer) {
            int bins = 10;
            double[] predSums = new double[bins];
            double[] obsSums = new double[bins];
            int[] counts = new int[bins];

            for (int i = 0; i < predicted.size(); i++) {
                double p = transformer == null ? predicted.get(i) : transformer.apply(predicted.get(i));
                p = WalkForwardMetricSupport.clamp01(p);
                double y = WalkForwardMetricSupport.clamp01(observed.get(i));
                int index = Math.min(bins - 1, (int) Math.floor(p * bins));
                predSums[index] += p;
                obsSums[index] += y;
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
            return ece;
        }
    }
}
