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
 * and {@code decayFactor}, and the recursion is seeded with the rolling
 * population variance {@link VarianceIndicator#ofPopulation(Indicator, int)
 * VarianceIndicator.ofPopulation(indicator, barCount)}. The seed also
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
    private final transient VarianceIndicator initialVarianceIndicator;
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
        this.initialVarianceIndicator = VarianceIndicator.ofPopulation(indicator, barCount);
        this.decay = indicator.getBarSeries().numFactory().numOf(decayFactor);
        this.oneMinusDecay = indicator.getBarSeries().numFactory().numOf(1d - decayFactor);
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
            if (series.getRemovedBarsCount() == observedRemovedBarsCount) {
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
            Num seedVariance = initialVarianceIndicator.getValue(index);
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
            return initialVarianceIndicator.getValue(index);
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
                Num seedVariance = initialVarianceIndicator.getValue(index);
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
        int removedBarsCount = series.getRemovedBarsCount();
        if (removedBarsCount != observedRemovedBarsCount) {
            resetForRetainedHead(removedBarsCount);
        }
        Indicator<Num> current = meanIndicator;
        // Same bounded re-check as getValue: a prune between the check and
        // the reference read would hand out an estimator anchored to the
        // discarded prefix, so re-check and retry once.
        if (series.getRemovedBarsCount() != observedRemovedBarsCount) {
            resetForRetainedHead(series.getRemovedBarsCount());
            return meanIndicator;
        }
        return current;
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
