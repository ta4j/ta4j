/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.ta4j.core.BarSeries;
import org.ta4j.core.backtest.ProgressCompletion;
import org.ta4j.core.num.Num;

/**
 * Generic walk-forward execution engine.
 *
 * <p>
 * The engine orchestrates split iteration, snapshot generation, fixed-horizon
 * outcome labeling, leakage audit tracing, and metric aggregation.
 *
 * @param <C> provider context type
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
public final class WalkForwardEngine<C, P, O> {

    private final WalkForwardSplitter splitter;
    private final PredictionProvider<C, P> predictionProvider;
    private final OutcomeLabeler<P, O> outcomeLabeler;
    private final List<WalkForwardMetric<P, O>> metrics;
    private final Consumer<Integer> progressCallback;
    private final Consumer<WalkForwardRunResult.LeakageAudit> leakageAuditHook;

    /**
     * Creates an engine with no-op progress and audit hooks.
     *
     * @param splitter           split strategy
     * @param predictionProvider prediction provider
     * @param outcomeLabeler     outcome labeler
     * @param metrics            metrics to compute
     * @since 0.22.4
     */
    public WalkForwardEngine(WalkForwardSplitter splitter, PredictionProvider<C, P> predictionProvider,
            OutcomeLabeler<P, O> outcomeLabeler, List<WalkForwardMetric<P, O>> metrics) {
        this(splitter, predictionProvider, outcomeLabeler, metrics, ProgressCompletion.noOp(), record -> {
            // no-op
        });
    }

    /**
     * Creates an engine with explicit progress and audit hooks.
     *
     * @param splitter           split strategy
     * @param predictionProvider prediction provider
     * @param outcomeLabeler     outcome labeler
     * @param metrics            metrics to compute
     * @param progressCallback   callback invoked after each decision index
     * @param leakageAuditHook   callback invoked for each audit record
     * @since 0.22.4
     */
    public WalkForwardEngine(WalkForwardSplitter splitter, PredictionProvider<C, P> predictionProvider,
            OutcomeLabeler<P, O> outcomeLabeler, List<WalkForwardMetric<P, O>> metrics,
            Consumer<Integer> progressCallback, Consumer<WalkForwardRunResult.LeakageAudit> leakageAuditHook) {
        this.splitter = Objects.requireNonNull(splitter, "splitter");
        this.predictionProvider = Objects.requireNonNull(predictionProvider, "predictionProvider");
        this.outcomeLabeler = Objects.requireNonNull(outcomeLabeler, "outcomeLabeler");
        this.metrics = List.copyOf(Objects.requireNonNull(metrics, "metrics"));
        this.progressCallback = progressCallback == null ? ProgressCompletion.noOp() : progressCallback;
        this.leakageAuditHook = leakageAuditHook == null ? record -> {
            // no-op
        } : leakageAuditHook;
    }

    /**
     * Executes walk-forward evaluation for one candidate context.
     *
     * @param series  input series
     * @param context provider context
     * @param config  run configuration. Keep this configuration fixed as a baseline
     *                when comparing candidates inside the same tuning cycle.
     * @return run result bundle
     * @since 0.22.4
     */
    public WalkForwardRunResult<P, O> run(BarSeries series, C context, WalkForwardConfig config) {
        return run(series, context, config, "candidate-default", Map.of());
    }

    /**
     * Executes walk-forward evaluation for one candidate context and manifest
     * metadata.
     *
     * @param series           input series
     * @param context          provider context
     * @param config           run configuration. Keep this configuration fixed as a
     *                         baseline when comparing candidates inside the same
     *                         tuning cycle.
     * @param candidateId      candidate id for manifesting
     * @param manifestMetadata additional manifest metadata
     * @return run result bundle
     * @since 0.22.4
     */
    public WalkForwardRunResult<P, O> run(BarSeries series, C context, WalkForwardConfig config, String candidateId,
            Map<String, String> manifestMetadata) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(candidateId, "candidateId");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }

        long overallStart = System.nanoTime();

        List<WalkForwardSplit> splits = splitter.split(series, config);
        List<PredictionSnapshot<P>> snapshots = new ArrayList<>();
        List<WalkForwardRunResult.LeakageAudit> leakageAudit = new ArrayList<>();

        Map<Integer, List<WalkForwardObservation<P, O>>> observationsByHorizon = new LinkedHashMap<>();
        Map<Integer, Map<String, List<WalkForwardObservation<P, O>>>> foldObservationsByHorizon = new LinkedHashMap<>();
        for (int horizon : config.allHorizons()) {
            observationsByHorizon.put(horizon, new ArrayList<>());
            foldObservationsByHorizon.put(horizon, new LinkedHashMap<>());
        }

        List<WalkForwardRuntimeReport.FoldRuntime> foldRuntimes = new ArrayList<>();
        int progressCount = 0;
        int maxPredictions = config.allTopKs().stream().max(Integer::compareTo).orElse(config.optimizationTopK());

        for (WalkForwardSplit split : splits) {
            long foldStart = System.nanoTime();
            List<WalkForwardRuntimeReport.SnapshotRuntime> snapshotRuntimes = new ArrayList<>();

            for (int decisionIndex = split.testStart(); decisionIndex <= split.testEnd(); decisionIndex++) {
                long snapshotStart = System.nanoTime();
                List<RankedPrediction<P>> rawPredictions = predictionProvider.predict(series, decisionIndex, context);
                List<RankedPrediction<P>> predictions = normalizePredictions(rawPredictions, maxPredictions);

                Map<String, String> metadata = Map.of("visibleStartIndex", String.valueOf(series.getBeginIndex()),
                        "visibleEndIndex", String.valueOf(decisionIndex), "holdout", String.valueOf(split.holdout()));
                PredictionSnapshot<P> snapshot = new PredictionSnapshot<>(split.foldId(), decisionIndex, predictions,
                        metadata);
                snapshots.add(snapshot);

                for (int horizon : config.allHorizons()) {
                    int labelStart = decisionIndex + 1;
                    int labelEnd = decisionIndex + horizon;
                    boolean withinFoldBounds = labelEnd <= split.testEnd() && labelEnd <= series.getEndIndex();
                    String note = withinFoldBounds ? "label window bounded to test fold"
                            : "skipped: label window exceeds fold bounds";

                    WalkForwardRunResult.LeakageAudit audit = new WalkForwardRunResult.LeakageAudit(split.foldId(),
                            decisionIndex, series.getBeginIndex(), decisionIndex, labelStart, labelEnd, horizon,
                            withinFoldBounds, split.holdout(), note);
                    leakageAudit.add(audit);
                    leakageAuditHook.accept(audit);

                    if (!withinFoldBounds) {
                        continue;
                    }

                    List<WalkForwardObservation<P, O>> globalRows = observationsByHorizon.get(horizon);
                    List<WalkForwardObservation<P, O>> foldRows = foldObservationsByHorizon.get(horizon)
                            .computeIfAbsent(split.foldId(), unused -> new ArrayList<>());

                    for (RankedPrediction<P> prediction : predictions) {
                        O outcome = outcomeLabeler.label(series, decisionIndex, horizon, prediction);
                        WalkForwardObservation<P, O> row = new WalkForwardObservation<>(snapshot, prediction, outcome,
                                horizon);
                        globalRows.add(row);
                        foldRows.add(row);
                    }
                }

                progressCount++;
                progressCallback.accept(progressCount);
                snapshotRuntimes.add(new WalkForwardRuntimeReport.SnapshotRuntime(decisionIndex,
                        Duration.ofNanos(System.nanoTime() - snapshotStart), predictions.size()));
            }

            Duration foldRuntime = Duration.ofNanos(System.nanoTime() - foldStart);
            foldRuntimes.add(buildFoldRuntime(split.foldId(), foldRuntime, snapshotRuntimes));
        }

        Map<Integer, Map<String, Num>> globalMetricsByHorizon = computeGlobalMetrics(observationsByHorizon);
        Map<Integer, Map<String, Map<String, Num>>> foldMetricsByHorizon = computeFoldMetrics(
                foldObservationsByHorizon);

        WalkForwardRuntimeReport runtimeReport = buildRuntimeReport(foldRuntimes,
                Duration.ofNanos(System.nanoTime() - overallStart));

        String datasetId = series.getName() == null || series.getName().isBlank() ? "series" : series.getName();
        WalkForwardExperimentManifest manifest = new WalkForwardExperimentManifest(datasetId, candidateId,
                config.configHash(), config.seed(), manifestMetadata);

        return new WalkForwardRunResult<>(config, splits, snapshots, immutableObservationMap(observationsByHorizon),
                immutableMetricMap(globalMetricsByHorizon), immutableFoldMetricMap(foldMetricsByHorizon), leakageAudit,
                runtimeReport, manifest);
    }

    private List<RankedPrediction<P>> normalizePredictions(List<RankedPrediction<P>> predictions, int maxPredictions) {
        if (predictions == null || predictions.isEmpty() || maxPredictions <= 0) {
            return List.of();
        }
        List<RankedPrediction<P>> sorted = new ArrayList<>(predictions);
        sorted.sort(Comparator.comparingInt(RankedPrediction::rank));
        if (sorted.size() > maxPredictions) {
            sorted = new ArrayList<>(sorted.subList(0, maxPredictions));
        }
        return List.copyOf(sorted);
    }

    private Map<Integer, Map<String, Num>> computeGlobalMetrics(
            Map<Integer, List<WalkForwardObservation<P, O>>> observationsByHorizon) {
        Map<Integer, Map<String, Num>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<WalkForwardObservation<P, O>>> entry : observationsByHorizon.entrySet()) {
            Map<String, Num> metricValues = new LinkedHashMap<>();
            for (WalkForwardMetric<P, O> metric : metrics) {
                metricValues.put(metric.name(), metric.compute(entry.getValue()));
            }
            result.put(entry.getKey(), metricValues);
        }
        return result;
    }

    private Map<Integer, Map<String, Map<String, Num>>> computeFoldMetrics(
            Map<Integer, Map<String, List<WalkForwardObservation<P, O>>>> foldObservationsByHorizon) {
        Map<Integer, Map<String, Map<String, Num>>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<String, List<WalkForwardObservation<P, O>>>> horizonEntry : foldObservationsByHorizon
                .entrySet()) {
            Map<String, Map<String, Num>> perFold = new LinkedHashMap<>();
            for (Map.Entry<String, List<WalkForwardObservation<P, O>>> foldEntry : horizonEntry.getValue().entrySet()) {
                Map<String, Num> metricValues = new LinkedHashMap<>();
                for (WalkForwardMetric<P, O> metric : metrics) {
                    metricValues.put(metric.name(), metric.compute(foldEntry.getValue()));
                }
                perFold.put(foldEntry.getKey(), metricValues);
            }
            result.put(horizonEntry.getKey(), perFold);
        }
        return result;
    }

    private WalkForwardRuntimeReport buildRuntimeReport(List<WalkForwardRuntimeReport.FoldRuntime> foldRuntimes,
            Duration overallRuntime) {
        if (foldRuntimes.isEmpty()) {
            return WalkForwardRuntimeReport.empty();
        }

        List<Duration> durations = new ArrayList<>(foldRuntimes.size());
        for (WalkForwardRuntimeReport.FoldRuntime foldRuntime : foldRuntimes) {
            durations.add(foldRuntime.runtime());
        }
        DurationSummary summary = summarizeDurations(durations);
        return new WalkForwardRuntimeReport(overallRuntime, summary.min(), summary.max(), summary.average(),
                summary.median(), foldRuntimes);
    }

    private WalkForwardRuntimeReport.FoldRuntime buildFoldRuntime(String foldId, Duration foldRuntime,
            List<WalkForwardRuntimeReport.SnapshotRuntime> snapshotRuntimes) {
        List<WalkForwardRuntimeReport.SnapshotRuntime> immutableSnapshots = List.copyOf(snapshotRuntimes);
        DurationSummary summary = summarizeDurations(
                immutableSnapshots.stream().map(WalkForwardRuntimeReport.SnapshotRuntime::runtime).toList());
        return new WalkForwardRuntimeReport.FoldRuntime(foldId, foldRuntime, immutableSnapshots.size(), summary.min(),
                summary.max(), summary.average(), summary.median(), immutableSnapshots);
    }

    private DurationSummary summarizeDurations(List<Duration> durations) {
        if (durations == null || durations.isEmpty()) {
            return new DurationSummary(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);
        }
        Duration min = Collections.min(durations);
        Duration max = Collections.max(durations);
        long totalNanos = 0L;
        for (Duration duration : durations) {
            totalNanos += duration.toNanos();
        }
        Duration average = Duration.ofNanos(totalNanos / durations.size());

        List<Duration> sorted = new ArrayList<>(durations);
        sorted.sort(Comparator.naturalOrder());
        Duration median;
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            long medianNanos = (sorted.get(middle - 1).toNanos() + sorted.get(middle).toNanos()) / 2;
            median = Duration.ofNanos(medianNanos);
        } else {
            median = sorted.get(middle);
        }
        return new DurationSummary(min, max, average, median);
    }

    private record DurationSummary(Duration min, Duration max, Duration average, Duration median) {
    }

    private static <P, O> Map<Integer, List<WalkForwardObservation<P, O>>> immutableObservationMap(
            Map<Integer, List<WalkForwardObservation<P, O>>> mutable) {
        Map<Integer, List<WalkForwardObservation<P, O>>> immutable = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<WalkForwardObservation<P, O>>> entry : mutable.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    private static Map<Integer, Map<String, Num>> immutableMetricMap(Map<Integer, Map<String, Num>> mutable) {
        Map<Integer, Map<String, Num>> immutable = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<String, Num>> entry : mutable.entrySet()) {
            immutable.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    private static Map<Integer, Map<String, Map<String, Num>>> immutableFoldMetricMap(
            Map<Integer, Map<String, Map<String, Num>>> mutable) {
        Map<Integer, Map<String, Map<String, Num>>> immutable = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<String, Map<String, Num>>> horizonEntry : mutable.entrySet()) {
            Map<String, Map<String, Num>> perFold = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Num>> foldEntry : horizonEntry.getValue().entrySet()) {
                perFold.put(foldEntry.getKey(), Map.copyOf(foldEntry.getValue()));
            }
            immutable.put(horizonEntry.getKey(), Map.copyOf(perFold));
        }
        return Map.copyOf(immutable);
    }
}
