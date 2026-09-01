/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Objects;
import java.math.BigDecimal;

/**
 * Exponentially weighted moving variance (EWMA variance).
 *
 * <p>
 * Tracks the current variance of the monitored indicator with
 *
 * <pre>
 * sigma^2_t = decayFactor * sigma^2_{t-1} + (1 - decayFactor) * (X_t - mu_{t-1})^2
 * </pre>
 *
 * where {@code mu} is an EWMA mean recursion over the same {@code barCount} and
 * the same exact decay complement (so both legs always share identical decay
 * weights), and the recursion is seeded with the seed window's population
 * variance. The seed is computed from a compensated window mean and per-term
 * scaled squared deviations, so a seed whose naive sum-of-squares would
 * overflow the numeric representation (for example a window whose population
 * variance is representable but contains a bar large enough to overflow its own
 * square) still publishes a finite value. The seed also re-anchors the
 * recursion whenever the previous state is non-finite: when only the variance
 * leg has collapsed, it re-anchors on the seed window measured around the
 * retained EWMA mean at the previous index, so the published mean and variance
 * keep describing one estimator.
 *
 * <p>
 * The value is {@code NaN} while warming up, with
 * {@code getCountOfUnstableBars() = indicator.getCountOfUnstableBars() +
 * barCount - 1}, and non-finite inputs or extreme regime changes whose derived
 * deviation overflows the numeric representation yield {@code NaN} until the
 * next finite bar re-seeds the variance. The weighted squared deviation
 * {@code (1 - decayFactor) * deviation^2} is accumulated by scaling one
 * deviation factor by the complement weight before completing the product, so a
 * deviation whose square overflows still yields a finite weighted contribution.
 * A seed window whose own population variance is non-finite publishes
 * {@code NaN} rather than a non-finite seed, so a composed kill switch fails
 * open until a finite window is available. Smaller decay factors react faster
 * to volatility changes at the cost of a noisier estimate.
 *
 * <p>
 * When the backing series prunes its retained head (for example through
 * {@link org.ta4j.core.BarSeries#setMaximumBarCount(int)}), cached values
 * computed against the discarded prefix are dropped, the control mean estimator
 * is rebuilt with the configured window so that its seed at the re-anchoring
 * index is the configured EWMA seed (the mean of the retained seed window), and
 * the recursion re-anchors with the first full seed window available at the
 * retained head; indices before that window, and any index still inside the
 * source's own warm-up, publish {@code NaN} so no future bar or source-unstable
 * bar leaks into a historical value. Reads bracket the removal count across the
 * cached read and repeat until it is stable, so a concurrently pruning series
 * can never publish a value computed against the discarded prefix.
 *
 * <p>
 * Combined with {@link CusumIndicator} this provides the volatility leg of a
 * statistical control-limit kill switch, e.g. the control limit
 * {@code H = NumericIndicator.of(ewmaVariance).sqrt().multipliedBy(h)} with
 * {@code h} around four or five.
 *
 * @since 0.24.2
 */
public class EwmaVarianceIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final double decayFactor;
    private volatile transient SharedMeanIndicator meanIndicator;
    private final transient Num decay;
    private final transient Num oneMinusDecay;
    private volatile transient int observedRemovedBarsCount = getBarSeries().getRemovedBarsCount();
    private volatile transient int reseedIndex = -1;

    /**
     * Creates a close-price EWMA variance indicator.
     *
     * @param series      bar series
     * @param barCount    the seed window length; must be >= 1
     * @param decayFactor the EWMA decay factor; must be in (0, 1)
     */
    public EwmaVarianceIndicator(BarSeries series, int barCount, double decayFactor) {
        this(validateParameters(closePrice(series), barCount, decayFactor));
    }

    private static ClosePriceIndicator closePrice(BarSeries series) {
        return new ClosePriceIndicator(Objects.requireNonNull(series, "series must not be null"));
    }

    /**
     * Constructor.
     *
     * @param indicator   the indicator whose variance is monitored
     * @param barCount    the seed window length; must be >= 1
     * @param decayFactor the EWMA decay factor; must be in (0, 1)
     */
    public EwmaVarianceIndicator(Indicator<Num> indicator, int barCount, double decayFactor) {
        this(validateParameters(indicator, barCount, decayFactor));
    }

    private EwmaVarianceIndicator(Parameters parameters) {
        super(parameters.indicator());

        this.indicator = parameters.indicator();
        this.barCount = parameters.barCount();
        this.decayFactor = parameters.decayFactor();
        // The complement is the exact BigDecimal difference 1 - rawDecay and
        // the decay is derived from it, mirroring CusumIndicator's scale-decay
        // conversion: computing the complement in primitive double first
        // injects the binary rounding artifact (1d - 0.06), and deriving it
        // from the already rounded decay collapses to zero where the decay
        // rounds to one (DecimalNumFactory precision 1 rounds 0.9999 to 1),
        // so the exact subtraction keeps decay plus complement summing to
        // exactly one under every factory precision. The mean recursion
        // consumes the same complement, so the mean and variance legs always
        // apply identical decay weights.
        BigDecimal rawDecay = BigDecimal.valueOf(decayFactor);
        this.oneMinusDecay = indicator.getBarSeries().numFactory().numOf(BigDecimal.ONE.subtract(rawDecay));
        this.decay = indicator.getBarSeries().numFactory().one().minus(this.oneMinusDecay);
        this.meanIndicator = new SharedMeanIndicator(indicator, barCount, decay, oneMinusDecay);
    }

    private static Parameters validateParameters(Indicator<Num> indicator, int barCount, double decayFactor) {
        Objects.requireNonNull(indicator, "indicator must not be null");
        if (indicator.getBarSeries() == null) {
            throw new IllegalArgumentException("indicator must be bound to a BarSeries");
        }
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be >= 1");
        }
        if (Double.isNaN(decayFactor) || decayFactor <= 0 || decayFactor >= 1) {
            throw new IllegalArgumentException("decayFactor must be in (0, 1)");
        }

        int unstableBars = indicator.getCountOfUnstableBars();
        if (barCount - 1 > Integer.MAX_VALUE - unstableBars) {
            throw new IllegalArgumentException("barCount must not overflow the unstable-bar count");
        }
        return new Parameters(indicator, barCount, decayFactor);
    }

    private record Parameters(Indicator<Num> indicator, int barCount, double decayFactor) {
    }

    @Override
    public Num getValue(int index) {
        BarSeries series = getBarSeries();
        while (true) {
            int removedBarsCount = series.getRemovedBarsCount();
            if (removedBarsCount != observedRemovedBarsCount) {
                resetForRetainedHead(removedBarsCount);
            }
            Num value = super.getValue(index);
            // Validate against the locally captured snapshot, not the shared
            // observed count: another reader may have re-anchored after this
            // read, and the shared field would then match the series while
            // this value still belongs to the discarded prefix.
            if (series.getRemovedBarsCount() == removedBarsCount) {
                return value;
            }
            // A prune raced the cached read, so the value may still be
            // computed against the discarded prefix: reset and read again
            // until a full read completes against a stable removal count. The
            // cached read is cheap once re-anchored, so this settles as soon
            // as the series stops pruning concurrently.
        }
    }

    private synchronized void resetForRetainedHead(int removedBarsCount) {
        if (removedBarsCount != observedRemovedBarsCount) {
            // Invalidate first, publish last: a concurrent reader that
            // observes the new count must never see caches still computed
            // from the discarded prefix.
            this.meanIndicator = new SharedMeanIndicator(indicator, barCount, decay, oneMinusDecay);
            reseedIndex = (int) Math.min((long) getBarSeries().getBeginIndex() + barCount - 1L, Integer.MAX_VALUE);
            invalidateCache();
            observedRemovedBarsCount = removedBarsCount;
        }
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (index == 0 && beginIndex > 0) {
            // Removed-index reads map to the synthetic zero; anchor at the
            // retained head like CusumIndicator so the warm-up guard below
            // treats it as the first retained bar instead of the pruned 0.
            index = beginIndex;
        }
        int unstableBars = getCountOfUnstableBars();
        if (index == reseedIndex && index - beginIndex >= unstableBars) {
            // The full seed window [index - barCount + 1, index] now lies
            // within the retained bars and the source has completed its own
            // warm-up: re-anchor with the window's population variance
            // instead of seeding from a window that reaches into future or
            // source-unstable bars. If the source is still warming up at
            // reseedIndex, the warm-up guard below keeps the value NaN and
            // the first source-stable index re-seeds through the
            // non-finite-previous path.
            Num seedVariance = rollingWindowVariance(index);
            return Num.isFinite(seedVariance) ? seedVariance : NaN.NaN;
        }
        if (index - beginIndex < unstableBars) {
            return NaN.NaN;
        }
        if (index == beginIndex) {
            Num current = indicator.getValue(index);
            if (!Num.isFinite(current)) {
                return NaN.NaN;
            }
            // Only reachable when barCount == 1 (any larger window still has
            // unstable bars at beginIndex), so the single-bar window scores
            // zero variance around the bar itself.
            return rollingWindowVariance(index);
        }
        Num current = indicator.getValue(index);
        if (!Num.isFinite(current)) {
            return NaN.NaN;
        }
        Num previousVariance = getValue(index - 1);
        Num previousMean = meanIndicator.getValue(index - 1);
        if (!Num.isFinite(previousVariance)) {
            if (!Num.isFinite(previousMean)) {
                // Both legs of the recursion collapsed: re-anchor on the
                // rolling population variance of the seed window.
                Num seedVariance = rollingWindowVariance(index);
                return Num.isFinite(seedVariance) ? seedVariance : NaN.NaN;
            }
            // Only the variance leg collapsed. Re-anchor it on the seed
            // window, measured around the retained EWMA mean at the previous
            // index (the level this recursion deviates from), so the
            // published mean and variance keep describing one estimator. The
            // rolling population variance would be measured around the window
            // mean instead and can contradict the retained mean (a window of
            // equal bars scores zero variance while the retained mean still
            // differs from every window bar).
            Num recoveredVariance = windowVarianceAround(index, previousMean);
            return Num.isFinite(recoveredVariance) ? recoveredVariance : NaN.NaN;
        }
        Num deviation = current.minus(previousMean);
        // Scale one deviation factor by the complement weight before
        // completing the product: deviation * deviation can overflow even
        // when the weighted contribution oneMinusDecay * deviation^2 is
        // representable (a deviation near sqrt(MAX) against a decay near
        // one), so this multiplication order keeps finite EWMA variances
        // available to control-limit consumers.
        Num updatedVariance = previousVariance.multipliedBy(decay)
                .plus(deviation.multipliedBy(deviation.multipliedBy(oneMinusDecay)));
        if (updatedVariance.isZero() && (!previousVariance.isZero() || !deviation.isZero())) {
            // Both nonnegative terms rounded to zero although their exact
            // sum is representable (a subnormal variance carried by two
            // half-subnormal contributions): recombine the exact binary
            // products without intermediate rounding and narrow once.
            BigDecimal recovered = new BigDecimal(previousVariance.doubleValue())
                    .multiply(new BigDecimal(decay.doubleValue()))
                    .add(new BigDecimal(deviation.doubleValue()).multiply(new BigDecimal(deviation.doubleValue()))
                            .multiply(new BigDecimal(oneMinusDecay.doubleValue())));
            updatedVariance = getBarSeries().numFactory().numOf(recovered);
        }
        return Num.isFinite(deviation) && Num.isFinite(updatedVariance) ? updatedVariance : NaN.NaN;
    }

    private Num windowVarianceAround(int index, Num center) {
        int windowBegin = Math.max(index - barCount + 1, getBarSeries().getBeginIndex());
        NumFactory factory = getBarSeries().numFactory();
        Num barCountNum = factory.numOf(barCount);
        // Accumulate squared deviations under one shared scale so no individual
        // term can overflow or underflow: every bar contributes
        // (deviation / scale)^2, each at most one, and the population variance
        // is scale^2 * sumOfSquares / barCount. The scale keeps each quotient
        // within the representable range while the running sum re-scales by the
        // old-to-new scale ratio whenever a larger deviation arrives. Dividing
        // the summed quotients by the window size keeps every factor inside the
        // representation range: the shared scale keeps the summed terms from
        // underflowing (deviations of 2^-537 average to the subnormal 2^-1074
        // even though each per-term quotient rounds to zero), and multiplying
        // the normalized sum between the two scale factors avoids squaring the
        // scale as an intermediate, which can overflow even when the averaged
        // variance is finite (a scale of 1.9e154 squares to 3.5e308 for
        // DoubleNum while the variance 1.7e308 is representable).
        Num scale = factory.zero();
        Num sumOfSquares = factory.zero();
        for (int windowIndex = windowBegin; windowIndex <= index; windowIndex++) {
            Num deviation = indicator.getValue(windowIndex).minus(center);
            Num magnitude = deviation.abs();
            if (magnitude.isZero()) {
                continue;
            }
            if (scale.isLessThan(magnitude)) {
                // Largest deviation so far: fold the running sum into the new
                // scale before adopting it. The old-to-new ratio is at most
                // one, so the re-scaled sum cannot overflow.
                Num ratio = scale.dividedBy(magnitude);
                sumOfSquares = factory.one().plus(sumOfSquares.multipliedBy(ratio.multipliedBy(ratio)));
                scale = magnitude;
            } else {
                Num ratio = magnitude.dividedBy(scale);
                sumOfSquares = sumOfSquares.plus(ratio.multipliedBy(ratio));
            }
        }
        Num normalizedSum = sumOfSquares.dividedBy(barCountNum);
        return scale.multipliedBy(normalizedSum).multipliedBy(scale);
    }

    /**
     * Population variance of the seed window {@code [index - barCount + 1,
     * index]}, measured around the compensated window mean and accumulated under
     * one shared scale. The naive sum-of-squares form first squares each bar and
     * can overflow the numeric representation even when the averaged variance is
     * representable (a window containing {@code 2e154} squares to {@code 4e308} for
     * {@code DoubleNum}, while its population variance {@code 8e308/9} is finite),
     * and dividing each term first can underflow every contribution even when the
     * averaged variance is representable (deviations of {@code 2^-537} average to
     * the subnormal {@code 2^-1074}), so the mean is accumulated with compensated
     * summation and the squared deviations are accumulated against a shared scale
     * before the window size divides their sum; the normalized sum multiplies
     * between the two scale factors so no intermediate squares an already
     * overflowing scale.
     */
    private Num rollingWindowVariance(int index) {
        Num scaledMean = windowMean(indicator, barCount, index);
        if (!Num.isFinite(scaledMean)) {
            return NaN.NaN;
        }
        return windowVarianceAround(index, scaledMean);
    }

    /**
     * Mean of the window {@code [index - barCount + 1, index]}, accumulated with
     * compensated (Neumaier) summation and re-scaled by the largest absolute bar
     * when the compensated sum overflows. A window of bars near the representation
     * ceiling (three {@code Double.MAX_VALUE} bars) sums to a non-finite running
     * total even though the exact mean is representable, so the re-scaled pass
     * divides every bar by the largest absolute bar, sums the normalized values
     * (each bounded by one), and multiplies back, which keeps the mean finite and
     * exact for equal bars. Non-finite bars publish {@code NaN}.
     */
    private static Num windowMean(Indicator<Num> source, int windowBarCount, int index) {
        BarSeries series = source.getBarSeries();
        int windowBegin = Math.max(index - windowBarCount + 1, series.getBeginIndex());
        NumFactory factory = series.numFactory();
        int windowLength = index - windowBegin + 1;
        Num[] windowValues = new Num[windowLength];
        for (int windowIndex = windowBegin; windowIndex <= index; windowIndex++) {
            Num value = source.getValue(windowIndex);
            if (!Num.isFinite(value)) {
                return NaN.NaN;
            }
            windowValues[windowIndex - windowBegin] = value;
        }
        Num mean = compensatedSum(windowValues, factory).dividedBy(factory.numOf(windowLength));
        if (Num.isFinite(mean)) {
            return mean;
        }
        // The exact window mean is representable (it is bounded by the
        // largest absolute bar value), so a non-finite compensated sum is a
        // summation artifact: re-scale every bar by the largest absolute bar
        // and accumulate the normalized values instead. The values are
        // validated finite above, so the scale is positive and finite.
        Num maxAbsValue = factory.zero();
        for (Num value : windowValues) {
            maxAbsValue = maxAbsValue.max(value.abs());
        }
        Num[] scaledValues = new Num[windowLength];
        for (int i = 0; i < windowLength; i++) {
            scaledValues[i] = windowValues[i].dividedBy(maxAbsValue);
        }
        return compensatedSum(scaledValues, factory).dividedBy(factory.numOf(windowLength)).multipliedBy(maxAbsValue);
    }

    /**
     * Neumaier compensated summation: each step carries the rounding residue of the
     * previous step forward, so the sum is order-stable even when large
     * opposite-sign values cancel catastrophically in a naive accumulation.
     */
    private static Num compensatedSum(Num[] values, NumFactory factory) {
        Num sum = factory.zero();
        Num compensation = factory.zero();
        for (Num value : values) {
            Num next = sum.plus(value);
            if (sum.abs().isGreaterThanOrEqual(value.abs())) {
                compensation = compensation.plus(sum.minus(next).plus(value));
            } else {
                compensation = compensation.plus(value.minus(next).plus(sum));
            }
            sum = next;
        }
        return sum.plus(compensation);
    }

    /**
     * Returns the EWMA mean estimator this variance is computed around.
     *
     * <p>
     * The estimator is the same recursion {@link #getValue(int)} deviates from, so
     * composing the mean and the variance from this single estimator keeps both
     * moments consistent. The accessor observes prune events on the backing series
     * and re-anchors the estimator before returning it, so a consumer that reads
     * the mean ahead of {@link #getValue(int)} still pairs it with the variance of
     * the retained head; consumers must re-read the estimator for each use rather
     * than cache the reference.
     *
     * <p>
     * The accessor is public rather than package-private because the forecast state
     * indicator in {@code org.ta4j.core.indicators.forecast} consumes it across
     * packages: publishing the mean and the variance from this single shared
     * estimator is what keeps both moments consistent for one state.
     *
     * @return the shared EWMA mean estimator
     * @since 0.24.2
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "the mean estimator is the shared recursion this "
            + "variance deviates from; exposing the instance is the accessor's contract and it is only replaced "
            + "in place on prune re-anchoring, never mutated after construction")
    public Indicator<Num> getMeanIndicator() {
        BarSeries series = getBarSeries();
        while (true) {
            int removedBarsCount = series.getRemovedBarsCount();
            if (removedBarsCount != observedRemovedBarsCount) {
                resetForRetainedHead(removedBarsCount);
            }
            Indicator<Num> current = meanIndicator;
            // Validate against the locally captured snapshot, not the shared
            // field: another reader may have re-anchored the estimator after
            // this read, and a value anchored to the discarded prefix must
            // never be handed out.
            if (series.getRemovedBarsCount() == removedBarsCount) {
                return current;
            }
        }
    }

    /**
     * EWMA mean recursion that shares the parent's exact decay complement.
     *
     * <p>
     * {@code EWMAIndicator} derives its complement in primitive double
     * ({@code 1d - decayFactor}), which injects the binary rounding artifact and,
     * under a coarse factory that rounds the decay to one, collapses the weight to
     * zero; this recursion applies the parent's exact BigDecimal complement
     * instead, so the published mean and the variance deviate from one estimator
     * with identical decay weights. Seeding mirrors the EWMA contract: the window
     * mean after warm-up and whenever the previous state is non-finite.
     */
    private static final class SharedMeanIndicator extends RecursiveCachedIndicator<Num> {

        private final Indicator<Num> indicator;
        private final int barCount;
        private final Num decay;
        private final Num oneMinusDecay;

        private SharedMeanIndicator(Indicator<Num> indicator, int barCount, Num decay, Num oneMinusDecay) {
            super(indicator);
            this.indicator = indicator;
            this.barCount = barCount;
            this.decay = decay;
            this.oneMinusDecay = oneMinusDecay;
        }

        @Override
        protected Num calculate(int index) {
            int beginIndex = getBarSeries().getBeginIndex();
            if (index == 0 && beginIndex > 0) {
                // Removed-index reads anchor at the retained head, mirroring
                // the parent recursion: after pruning, the mean read at
                // index 0 belongs to the first retained bar, not the
                // discarded one.
                index = beginIndex;
            }
            if (index - beginIndex < getCountOfUnstableBars()) {
                return NaN.NaN;
            }
            Num current = indicator.getValue(index);
            if (!Num.isFinite(current)) {
                return NaN.NaN;
            }
            if (index == beginIndex) {
                // Only reachable when barCount == 1 (any larger window still
                // has unstable bars at beginIndex), so the single-bar window
                // seeds the mean with the bar itself.
                return windowMean(indicator, barCount, index);
            }
            Num previousMean = getValue(index - 1);
            if (!Num.isFinite(previousMean)) {
                // Non-finite predecessor: re-seed from the window mean, the
                // graceful recovery EWMAIndicator applies, so one bad bar
                // never contaminates the mean recursion.
                return windowMean(indicator, barCount, index);
            }
            // The update is the convex combination decay * previousMean +
            // (1 - decay) * current, evaluated in the difference form
            // previousMean + (1 - decay) * (current - previousMean) while the
            // raw difference is finite: weighting the difference before
            // combining keeps a same-sign subnormal pair (a constant
            // Double.MIN_VALUE source at decay 0.5) from rounding both convex
            // operands to zero although their exact sum is representable.
            // Opposite-extreme finite bars overflow the raw difference, and a
            // subnormal delta underflows its weight and stalls the recursion
            // (both when the mean decays toward zero and when it grows past a
            // half-subnormal step), so both failures fall back to one exactly
            // combined convex sum.
            Num delta = current.minus(previousMean);
            if (!Num.isFinite(delta)) {
                return combinedMean(previousMean, current);
            }
            Num updated = previousMean.plus(delta.multipliedBy(oneMinusDecay));
            if (updated.isEqual(previousMean) && !delta.isZero()) {
                return combinedMean(previousMean, current);
            }
            return updated;
        }

        private Num combinedMean(Num previousMean, Num current) {
            // Exact binary expansions of the finite operands and weights are
            // combined without intermediate rounding and narrowed once: the
            // separately rounded products discard a half-subnormal
            // contribution although the correctly rounded mean is
            // representable (an exact 1.5 * MIN_VALUE mean rounds up to
            // 2 * MIN_VALUE instead of stalling at MIN_VALUE).
            BigDecimal weightedSum = new BigDecimal(previousMean.doubleValue())
                    .multiply(new BigDecimal(decay.doubleValue()))
                    .add(new BigDecimal(current.doubleValue()).multiply(new BigDecimal(oneMinusDecay.doubleValue())));
            return getBarSeries().numFactory().numOf(weightedSum);
        }

        @Override
        public int getCountOfUnstableBars() {
            return indicator.getCountOfUnstableBars() + barCount - 1;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " decayFactor: " + decayFactor;
    }
}
