/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * One-sided, winsorized cumulative sum (CUSUM) statistic.
 *
 * <p>
 * CUSUM detects a shift of the monitored value away from a target mean
 * {@code targetMean}. At each addressable index {@code t} it accumulates the
 * shortfall
 *
 * <pre>
 * S_t = max(0, S_{t-1} + clip(mu0 - X_t - k, +-clipFactor * sigmaHat_{t-1}))
 * </pre>
 *
 * where {@code mu0} is the {@code targetMean}, {@code k} the {@code allowance},
 * and {@code S} is seeded with {@code max(0, mu0 - X_begin - k)} on the first
 * addressable bar. The raw increment is winsorized against the exponentially
 * smoothed mean absolute deviation
 *
 * <pre>
 * sigmaHat_t = scaleDecay * sigmaHat_{t-1} + (1 - scaleDecay) * |mu0 - X_t - k|
 * </pre>
 *
 * so isolated outliers do not dominate the accumulated statistic; only
 * persistent deviations from the target raise {@code S}. When the previous
 * deviation scale is zero (all raw increments so far were exactly zero), the
 * winsorization bound is zero: the first non-zero deviation is fully damped and
 * its raw magnitude bootstraps {@code sigmaHat}, so a single outlier cannot
 * trip the statistic right after a perfectly on-target run.
 *
 * <p>
 * While the source indicator is warming up ({@code index - beginIndex <
 * getCountOfUnstableBars()}) the value is {@code NaN}. Non-finite inputs carry
 * the previous CUSUM value forward (seeded at zero on the first addressable
 * bar) and leave the deviation scale unchanged, so a gap in the data neither
 * triggers nor resets the statistic.
 *
 * If the derived deviation, the deviation scale, or the accumulated CUSUM
 * overflows the numeric representation (a {@code DoubleNum} series jumping
 * between opposite extremes), the overflowing term saturates at the largest
 * finite magnitude instead of publishing infinity, so a composed kill switch
 * still reacts to the extreme observation and the winsorization bound is never
 * silently disabled by a non-finite scale.
 *
 * <p>
 * When the backing series prunes its retained head (for example through
 * {@link org.ta4j.core.BarSeries#setMaximumBarCount(int)}), the recursion is
 * re-anchored at the new first addressable bar: cached values computed against
 * the discarded prefix are dropped and the statistic resumes from the new head
 * as if the series had started there.
 *
 * <p>
 * Typical use is a statistical control-limit kill switch on an equity curve or
 * return stream: build the CUSUM over, e.g., {@code Returns}, and stop trading
 * once {@code NumericIndicator.of(cusum).isLessThan(H)} flips, where {@code H}
 * is derived from a volatility monitor such as
 * {@code NumericIndicator.of(ewmaVariance).sqrt().multipliedBy(h)} with
 * {@code h} around four or five.
 *
 * @since 0.24.2
 */
public class CusumIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final Num targetMean;
    private final Num allowance;
    private final Num outlierClipFactor;
    private final Num scaleDecay;
    private final transient DeviationScaleIndicator deviationScale;
    private volatile transient int observedRemovedBarsCount = getBarSeries().getRemovedBarsCount();

    @Override
    public Num getValue(int index) {
        BarSeries series = getBarSeries();
        int removedBarsCount = series.getRemovedBarsCount();
        if (removedBarsCount != observedRemovedBarsCount) {
            resetForRetainedHead(removedBarsCount);
        }
        return super.getValue(index);
    }

    private synchronized void resetForRetainedHead(int removedBarsCount) {
        if (removedBarsCount != observedRemovedBarsCount) {
            // Invalidate first, publish last: a concurrent reader that
            // observes the new count must never see caches still computed
            // from the discarded prefix.
            invalidateCache();
            deviationScale.invalidateForRetainedHead();
            observedRemovedBarsCount = removedBarsCount;
        }
    }

    /**
     * Constructor with defaults {@code outlierClipFactor = 3.0} and
     * {@code scaleDecay = 0.94}.
     *
     * @param indicator  the indicator to monitor for mean shifts
     * @param targetMean the in-control process mean {@code mu0}
     * @param allowance  the tolerable deviation {@code k}; must be >= 0
     */
    public CusumIndicator(Indicator<Num> indicator, Number targetMean, Number allowance) {
        this(validateParameters(indicator, targetMean, allowance, 3.0, 0.94));
    }

    /**
     * Constructor.
     *
     * @param indicator         the indicator to monitor for mean shifts
     * @param targetMean        the in-control process mean {@code mu0}; must be
     *                          finite
     * @param allowance         the tolerable deviation {@code k}; must be >= 0
     * @param outlierClipFactor the winsorization factor; must be > 0
     * @param scaleDecay        the EWMA decay of the deviation scale; must be in
     *                          (0, 1)
     */
    public CusumIndicator(Indicator<Num> indicator, Number targetMean, Number allowance, Number outlierClipFactor,
            Number scaleDecay) {
        this(validateParameters(indicator, targetMean, allowance, outlierClipFactor, scaleDecay));
    }

    private CusumIndicator(Parameters parameters) {
        super(parameters.indicator());

        this.indicator = parameters.indicator();
        this.targetMean = parameters.targetMean();
        this.allowance = parameters.allowance();
        this.outlierClipFactor = parameters.outlierClipFactor();
        this.scaleDecay = parameters.scaleDecay();
        this.deviationScale = new DeviationScaleIndicator(this.indicator, this.targetMean, this.allowance,
                this.scaleDecay);
    }

    private static Parameters validateParameters(Indicator<Num> indicator, Number targetMean, Number allowance,
            Number outlierClipFactor, Number scaleDecay) {
        Objects.requireNonNull(indicator, "indicator must not be null");
        if (indicator.getBarSeries() == null) {
            throw new IllegalArgumentException("indicator must be bound to a BarSeries");
        }
        NumFactory factory = indicator.getBarSeries().numFactory();
        Num validatedTargetMean = requireFiniteNum(targetMean, "targetMean", factory);
        Num validatedAllowance = requireFiniteNum(allowance, "allowance", factory);
        Num validatedOutlierClipFactor = requireFiniteNum(outlierClipFactor, "outlierClipFactor", factory);
        Num validatedScaleDecay = validateScaleDecay(scaleDecay, factory);
        if (validatedAllowance.isNegative()) {
            throw new IllegalArgumentException("allowance must be >= 0");
        }
        if (!validatedOutlierClipFactor.isPositive()) {
            throw new IllegalArgumentException("outlierClipFactor must be > 0");
        }
        return new Parameters(indicator, validatedTargetMean, validatedAllowance, validatedOutlierClipFactor,
                validatedScaleDecay);
    }

    private static Num validateScaleDecay(Number scaleDecay, NumFactory factory) {
        Objects.requireNonNull(scaleDecay, "scaleDecay must not be null");
        // Validate the raw value before it passes through the factory: a
        // low-precision factory can round an in-range value such as 0.9999 to
        // its boundary 1. The complement is converted first because 1 - decay
        // stays representable where the decay itself rounds to one, keeping
        // the EWMA weight meaningful under coarse precision, and decay plus
        // complement sums to exactly one.
        double rawScaleDecay = scaleDecay.doubleValue();
        if (!Double.isFinite(rawScaleDecay) || rawScaleDecay <= 0d || rawScaleDecay >= 1d) {
            throw new IllegalArgumentException("scaleDecay must be in (0, 1)");
        }
        Num oneMinusScaleDecay = factory.numOf(1d - rawScaleDecay);
        return factory.one().minus(oneMinusScaleDecay);
    }

    private record Parameters(Indicator<Num> indicator, Num targetMean, Num allowance, Num outlierClipFactor,
            Num scaleDecay) {
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (index - beginIndex < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num current = indicator.getValue(index);
        if (!Num.isFinite(current)) {
            Num previous = index == beginIndex ? getBarSeries().numFactory().zero() : getValue(index - 1);
            return Num.isFinite(previous) ? previous : getBarSeries().numFactory().zero();
        }
        Num deviation = targetMean.minus(current).minus(allowance);
        if (!Num.isFinite(deviation)) {
            deviation = getBarSeries().numFactory()
                    .numOf(deviation.isNegative() ? -Double.MAX_VALUE : Double.MAX_VALUE);
        }
        if (index > beginIndex) {
            Num previousScale = deviationScale.getValue(index - 1);
            if (Num.isFinite(previousScale)) {
                Num bound = previousScale.multipliedBy(outlierClipFactor);
                deviation = deviation.max(bound.negate()).min(bound);
            }
        }
        Num previous = index == beginIndex ? getBarSeries().numFactory().zero() : getValue(index - 1);
        if (!Num.isFinite(previous)) {
            previous = getBarSeries().numFactory().zero();
        }
        Num updated = previous.plus(deviation);
        if (!Num.isFinite(updated)) {
            updated = getBarSeries().numFactory().numOf(Double.MAX_VALUE);
        }
        return updated.max(getBarSeries().numFactory().zero());
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " targetMean: " + targetMean + " allowance: " + allowance;
    }

    private static Num requireFiniteNum(Number value, String name, NumFactory factory) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value instanceof Double || value instanceof Float) {
            if (!Double.isFinite(value.doubleValue())) {
                throw new IllegalArgumentException(name + " must be finite");
            }
        }
        Num num = factory.numOf(value);
        if (!Num.isFinite(num)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return num;
    }

    /**
     * Exponentially smoothed mean absolute deviation of the raw CUSUM increment
     * {@code mu0 - X_t - k}, using the parent's {@code scaleDecay}. Follows the
     * parent's non-finite convention: gaps carry the previous scale forward and are
     * seeded at zero on the first addressable bar.
     */
    private static final class DeviationScaleIndicator extends RecursiveCachedIndicator<Num> {

        private final Indicator<Num> indicator;
        private final Num targetMean;
        private final Num allowance;
        private final Num scaleDecay;
        private final Num oneMinusScaleDecay;

        private DeviationScaleIndicator(Indicator<Num> indicator, Num targetMean, Num allowance, Num scaleDecay) {
            super(indicator);
            this.indicator = indicator;
            this.targetMean = targetMean;
            this.allowance = allowance;
            this.scaleDecay = scaleDecay;
            this.oneMinusScaleDecay = indicator.getBarSeries().numFactory().one().minus(scaleDecay);
        }

        private void invalidateForRetainedHead() {
            invalidateCache();
        }

        @Override
        public int getCountOfUnstableBars() {
            return indicator.getCountOfUnstableBars();
        }

        @Override
        protected Num calculate(int index) {
            int beginIndex = getBarSeries().getBeginIndex();
            if (index - beginIndex < getCountOfUnstableBars()) {
                return NaN.NaN;
            }
            Num current = indicator.getValue(index);
            if (!Num.isFinite(current)) {
                Num previous = index == beginIndex ? getBarSeries().numFactory().zero() : getValue(index - 1);
                return Num.isFinite(previous) ? previous : getBarSeries().numFactory().zero();
            }
            Num increment = targetMean.minus(current).minus(allowance).abs();
            if (!Num.isFinite(increment)) {
                // Opposite extremes overflow the subtraction: saturate so the
                // scale stays finite and the parent keeps winsorizing against
                // its bound instead of skipping the clip for a non-finite
                // previous scale.
                increment = getBarSeries().numFactory().numOf(Double.MAX_VALUE);
            }
            if (index == beginIndex) {
                return increment;
            }
            Num previous = getValue(index - 1);
            if (!Num.isFinite(previous)) {
                return increment;
            }
            return previous.multipliedBy(scaleDecay).plus(increment.multipliedBy(oneMinusScaleDecay));
        }
    }
}
