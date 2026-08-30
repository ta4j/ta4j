/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

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
 * persistent deviations from the target raise {@code S}.
 *
 * <p>
 * While the source indicator is warming up ({@code index - beginIndex <
 * getCountOfUnstableBars()}) the value is {@code NaN}. Non-finite inputs carry
 * the previous CUSUM value forward (seeded at zero on the first addressable
 * bar) and leave the deviation scale unchanged, so a gap in the data neither
 * triggers nor resets the statistic.
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
        NumFactory factory = parameters.indicator().getBarSeries().numFactory();
        this.targetMean = factory.numOf(parameters.targetMean().doubleValue());
        this.allowance = factory.numOf(parameters.allowance().doubleValue());
        this.outlierClipFactor = factory.numOf(parameters.outlierClipFactor().doubleValue());
        this.scaleDecay = factory.numOf(parameters.scaleDecay().doubleValue());
        this.deviationScale = new DeviationScaleIndicator(this.indicator, this.targetMean, this.allowance,
                this.scaleDecay);
    }

    private static Parameters validateParameters(Indicator<Num> indicator, Number targetMean, Number allowance,
            Number outlierClipFactor, Number scaleDecay) {
        Objects.requireNonNull(indicator, "indicator must not be null");
        if (indicator.getBarSeries() == null) {
            throw new IllegalArgumentException("indicator must be bound to a BarSeries");
        }
        requireFinite(targetMean, "targetMean");
        requireFinite(allowance, "allowance");
        requireFinite(outlierClipFactor, "outlierClipFactor");
        requireFinite(scaleDecay, "scaleDecay");
        if (allowance.doubleValue() < 0) {
            throw new IllegalArgumentException("allowance must be >= 0");
        }
        if (outlierClipFactor.doubleValue() <= 0) {
            throw new IllegalArgumentException("outlierClipFactor must be > 0");
        }
        if (scaleDecay.doubleValue() <= 0 || scaleDecay.doubleValue() >= 1) {
            throw new IllegalArgumentException("scaleDecay must be in (0, 1)");
        }
        return new Parameters(indicator, targetMean, allowance, outlierClipFactor, scaleDecay);
    }

    private record Parameters(Indicator<Num> indicator, Number targetMean, Number allowance, Number outlierClipFactor,
            Number scaleDecay) {
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
        if (index > beginIndex) {
            Num previousScale = deviationScale.getValue(index - 1);
            if (Num.isFinite(previousScale) && !previousScale.isZero()) {
                Num bound = previousScale.multipliedBy(outlierClipFactor);
                deviation = deviation.max(bound.negate()).min(bound);
            }
        }
        Num previous = index == beginIndex ? getBarSeries().numFactory().zero() : getValue(index - 1);
        if (!Num.isFinite(previous)) {
            previous = getBarSeries().numFactory().zero();
        }
        return previous.plus(deviation).max(getBarSeries().numFactory().zero());
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " targetMean: " + targetMean + " allowance: " + allowance;
    }

    private static double requireFinite(Number value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        double doubleValue = value.doubleValue();
        if (!Double.isFinite(doubleValue)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return doubleValue;
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
