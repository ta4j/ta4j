/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.HistoryPolicy;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Evaluates mutual information between a continuous predictor indicator and a
 * sparse Boolean event target over an explicit current/future bar window.
 *
 * <p>
 * A predictor sample at index {@code i} is labeled positive when at least one
 * target event occurs in
 * {@code [i + targetWindowStartBars, i + targetWindowEndBars]}. Every target
 * index must lie inside the supplied analysis partition: samples whose target
 * window would cross {@code endIndex} (a validation boundary) are excluded, so
 * a training evaluation can never consume an event that belongs to a later
 * validation partition.
 * </p>
 *
 * <p>
 * Results are reported in natural logarithms (nats), together with target
 * entropy, normalized MI, event prevalence, and bin diagnostics.
 * Equal-frequency binning never splits identical predictor values, so the
 * effective bin count may be smaller than requested. A non-finite predictor
 * sample or an empty effective range makes the evaluation undefined (metrics
 * {@code NaN}) instead of silently dropping samples.
 * </p>
 *
 * @since 0.24.1
 */
public final class EventMutualInformationEvaluator {

    /**
     * Creates an evaluator.
     */
    public EventMutualInformationEvaluator() {
    }

    /**
     * Evaluates the predictor against the future Boolean event target.
     *
     * @param predictor  continuous predictor indicator
     * @param target     sparse Boolean event target
     * @param startIndex inclusive start of the analysis partition
     * @param endIndex   inclusive end of the analysis partition; target windows
     *                   never cross it
     * @param config     target window, binning, and history policy
     * @return the immutable evaluation result
     * @throws IllegalArgumentException if {@code startIndex > endIndex},
     *                                  {@code startIndex} is below the available
     *                                  history or {@code endIndex} beyond the
     *                                  series under {@code STRICT} history, the
     *                                  partition cannot hold a single complete
     *                                  target window under {@code STRICT}, or the
     *                                  predictor and target use different series
     * @throws NullPointerException     if an argument is null
     */
    public EventMutualInformationResult evaluate(Indicator<Num> predictor, EventSignal target, int startIndex,
            int endIndex, EventMutualInformationConfig config) {
        Objects.requireNonNull(predictor, "predictor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        if (startIndex > endIndex) {
            throw new IllegalArgumentException("startIndex must be <= endIndex");
        }
        requireSameSeries(predictor.getBarSeries(), target.getBarSeries());
        BarSeries series = predictor.getBarSeries();
        NumFactory numFactory = series.numFactory();

        int availableStart = Math.max(series.getBeginIndex(), predictor.getCountOfUnstableBars());
        availableStart = Math.max(availableStart, target.getCountOfUnstableBars());
        int availableEnd = series.getEndIndex();
        int maxSampleIndex = endIndex - config.targetWindowEndBars();
        int availableMaxSample = availableEnd - config.targetWindowEndBars();

        int effectiveStart = startIndex;
        int effectiveEnd = maxSampleIndex;
        if (config.historyPolicy() == HistoryPolicy.STRICT) {
            if (startIndex < availableStart || endIndex > availableEnd || maxSampleIndex < startIndex) {
                throw new IllegalArgumentException(
                        "requested evaluation range includes unavailable history or cannot hold a complete target window");
            }
        } else {
            effectiveStart = Math.max(effectiveStart, availableStart);
            effectiveEnd = Math.min(effectiveEnd, availableMaxSample);
        }

        List<Num> predictorValues = new ArrayList<>();
        List<Boolean> labels = new ArrayList<>();
        int positiveTargetCount = 0;
        boolean undefined = effectiveStart > effectiveEnd;
        for (int i = effectiveStart; !undefined && i <= effectiveEnd; i++) {
            Num predictorValue = predictor.getValue(i);
            if (!isFinite(predictorValue)) {
                undefined = true;
                break;
            }
            predictorValues.add(predictorValue);
            boolean positive = targetWindowHasEvent(target, i, config);
            labels.add(positive);
            if (positive) {
                positiveTargetCount++;
            }
        }

        int sampleCount = predictorValues.size();
        Num positiveTargetRate = sampleCount == 0 ? NaN.NaN
                : numFactory.numOf(positiveTargetCount).dividedBy(numFactory.numOf(sampleCount));
        if (undefined || sampleCount == 0) {
            return new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN, sampleCount, positiveTargetCount,
                    positiveTargetRate, config.predictorBinCount(), 0, config.binningStrategy(),
                    config.targetWindowStartBars(), config.targetWindowEndBars());
        }

        int effectiveBinCount = binCount(predictorValues, config);
        if (positiveTargetCount == 0 || positiveTargetCount == sampleCount) {
            // Constant target: no uncertainty to explain, so raw MI is zero and
            // normalized MI is undefined; a no-event target must never become a
            // perfect optimizer score.
            return new EventMutualInformationResult(numFactory.zero(), numFactory.zero(), NaN.NaN, sampleCount,
                    positiveTargetCount, positiveTargetRate, config.predictorBinCount(), effectiveBinCount,
                    config.binningStrategy(), config.targetWindowStartBars(), config.targetWindowEndBars());
        }

        int[] bins = binSamples(predictorValues, config, effectiveBinCount);
        int[] jointCounts = new int[effectiveBinCount * 2];
        int[] predictorCounts = new int[effectiveBinCount];
        for (int k = 0; k < sampleCount; k++) {
            int label = labels.get(k) ? 1 : 0;
            jointCounts[(bins[k] * 2) + label]++;
            predictorCounts[bins[k]]++;
        }

        Num sampleCountNum = numFactory.numOf(sampleCount);
        Num mutualInformation = numFactory.zero();
        for (int bin = 0; bin < effectiveBinCount; bin++) {
            for (int label = 0; label < 2; label++) {
                int jointCount = jointCounts[(bin * 2) + label];
                if (jointCount == 0) {
                    continue;
                }
                Num jointProbability = numFactory.numOf(jointCount).dividedBy(sampleCountNum);
                Num predictorProbability = numFactory.numOf(predictorCounts[bin]).dividedBy(sampleCountNum);
                Num targetProbability = numFactory
                        .numOf(label == 1 ? positiveTargetCount : sampleCount - positiveTargetCount)
                        .dividedBy(sampleCountNum);
                Num ratio = jointProbability.dividedBy(predictorProbability.multipliedBy(targetProbability));
                mutualInformation = mutualInformation.plus(jointProbability.multipliedBy(ratio.log()));
            }
        }

        Num positiveProbability = numFactory.numOf(positiveTargetCount).dividedBy(sampleCountNum);
        Num negativeProbability = numFactory.one().minus(positiveProbability);
        Num targetEntropy = positiveProbability.multipliedBy(positiveProbability.log())
                .plus(negativeProbability.multipliedBy(negativeProbability.log()))
                .negate();
        Num normalized = targetEntropy.isPositive() ? mutualInformation.dividedBy(targetEntropy) : NaN.NaN;
        return new EventMutualInformationResult(mutualInformation, targetEntropy, normalized, sampleCount,
                positiveTargetCount, positiveTargetRate, config.predictorBinCount(), effectiveBinCount,
                config.binningStrategy(), config.targetWindowStartBars(), config.targetWindowEndBars());
    }

    private static boolean targetWindowHasEvent(EventSignal target, int sampleIndex,
            EventMutualInformationConfig config) {
        for (int index = sampleIndex + config.targetWindowStartBars(); index <= sampleIndex
                + config.targetWindowEndBars(); index++) {
            if (target.isEvent(index)) {
                return true;
            }
        }
        return false;
    }

    private static int binCount(List<Num> values, EventMutualInformationConfig config) {
        if (config.binningStrategy() == BinningStrategy.EQUAL_FREQUENCY) {
            return equalFrequencyBinCount(values, config.predictorBinCount());
        }
        Num minimum = values.get(0);
        Num maximum = values.get(0);
        for (Num value : values) {
            minimum = minimum.min(value);
            maximum = maximum.max(value);
        }
        return minimum.compareTo(maximum) == 0 ? 1 : config.predictorBinCount();
    }

    private static int[] binSamples(List<Num> values, EventMutualInformationConfig config, int effectiveBinCount) {
        if (config.binningStrategy() == BinningStrategy.EQUAL_FREQUENCY) {
            return equalFrequencyBins(values, effectiveBinCount);
        }
        return equalWidthBins(values, effectiveBinCount);
    }

    private static int equalFrequencyBinCount(List<Num> values, int requestedBinCount) {
        int sampleCount = values.size();
        Num[] sorted = values.toArray(new Num[0]);
        Arrays.sort(sorted, Num::compareTo);
        long desired = (sampleCount + (long) requestedBinCount - 1L) / requestedBinCount;
        int bin = 0;
        int index = 0;
        while (index < sampleCount) {
            int end = (int) Math.min(sampleCount, index + desired);
            while (end < sampleCount && sorted[end].compareTo(sorted[end - 1]) == 0) {
                end++;
            }
            bin++;
            index = end;
        }
        return bin;
    }

    private static int[] equalFrequencyBins(List<Num> values, int effectiveBinCount) {
        int sampleCount = values.size();
        Integer[] order = new Integer[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (left, right) -> values.get(left).compareTo(values.get(right)));
        long desired = (sampleCount + (long) effectiveBinCount - 1L) / effectiveBinCount;
        int[] bins = new int[sampleCount];
        int bin = 0;
        int index = 0;
        while (index < sampleCount) {
            int end = (int) Math.min(sampleCount, index + desired);
            while (end < sampleCount && values.get(order[end]).compareTo(values.get(order[end - 1])) == 0) {
                end++;
            }
            for (int k = index; k < end; k++) {
                bins[order[k]] = bin;
            }
            bin++;
            index = end;
        }
        return bins;
    }

    private static int[] equalWidthBins(List<Num> values, int effectiveBinCount) {
        int sampleCount = values.size();
        if (effectiveBinCount == 1) {
            return new int[sampleCount];
        }
        Num minimum = values.get(0);
        Num maximum = values.get(0);
        for (Num value : values) {
            minimum = minimum.min(value);
            maximum = maximum.max(value);
        }
        Num width = maximum.minus(minimum).dividedBy(minimum.getNumFactory().numOf(effectiveBinCount));
        int[] bins = new int[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int bin = values.get(i).minus(minimum).dividedBy(width).intValue();
            if (bin < 0) {
                bin = 0;
            }
            if (bin >= effectiveBinCount) {
                bin = effectiveBinCount - 1;
            }
            bins[i] = bin;
        }
        return bins;
    }

    private static void requireSameSeries(BarSeries first, BarSeries second) {
        if (!(first.equals(second) || second.equals(first))) {
            throw new IllegalArgumentException("predictor and target must use the same bar series");
        }
    }

    private static boolean isFinite(Num value) {
        if (value == null || value.isNaN()) {
            return false;
        }
        Number delegate = value.getDelegate();
        if (delegate instanceof Double primitive) {
            return Double.isFinite(primitive);
        }
        if (delegate instanceof Float primitive) {
            return Float.isFinite(primitive);
        }
        return true;
    }
}
