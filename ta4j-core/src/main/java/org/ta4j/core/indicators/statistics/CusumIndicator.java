/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;
import java.math.BigDecimal;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
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
 * The raw {@code scaleDecay} parameter is retained separately from its
 * factory-rounded arithmetic value: a coarse factory may round an in-range
 * decay such as {@code 0.9999} to its boundary {@code 1}, and the descriptor /
 * JSON round trip must reconstruct the logical indicator from the raw value
 * instead of the rounded one.
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
 * finite magnitude the active factory can represent (the double ceiling for
 * double- and decimal-backed factories, the float ceiling for float-backed
 * ones) instead of publishing infinity, so a composed kill switch still reacts
 * to the extreme observation and the winsorization bound is never silently
 * disabled by a non-finite scale. The three-term deviation is first computed in
 * scaled space, so a representable true difference that only overflows the
 * naive subtraction order (for example {@code 1.7e308 - (-1e308) - 1.7e308 =
 * 1e308}) is never saturated: only a genuinely unrepresentable difference falls
 * back to the finite saturation.
 *
 * <p>
 * When the backing series prunes its retained head (for example through
 * {@link org.ta4j.core.BarSeries#setMaximumBarCount(int)}), the recursion is
 * re-anchored at the new first addressable bar: cached values computed against
 * the discarded prefix are dropped and the statistic resumes from the new head
 * as if the series had started there. Reads bracket the removal count across
 * the cached read and repeat until it is stable, so a concurrently pruning
 * series can never publish a value computed against the discarded prefix.
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
    private final BigDecimal scaleDecay;
    private final transient DeviationScaleIndicator deviationScale;
    private volatile transient int observedRemovedBarsCount = getBarSeries().getRemovedBarsCount();

    @Override
    public Num getValue(int index) {
        BarSeries series = getBarSeries();
        while (true) {
            int removedBarsCount = series.getRemovedBarsCount();
            if (removedBarsCount != observedRemovedBarsCount) {
                resetForRetainedHead(removedBarsCount);
            }
            Num value = super.getValue(index);
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
            invalidateCache();
            deviationScale.invalidateForRetainedHead();
            observedRemovedBarsCount = removedBarsCount;
        }
    }

    /**
     * Creates a close-price CUSUM indicator with defaults
     * {@code outlierClipFactor = 3.0} and {@code scaleDecay = 0.94}.
     *
     * @param series     bar series
     * @param targetMean the in-control process mean {@code mu0}; must be finite
     * @param allowance  the tolerable deviation {@code k}; must be >= 0
     * @since 0.24.2
     */
    public CusumIndicator(BarSeries series, Number targetMean, Number allowance) {
        this(validateParameters(closePrice(series), targetMean, allowance, 3.0, 0.94));
    }

    /**
     * Creates a close-price CUSUM indicator.
     *
     * @param series            bar series
     * @param targetMean        the in-control process mean {@code mu0}; must be
     *                          finite
     * @param allowance         the tolerable deviation {@code k}; must be >= 0
     * @param outlierClipFactor the winsorization factor; must be > 0
     * @param scaleDecay        the EWMA decay of the deviation scale; must be in
     *                          (0, 1)
     * @since 0.24.2
     */
    public CusumIndicator(BarSeries series, Number targetMean, Number allowance, Number outlierClipFactor,
            Number scaleDecay) {
        this(validateParameters(closePrice(series), targetMean, allowance, outlierClipFactor, scaleDecay));
    }

    private static ClosePriceIndicator closePrice(BarSeries series) {
        return new ClosePriceIndicator(Objects.requireNonNull(series, "series must not be null"));
    }

    /**
     * Constructor with defaults {@code outlierClipFactor = 3.0} and
     * {@code scaleDecay = 0.94}.
     *
     * @param indicator  the indicator to monitor for mean shifts
     * @param targetMean the in-control process mean {@code mu0}
     * @param allowance  the tolerable deviation {@code k}; must be >= 0
     * @since 0.24.2
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
     * @since 0.24.2
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
        this.scaleDecay = parameters.rawScaleDecay();
        this.deviationScale = new DeviationScaleIndicator(this.indicator, this.targetMean, this.allowance,
                parameters.oneMinusScaleDecay());
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
        ValidatedScaleDecay validatedScaleDecay = validateScaleDecay(scaleDecay, factory);
        if (validatedAllowance.isNegative()) {
            throw new IllegalArgumentException("allowance must be >= 0");
        }
        if (!validatedOutlierClipFactor.isPositive()) {
            throw new IllegalArgumentException("outlierClipFactor must be > 0");
        }
        return new Parameters(indicator, validatedTargetMean, validatedAllowance, validatedOutlierClipFactor,
                validatedScaleDecay.rawScaleDecay(), validatedScaleDecay.oneMinusScaleDecay());
    }

    private static ValidatedScaleDecay validateScaleDecay(Number scaleDecay, NumFactory factory) {
        Objects.requireNonNull(scaleDecay, "scaleDecay must not be null");
        // Validate the raw value before it passes through the factory: a
        // low-precision factory can round an in-range value such as 0.9999 to
        // its boundary 1, and narrowing an arbitrary-precision BigDecimal
        // such as 1e-400 through doubleValue() collapses it to zero.
        // BigDecimal comparison keeps the (0, 1) interval check exact. The
        // complement is the exact BigDecimal difference 1 - rawDecay:
        // computing it in primitive double first injects the binary rounding
        // artifact (1d - 0.94 = 0.060000000000000005), and deriving it from
        // the already rounded decay collapses to zero where the decay rounds
        // to one. Carrying this applied update weight preserves meaningful
        // scale updates under coarse precision; exact recovery derives its
        // matching first factor from the same value instead of multiplying
        // independently rounded weights. The raw value is retained on the
        // indicator so the descriptor / JSON round trip serializes the in-range
        // parameter instead of its rounded boundary.
        double narrowed = scaleDecay.doubleValue();
        if (!Double.isFinite(narrowed)) {
            throw new IllegalArgumentException("scaleDecay must be in (0, 1)");
        }
        BigDecimal rawScaleDecay = scaleDecay instanceof BigDecimal ? (BigDecimal) scaleDecay
                : BigDecimal.valueOf(narrowed);
        if (rawScaleDecay.compareTo(BigDecimal.ZERO) <= 0 || rawScaleDecay.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("scaleDecay must be in (0, 1)");
        }
        BigDecimal complement = BigDecimal.ONE.subtract(rawScaleDecay);
        Num oneMinusScaleDecay = factory.numOf(complement);
        return new ValidatedScaleDecay(rawScaleDecay, oneMinusScaleDecay);
    }

    private record ValidatedScaleDecay(BigDecimal rawScaleDecay, Num oneMinusScaleDecay) {
    }

    private record Parameters(Indicator<Num> indicator, Num targetMean, Num allowance, Num outlierClipFactor,
            BigDecimal rawScaleDecay, Num oneMinusScaleDecay) {
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (index == 0 && beginIndex > 0) {
            // Removed-index reads map to the synthetic zero; anchor at the
            // retained head so the recursion never backtracks into pruned bars.
            index = beginIndex;
        }
        if (index - beginIndex < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num current = indicator.getValue(index);
        if (!Num.isFinite(current)) {
            Num previous = index == beginIndex ? getBarSeries().numFactory().zero() : getValue(index - 1);
            return Num.isFinite(previous) ? previous : getBarSeries().numFactory().zero();
        }
        Num deviation = scaledDeviation(targetMean, current, allowance);
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
            updated = saturationMagnitude(getBarSeries().numFactory());
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
     * The largest finite magnitude the active factory can represent: the double
     * ceiling for double- and decimal-backed factories, and the float ceiling when
     * the factory's backing primitive overflows {@code Double.MAX_VALUE} to
     * infinity, so the documented finite saturation holds for every delegate type.
     */
    static Num saturationMagnitude(NumFactory factory) {
        Num doubleCeiling = factory.numOf(Double.MAX_VALUE);
        return Num.isFinite(doubleCeiling) ? doubleCeiling : factory.numOf(Float.MAX_VALUE);
    }

    /**
     * The three-term deviation {@code targetMean - current - allowance}, computed
     * in scaled space when the naive subtraction overflows. Opposite extremes
     * overflow the naive form even when the true difference is representable
     * ({@code 1.7e308 - (-1e308) - 1.7e308 = 1e308} overflows the intermediate sum
     * to infinity for {@code DoubleNum}), and no fixed reordering of the terms is
     * universally safe. Rescaling the terms by the largest magnitude bounds each
     * scaled operand to {@code [-1, 1]}, so the scaled subtraction is finite and
     * the rescaled product overflows only when the true difference itself is
     * unrepresentable; that case saturates at the largest finite magnitude by sign.
     * The inputs are validated finite at the call sites, so the scale is positive
     * and finite.
     */
    static Num scaledDeviation(Num targetMean, Num current, Num allowance) {
        Num deviation = targetMean.minus(current).minus(allowance);
        if (Num.isFinite(deviation)) {
            return deviation;
        }
        NumFactory factory = targetMean.getNumFactory();
        Num scale = targetMean.abs().max(current.abs()).max(allowance.abs());
        Num scaled = targetMean.dividedBy(scale).minus(current.dividedBy(scale)).minus(allowance.dividedBy(scale));
        Num rescaled = scaled.multipliedBy(scale);
        if (Num.isFinite(rescaled)) {
            return rescaled;
        }
        Num magnitude = saturationMagnitude(factory);
        return rescaled.isNegative() ? magnitude.negate() : magnitude;
    }

    /**
     * Exponentially smoothed mean absolute deviation of the raw CUSUM increment
     * {@code mu0 - X_t - k}, using the parent's exact-complement update weight.
     * Follows the parent's non-finite convention: gaps carry the previous scale
     * forward and are seeded at zero on the first addressable bar.
     */
    private static final class DeviationScaleIndicator extends RecursiveCachedIndicator<Num> {

        private final Indicator<Num> indicator;
        private final Num targetMean;
        private final Num allowance;
        private final Num oneMinusScaleDecay;

        private DeviationScaleIndicator(Indicator<Num> indicator, Num targetMean, Num allowance,
                Num oneMinusScaleDecay) {
            super(indicator);
            this.indicator = indicator;
            this.targetMean = targetMean;
            this.allowance = allowance;
            this.oneMinusScaleDecay = oneMinusScaleDecay;
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
            // Scaled deviation keeps the increment finite for representable
            // true differences, so the scale never saturates prematurely and
            // the parent keeps winsorizing against its bound.
            Num increment = CusumIndicator.scaledDeviation(targetMean, current, allowance).abs();
            if (index == beginIndex) {
                return increment;
            }
            Num previous = getValue(index - 1);
            if (!Num.isFinite(previous)) {
                return increment;
            }
            // Weight the finite difference before combining. A low-magnitude
            // result or delta can misround on the active primitive grid, and
            // the published scale drives the parent's winsorization bound.
            // Such updates recombine exact operands without intermediate
            // rounding and narrow once.
            Num delta = increment.minus(previous);
            Num updated = previous.plus(delta.multipliedBy(oneMinusScaleDecay));
            if (ExactDecimalArithmetic.requiresExactRecovery(updated, delta)) {
                return ExactDecimalArithmetic.exactWeightedSum(getBarSeries().numFactory(),
                        ExactDecimalArithmetic.exactValueOf(previous), ExactDecimalArithmetic.exactValueOf(increment),
                        oneMinusScaleDecay);
            }
            return updated;
        }
    }
}
