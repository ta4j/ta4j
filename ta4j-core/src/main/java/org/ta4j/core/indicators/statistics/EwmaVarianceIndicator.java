/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.averages.EWMAIndicator;
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
 * where {@code mu} is an {@link EWMAIndicator} over the same {@code barCount}
 * and {@code decayFactor}, and the recursion is seeded with the seed window's
 * population variance. The seed is computed from a window-size-scaled mean and
 * per-term scaled squared deviations, so a seed whose naive sum-of-squares form
 * would overflow the numeric representation (for example a window whose
 * population variance is representable but contains a bar large enough to
 * overflow its own square) still publishes a finite value. The seed also
 * re-anchors the recursion whenever the previous state is non-finite: when only
 * the variance leg has collapsed, it re-anchors on the seed window measured
 * around the retained EWMA mean at the previous index, so the published mean
 * and variance keep describing one estimator.
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
 * computed against the discarded prefix are dropped, the control mean
 * {@link EWMAIndicator} is rebuilt with the configured window so that its seed
 * at the re-anchoring index is the configured EWMA seed (the SMA of the
 * retained seed window), and the recursion re-anchors with the first full seed
 * window available at the retained head; indices before that window, and any
 * index still inside the source's own warm-up, publish {@code NaN} so no future
 * bar or source-unstable bar leaks into a historical value. Reads bracket the
 * removal count across the cached read and repeat until it is stable, so a
 * concurrently pruning series can never publish a value computed against the
 * discarded prefix.
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
    private volatile transient EWMAIndicator meanIndicator;
    private final transient Num decay;
    private final transient Num oneMinusDecay;
    private volatile transient int observedRemovedBarsCount = getBarSeries().getRemovedBarsCount();
    private volatile transient int reseedIndex = -1;

    /**
     * Constructor.
     *
     * @param indicator   the indicator whose variance is monitored
     * @param barCount    the seed window length; must be >= 1
     * @param decayFactor the EWMA decay factor; must be in (0, 1)
     */
    public EwmaVarianceIndicator(Indicator<Num> indicator, int barCount, double decayFactor) {
        super(validateParameters(indicator, barCount, decayFactor));
        this.indicator = indicator;
        this.barCount = barCount;
        this.decayFactor = decayFactor;
        this.meanIndicator = new EWMAIndicator(indicator, barCount, decayFactor);
        // The complement is the exact BigDecimal difference 1 - rawDecay and
        // the decay is derived from it, mirroring CusumIndicator's scale-decay
        // conversion: computing the complement in primitive double first
        // injects the binary rounding artifact (1d - 0.06), and deriving it
        // from the already rounded decay collapses to zero where the decay
        // rounds to one (DecimalNumFactory precision 1 rounds 0.9999 to 1),
        // so the exact subtraction keeps decay plus complement summing to
        // exactly one under every factory precision.
        BigDecimal rawDecay = BigDecimal.valueOf(decayFactor);
        this.oneMinusDecay = indicator.getBarSeries().numFactory().numOf(BigDecimal.ONE.subtract(rawDecay));
        this.decay = indicator.getBarSeries().numFactory().one().minus(this.oneMinusDecay);
    }

    private static Indicator<Num> validateParameters(Indicator<Num> indicator, int barCount, double decayFactor) {
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
        return indicator;
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
            this.meanIndicator = new EWMAIndicator(indicator, barCount, decayFactor);
            reseedIndex = (int) Math.min((long) getBarSeries().getBeginIndex() + barCount - 1L, Integer.MAX_VALUE);
            invalidateCache();
            observedRemovedBarsCount = removedBarsCount;
        }
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
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
        return Num.isFinite(deviation) && Num.isFinite(updatedVariance) ? updatedVariance : NaN.NaN;
    }

    private Num windowVarianceAround(int index, Num center) {
        int windowBegin = Math.max(index - barCount + 1, getBarSeries().getBeginIndex());
        NumFactory factory = getBarSeries().numFactory();
        Num barCountNum = factory.numOf(barCount);
        Num scaledSquareSum = factory.zero();
        for (int windowIndex = windowBegin; windowIndex <= index; windowIndex++) {
            Num deviation = indicator.getValue(windowIndex).minus(center);
            // Scale one deviation factor by the window size before completing
            // the product: each squared deviation can overflow even when the
            // averaged recovery variance deviation^2 / barCount is
            // representable, so per-term scaling keeps the re-anchor finite.
            scaledSquareSum = scaledSquareSum.plus(deviation.multipliedBy(deviation.dividedBy(barCountNum)));
        }
        return scaledSquareSum;
    }

    /**
     * Population variance of the seed window {@code [index - barCount + 1,
     * index]}, computed from a window-size-scaled mean and per-term scaled squared
     * deviations. The naive sum-of-squares form first squares each bar and can
     * overflow the numeric representation even when the averaged variance is
     * representable (a window containing {@code 2e154} squares to {@code 4e308} for
     * {@code DoubleNum}, while its population variance {@code 8e308/9} is finite),
     * so both the mean and the accumulation are scaled by the window size before
     * their products complete.
     */
    private Num rollingWindowVariance(int index) {
        int windowBegin = Math.max(index - barCount + 1, getBarSeries().getBeginIndex());
        NumFactory factory = getBarSeries().numFactory();
        Num barCountNum = factory.numOf(barCount);
        Num scaledMean = factory.zero();
        for (int windowIndex = windowBegin; windowIndex <= index; windowIndex++) {
            scaledMean = scaledMean.plus(indicator.getValue(windowIndex).dividedBy(barCountNum));
        }
        return windowVarianceAround(index, scaledMean);
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
     * @return the shared EWMA mean indicator
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

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " decayFactor: " + decayFactor;
    }
}
