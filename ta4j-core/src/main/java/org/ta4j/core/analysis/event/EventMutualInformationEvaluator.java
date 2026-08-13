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
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.indicators.IndicatorUtils;
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
 * sample, an empty effective range, or a target-window span too large to
 * represent in memory makes the evaluation undefined (metrics {@code NaN})
 * instead of silently dropping samples or truncating the window.
 * </p>
 *
 * @since 0.24.2
 */
public final class EventMutualInformationEvaluator {

    /**
     * HotSpot's maximum usable {@code int[]} length is a few words below
     * {@link Integer#MAX_VALUE} (observed here: {@code MAX_VALUE - 2}); a span at
     * or above that boundary cannot be represented in memory on any supported JVM
     * and must be reported undefined instead of throwing {@link OutOfMemoryError}.
     * {@code MAX_VALUE - 8} conservatively covers every HotSpot header layout.
     */
    private static final int MAX_PREFIX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    /**
     * Creates an evaluator.
     *
     * @since 0.24.2
     */
    public EventMutualInformationEvaluator() {
    }

    /**
     * Evaluates the predictor against the future Boolean event target.
     *
     * <p>
     * The target is an ordinary {@code Indicator<Boolean>}: only
     * {@link Boolean#TRUE} counts as an event, and the target's own unstable-bar
     * boundary is honored.
     *
     * @param predictor  continuous predictor indicator
     * @param target     sparse Boolean event target indicator
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
     * @since 0.24.2
     */
    public EventMutualInformationResult evaluate(Indicator<Num> predictor, Indicator<Boolean> target, int startIndex,
            int endIndex, EventMutualInformationConfig config) {
        Objects.requireNonNull(predictor, "predictor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        if (startIndex > endIndex) {
            throw new IllegalArgumentException("startIndex must be <= endIndex");
        }
        IndicatorUtils.requireSameSeries(predictor, target);
        BarSeries series = predictor.getBarSeries();
        NumFactory numFactory = series.numFactory();

        if (series.isEmpty()) {
            // An empty series has natural bounds (-1, -1), which pass the
            // availability checks below and would make evaluateInRange read
            // getValue(-1). STRICT keeps rejecting the range (it cannot hold a
            // complete target window); the default CLAMP policy yields the
            // documented undefined result for the empty effective range.
            if (config.historyPolicy() == AnalysisContext.MissingHistoryPolicy.STRICT) {
                throw new IllegalArgumentException(
                        "requested evaluation range includes unavailable history or cannot hold a complete target window");
            }
            return evaluateInRange(predictor, target, numFactory, 0, -1, config);
        }

        // Unstable-bar counts are relative to the series' retained head, so with
        // a discarded history (beginIndex > 0) the first stable index is
        // beginIndex + unstableBars, not unstableBars. The earliest target index
        // a sample reads is i + targetWindowStartBars, so samples may start
        // below the target's own stable boundary as long as their first target
        // index is at or after it. Long arithmetic keeps the sums from wrapping
        // and keeps an above-int-range boundary distinct from a saturated one:
        // when no representable index is stable, the range stays unavailable
        // instead of silently admitting Integer.MAX_VALUE.
        long predictorBoundary = (long) series.getBeginIndex() + predictor.getCountOfUnstableBars();
        long targetBoundary = (long) series.getBeginIndex() + target.getCountOfUnstableBars()
                - (long) config.targetWindowStartBars();
        long availableStartValue = Math.max(predictorBoundary, targetBoundary);
        int availableEnd = series.getEndIndex();
        // Long arithmetic keeps window-offset extremes of the int range from
        // wrapping: endIndex - targetWindowEndBars could otherwise wrap to a
        // huge positive value and admit samples whose target window is empty.
        long maxSampleIndex = (long) endIndex - config.targetWindowEndBars();
        long availableMaxSample = (long) availableEnd - config.targetWindowEndBars();

        if (config.historyPolicy() == AnalysisContext.MissingHistoryPolicy.STRICT) {
            if ((long) startIndex < availableStartValue || endIndex > availableEnd || maxSampleIndex < startIndex) {
                throw new IllegalArgumentException(
                        "requested evaluation range includes unavailable history or cannot hold a complete target window");
            }
            // STRICT guarantees maxSampleIndex >= startIndex >= availableStartValue,
            // so the stable boundary is representable as an int.
            return evaluateInRange(predictor, target, numFactory, startIndex, (int) maxSampleIndex, config);
        }
        long effectiveStartValue = Math.max((long) startIndex, availableStartValue);
        long effectiveEndValue = Math.min(maxSampleIndex, availableMaxSample);
        int effectiveStart = effectiveStartValue > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) effectiveStartValue;
        int effectiveEnd = effectiveEndValue < effectiveStartValue ? -1
                : (int) Math.min(effectiveEndValue, Integer.MAX_VALUE);
        return evaluateInRange(predictor, target, numFactory, effectiveStart, effectiveEnd, config);
    }

    private EventMutualInformationResult evaluateInRange(Indicator<Num> predictor, Indicator<Boolean> target,
            NumFactory numFactory, int effectiveStart, int effectiveEnd, EventMutualInformationConfig config) {
        // eventPrefix is null when the target window span cannot be represented
        // in memory (more than Integer.MAX_VALUE distinct target indexes): such
        // an evaluation is undefined, never silently truncated, and never walks
        // the (astronomically large) effective range.
        int[] eventPrefix = effectiveStart <= effectiveEnd ? eventPrefix(target, effectiveStart, effectiveEnd, config)
                : new int[0];
        boolean infeasibleWindow = effectiveStart <= effectiveEnd && eventPrefix == null;
        List<Num> predictorValues = new ArrayList<>();
        List<Boolean> labels = new ArrayList<>();
        int positiveTargetCount = 0;
        boolean undefined = effectiveStart > effectiveEnd || infeasibleWindow;
        if (effectiveStart <= effectiveEnd && !infeasibleWindow) {
            // The while-true loop form keeps i from wrapping to MIN_VALUE when
            // effectiveEnd is Integer.MAX_VALUE.
            int i = effectiveStart;
            while (true) {
                Num predictorValue = predictor.getValue(i);
                if (!Num.isFinite(predictorValue)) {
                    // Undefined metrics, but the diagnostic counts keep covering
                    // every eligible sample so candidate sample counts cannot
                    // drift invisibly.
                    undefined = true;
                }
                predictorValues.add(predictorValue);
                // The loop is only entered when the target-window span is
                // representable, so the prefix array is always real here.
                boolean positive = targetWindowHasEvent(eventPrefix, i, effectiveStart, config);
                labels.add(positive);
                if (positive) {
                    positiveTargetCount++;
                }
                if (i == effectiveEnd) {
                    break;
                }
                i++;
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

        int[] bins;
        int effectiveBinCount;
        if (config.binningStrategy() == BinningStrategy.EQUAL_FREQUENCY) {
            // One walk decides both the partition and its size, so the reported
            // effectiveBinCount always describes the bins MI is computed over.
            bins = equalFrequencyBins(predictorValues, config.predictorBinCount());
            int maxBin = 0;
            for (int assignedBin : bins) {
                maxBin = Math.max(maxBin, assignedBin);
            }
            effectiveBinCount = maxBin + 1;
        } else {
            Num minimum = predictorValues.get(0);
            Num maximum = predictorValues.get(0);
            for (Num value : predictorValues) {
                minimum = minimum.min(value);
                maximum = maximum.max(value);
            }
            // Equal-width boundaries must honor the requested bin count even
            // when it exceeds the sample count: capping the count before the
            // width is computed would silently coarsen the requested
            // discretization (three samples at 0, 0.1, 1 with 10 bins must land
            // in bins 0, 1, 9, not 0, 0, 2). Storage follows the populated
            // extent (max assigned bin + 1), so trailing empty bins are not
            // allocated.
            // maximum - minimum can overflow to infinity for extreme spans
            // (e.g. samples at both ends of the double range); with a non-finite
            // span the bin width would be NaN and every sample would land in
            // bin 0. Finite extremes are binned in overflow-safe scaled
            // coordinates (affine bin positions are scale invariant), so
            // samples on opposite ends of the double range still separate into
            // their endpoint bins; only genuinely non-finite samples make the
            // evaluation undefined. A zero span (constant predictor) is the
            // opposite extreme: all samples belong to bin 0, giving one
            // effective bin and zero mutual information; computing bin
            // positions directly would divide 0/0 and throw inside
            // {@code intValue()} on the resulting NaN.
            Num span = maximum.minus(minimum);
            if (span.isZero()) {
                bins = new int[sampleCount];
            } else if (!Num.isFinite(span)) {
                if (Num.isFinite(minimum) && Num.isFinite(maximum)) {
                    bins = equalWidthBinsScaled(predictorValues, minimum, maximum, config.predictorBinCount());
                } else {
                    return new EventMutualInformationResult(NaN.NaN, NaN.NaN, NaN.NaN, sampleCount, positiveTargetCount,
                            positiveTargetRate, config.predictorBinCount(), 0, config.binningStrategy(),
                            config.targetWindowStartBars(), config.targetWindowEndBars());
                }
            } else {
                bins = equalWidthBins(predictorValues, minimum, span, config.predictorBinCount());
            }
            int maxBin = 0;
            for (int assignedBin : bins) {
                maxBin = Math.max(maxBin, assignedBin);
            }
            effectiveBinCount = maxBin + 1;
        }
        if (positiveTargetCount == 0 || positiveTargetCount == sampleCount) {
            // Constant target: no uncertainty to explain, so raw MI is zero and
            // normalized MI is undefined; a no-event target must never become a
            // perfect optimizer score.
            return new EventMutualInformationResult(numFactory.zero(), numFactory.zero(), NaN.NaN, sampleCount,
                    positiveTargetCount, positiveTargetRate, config.predictorBinCount(), effectiveBinCount,
                    config.binningStrategy(), config.targetWindowStartBars(), config.targetWindowEndBars());
        }

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

        // An exactly independent contingency table can sum to a tiny negative
        // value through rounding alone (for example -1.7e-16 with DoubleNum and
        // bins of (1, 3) and (4, 12) counts); mutual information is
        // non-negative by definition, so normalize roundoff-scale negatives to
        // zero before the result constructor validates the metrics.
        if (mutualInformation.isNegative() && mutualInformation.abs().compareTo(numFactory.epsilon()) <= 0) {
            mutualInformation = numFactory.zero();
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

    /**
     * @return the target event prefix array for the effective sample range, or
     *         {@code null} when the covered target window span cannot be
     *         represented in memory
     */
    private static int[] eventPrefix(Indicator<Boolean> target, int effectiveStart, int effectiveEnd,
            EventMutualInformationConfig config) {
        long targetStart = (long) effectiveStart + config.targetWindowStartBars();
        long targetEnd = (long) effectiveEnd + config.targetWindowEndBars();
        long span = targetEnd - targetStart + 2L;
        if (span > MAX_PREFIX_ARRAY_LENGTH) {
            // A span of exactly Integer.MAX_VALUE would pass an
            // `> Integer.MAX_VALUE` check and then fail with "Requested array
            // size exceeds VM limit"; treat every span at or above the JVM's
            // usable int[] ceiling as unrepresentable.
            return null;
        }
        int[] prefix = new int[(int) span];
        long j = targetStart;
        int k = 1;
        while (true) {
            prefix[k] = prefix[k - 1] + (Boolean.TRUE.equals(target.getValue((int) j)) ? 1 : 0);
            if (j == targetEnd) {
                break;
            }
            j++;
            k++;
        }
        return prefix;
    }

    private static boolean targetWindowHasEvent(int[] eventPrefix, int sampleIndex, int effectiveStart,
            EventMutualInformationConfig config) {
        int offset = sampleIndex - effectiveStart;
        // prefix[k] counts events in [targetStart, targetStart + k), so the
        // window [i + start, i + end] maps to (offset, offset + end - start].
        // The index arithmetic cannot overflow here: eventPrefix is only built
        // when the whole target window span fits in an int array, and
        // offset + windowLength + 1 <= span - 1.
        return eventPrefix[offset + config.targetWindowEndBars() - config.targetWindowStartBars() + 1]
                - eventPrefix[offset] > 0;
    }

    private static int[] equalFrequencyBins(List<Num> values, int requestedBinCount) {
        int sampleCount = values.size();
        Integer[] order = new Integer[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (left, right) -> compareCanonical(values.get(left), values.get(right)));
        int[] bins = new int[sampleCount];
        int bin = 0;
        int index = 0;
        while (index < sampleCount) {
            // Size each bin from the remaining samples and remaining requested
            // bins, so a non-divisible sample count still forms every requested
            // bin (5 distinct samples with 4 bins -> sizes 2,1,1,1) instead of
            // collapsing the tail into fewer bins.
            int remaining = sampleCount - index;
            int remainingBins = requestedBinCount - bin;
            int end = index + (int) ((remaining + (long) remainingBins - 1L) / remainingBins);
            while (end < sampleCount && compareCanonical(values.get(order[end]), values.get(order[end - 1])) == 0) {
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

    /**
     * Compares two predictor values for equal-frequency binning.
     * {@link Num#compareTo} may treat numerically equal values as distinct
     * (DoubleNum's {@code Double.compare} ranks {@code -0.0} below {@code +0.0}),
     * which would split a run of identical zeros across bins; canonicalizing zeros
     * keeps such ties in one bin.
     */
    private static int compareCanonical(Num left, Num right) {
        if (left.isZero() && right.isZero()) {
            return 0;
        }
        return left.compareTo(right);
    }

    private static int[] equalWidthBins(List<Num> values, Num minimum, Num span, int binCount) {
        int sampleCount = values.size();
        if (binCount == 1) {
            return new int[sampleCount];
        }
        // span is finite (validated by the caller), so every value delta is
        // finite: correctly-rounded subtraction of finite doubles within
        // [minimum, minimum + span] cannot overflow past the double range.
        // Bin positions are computed as delta / span * binCount rather than
        // delta / (span / binCount): for a positive subnormal span the
        // rounded width is inaccurate (for example 5 * Double.MIN_VALUE / 4
        // rounds from 1.25 * Double.MIN_VALUE to Double.MIN_VALUE, shifting
        // the last samples into the wrong bins), and for a large bin count
        // the width can underflow to zero entirely, which would divide by
        // zero and throw inside {@code intValue()} on the resulting NaN. The
        // ratio form is well conditioned in both cases: delta / span is in
        // (0, 1] and binCount is an int, so the product can neither underflow
        // nor overflow.
        int[] bins = new int[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            Num position = values.get(i).minus(minimum);
            position = position.dividedBy(span).multipliedBy(minimum.getNumFactory().numOf(binCount));
            int bin = position.intValue();
            if (bin < 0) {
                bin = 0;
            }
            if (bin >= binCount) {
                bin = binCount - 1;
            }
            bins[i] = bin;
        }
        return bins;
    }

    /**
     * Equal-width bins computed in overflow-safe scaled coordinates.
     *
     * <p>
     * Used when the raw span {@code maximum - minimum} overflows to infinity in
     * primitive arithmetic even though both extremes are finite (for example
     * samples at {@code -Double.MAX_VALUE} and {@code Double.MAX_VALUE}). Affine
     * bin positions {@code (value - minimum) / (maximum - minimum)} are invariant
     * under a common nonzero scale, so every value is first multiplied by the same
     * power of two that maps the larger absolute extreme into {@code [1, 2)}: the
     * scaled span is then representable and the positions can be computed exactly
     * as in {@link #equalWidthBins}, including the same ratio form and clamping.
     * </p>
     */
    private static int[] equalWidthBinsScaled(List<Num> values, Num minimum, Num maximum, int binCount) {
        int sampleCount = values.size();
        if (binCount == 1) {
            return new int[sampleCount];
        }
        double maxAbs = Math.max(Math.abs(minimum.doubleValue()), Math.abs(maximum.doubleValue()));
        double scale = Math.scalb(1.0, -Math.getExponent(maxAbs));
        NumFactory numFactory = minimum.getNumFactory();
        Num scaleNum = numFactory.numOf(scale);
        Num scaledMinimum = minimum.multipliedBy(scaleNum);
        Num scaledSpan = maximum.multipliedBy(scaleNum).minus(scaledMinimum);
        int[] bins = new int[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            Num position = values.get(i).multipliedBy(scaleNum).minus(scaledMinimum);
            position = position.dividedBy(scaledSpan).multipliedBy(numFactory.numOf(binCount));
            int bin = position.intValue();
            if (bin < 0) {
                bin = 0;
            }
            if (bin >= binCount) {
                bin = binCount - 1;
            }
            bins[i] = bin;
        }
        return bins;
    }

}
