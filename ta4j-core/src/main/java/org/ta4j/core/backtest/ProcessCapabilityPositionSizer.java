/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Position sizer that dampens exposure as a process capability statistic
 * approaches or breaches a control limit.
 *
 * <p>
 * At the entry index the sizer evaluates the given capability indicator (for
 * example a {@link org.ta4j.core.indicators.statistics.CusumIndicator CUSUM}
 * statistic) and returns
 *
 * <pre>
 * amount = baseAmount / (1 + max(0, statistic / controlLimit))
 * </pre>
 *
 * A statistic at zero yields the full {@code baseAmount}, at the
 * {@code controlLimit} half of it, and far above the limit the amount
 * approaches zero, so position size degrades gracefully instead of acting as a
 * hard cutoff. When the standardized statistic overflows the numeric
 * representation, the sizer re-derives the ratio in the context factory when it
 * can represent the magnitude, and only then falls back to the lesser of the
 * context factory's epsilon and {@code baseAmount}, keeping the amount positive
 * and never exceeding the configured amount, so the backtest does not abort on
 * an invalid size.
 *
 * <p>
 * If the statistic is non-finite at the entry index (for example while the
 * indicator is warming up) the sizer fails open and returns {@code baseAmount}.
 * A statistic that is finite in its own factory but overflows the backtest
 * context's {@link NumFactory} during coercion (for example a
 * {@code DecimalNum} statistic of 1e400 coerced into a {@code DoubleNum}
 * context) follows the same rule by the sign of the overflow: a positive
 * overflow saturates to the underflow floor — the lesser of the context
 * factory's epsilon and {@code baseAmount} — while a negative overflow fails
 * open and returns {@code baseAmount}. A non-positive statistic returns the
 * full {@code baseAmount} before the division, so a control limit that
 * underflows to zero during coercion cannot turn an exactly safe statistic into
 * the epsilon fallback. The capability indicator must be bound to a bar series
 * holding the same bars that are being backtested; entry indexes are evaluated
 * against it without further checks. All arithmetic runs in the backtest
 * context's {@link NumFactory}: the capability statistic and the sizing
 * parameters are coerced into the context factory's exact configuration rather
 * than matched by implementation class, so mixed-factory and mixed-precision
 * {@code DecimalNum} compositions round consistently at sizing time. The
 * standardized ratio divides the coerced operands in the context factory so the
 * destination precision governs the quotient; when coercing an operand would
 * corrupt the ratio (underflow to zero or saturation in a narrower context),
 * the division falls back to the indicator factory, whose ratio stays exact.
 * The configured base amount and control limit are retained losslessly (as
 * their exact values, not the capability factory's rounded copies) and coerced
 * through the context factory at sizing time, so a coarse capability factory
 * never irreversibly rounds the configured amount or limit. A
 * {@code BigDecimal}-backed context receives the statistic and the base amount
 * through their {@code BigDecimal} delegate, preserving mantissa digits and
 * magnitude beyond the double range (for example a {@code 1e400} statistic
 * sizes at the exact damped amount {@code baseAmount / (1 + 1e400)}); the
 * double-overflow epsilon floor applies only when the context itself cannot
 * represent the ratio. Backtest managers validate the returned amount through
 * {@link Num#doubleValue()}, so any amount that cannot round-trip through a
 * primitive double — a float-backed context overflowing to infinity, or a
 * {@code BigDecimal}-backed amount beyond the double range such as
 * {@code 1e400} — saturates at the largest finite magnitude the context factory
 * can represent within the double range: {@code Double.MAX_VALUE} where it
 * round-trips, a just-below-ceiling value for decimal-backed factories that
 * round it past the double range, or the float ceiling for narrower factories —
 * instead of aborting validation.
 *
 * @since 0.24.2
 */
public final class ProcessCapabilityPositionSizer implements PositionSizer {

    private final Indicator<Num> capabilityIndicator;
    private final Num baseAmount;
    private final BigDecimal rawBaseAmount;
    private final BigDecimal rawControlLimit;

    /**
     * Constructor.
     *
     * @param capabilityIndicator the process capability statistic to monitor; must
     *                            not be null and must be bound to a
     *                            {@link org.ta4j.core.BarSeries}
     * @param baseAmount          the position amount when the statistic is zero;
     *                            must be > 0 and finite. The configured value is
     *                            retained losslessly and coerced through the
     *                            backtest context's {@link NumFactory} at sizing
     *                            time, so the capability factory's precision never
     *                            rounds it irreversibly (a precision-1 capability
     *                            factory would collapse 1.2345 to 1)
     * @param controlLimit        the statistical control limit; must be > 0 and
     *                            finite. The configured value is retained
     *                            losslessly like {@code baseAmount} and coerced
     *                            through the context factory at sizing time, so the
     *                            capability factory's precision or range never
     *                            rejects it (a precision-1 capability factory would
     *                            collapse 3.14159 to 3, and a limit of 1e400 is
     *                            representable by a DecimalNum context but not a
     *                            DoubleNum capability)
     * @since 0.24.2
     */
    public ProcessCapabilityPositionSizer(Indicator<Num> capabilityIndicator, Number baseAmount, Number controlLimit) {
        this.capabilityIndicator = Objects.requireNonNull(capabilityIndicator, "capabilityIndicator must not be null");
        this.rawBaseAmount = requirePositiveFiniteRaw(baseAmount, "baseAmount");
        this.baseAmount = capabilityIndicator.getBarSeries().numFactory().numOf(this.rawBaseAmount);
        this.rawControlLimit = requirePositiveFiniteRaw(controlLimit, "controlLimit");
    }

    @Override
    public Num amount(PositionSizer.Context context) {
        NumFactory factory = context.numFactory();
        Num statistic = capabilityIndicator.getValue(context.entryIndex());
        Num baseAmountValue = coerceBaseAmountToContextFactory(factory);
        if (baseAmountValue.isZero()) {
            // The constructor guarantees baseAmount is positive, so a coerced
            // zero can only be a positive underflow: saturate to the context
            // epsilon instead of returning zero and aborting validation.
            baseAmountValue = factory.epsilon();
        }
        if (!Num.isFinite(statistic) || !statistic.isPositive()) {
            // A non-positive statistic means an exactly safe process: size at
            // the full base amount. The sign is read in the indicator's own
            // factory, where a tiny positive statistic cannot underflow.
            return capToDoubleRange(factory, baseAmountValue);
        }
        // Standardize in the context factory so the division carries the
        // destination precision: a coarser indicator factory would
        // irreversibly round the ratio before the context ever sees it (a
        // precision-1 statistic of 1 over a control limit of 3 sized in a
        // precision-10 context must damp a base of 100 to 75, not 76.923).
        // Coercing the operands first can still corrupt the ratio: both can
        // underflow to zero or saturate in a narrower context while their
        // source ratio remains exact (statistic == controlLimit == 1e-400
        // must size at half the base amount, not the full amount), so those
        // cases recompute the ratio from the lossless raw forms instead of
        // dividing the capability factory's rounded operands.
        Num statisticValue = coerceToContextFactory(factory, statistic);
        Num controlLimitValue = coerceControlLimitToContextFactory(factory);
        boolean bigDecimalContext = factory.one().getDelegate() instanceof BigDecimal;
        boolean operandsCoercedLosslessly = bigDecimalContext || (Num.isFinite(statisticValue)
                && !statisticValue.isZero() && Num.isFinite(controlLimitValue) && !controlLimitValue.isZero()
                && Double.isFinite(statistic.doubleValue()) && Double.isFinite(rawControlLimit.doubleValue()));
        Num standardized;
        if (operandsCoercedLosslessly) {
            standardized = statisticValue.dividedBy(controlLimitValue);
        } else {
            // The context coercion of the operands was lossy (a saturated or
            // underflowed operand would corrupt the ratio): divide the lossless
            // raw forms at DECIMAL128, then narrow through the context factory.
            standardized = factory.numOf(statistic.bigDecimalValue().divide(rawControlLimit, MathContext.DECIMAL128));
        }
        if (!Num.isFinite(standardized)) {
            // The true ratio overflows the context factory; damp the lossless
            // decimal forms before narrowing.
            BigDecimal dampedQuotient = rawBaseAmount.divide(
                    BigDecimal.ONE.add(statistic.bigDecimalValue().divide(rawControlLimit, MathContext.DECIMAL128)),
                    MathContext.DECIMAL128);
            return floorOrCapRecoveredQuotient(factory, baseAmountValue, dampedQuotient);
        }
        Num damped;
        if (!Num.isFinite(baseAmountValue) && Double.isFinite(rawBaseAmount.doubleValue())
                && Double.isFinite(standardized.doubleValue())) {
            // A finite configured base that overflows the narrower context
            // factory still has a representable damped quotient in double space.
            damped = factory.numOf(rawBaseAmount.doubleValue() / (1.0 + standardized.doubleValue()));
        } else if (!(factory.one().getDelegate() instanceof BigDecimal) && !Double.isFinite(rawBaseAmount.doubleValue())
                && Double.isFinite(standardized.doubleValue())) {
            // A base beyond double range in a double-backed context must damp
            // from its lossless decimal form, not the saturated coercion.
            BigDecimal dampedQuotient = rawBaseAmount.divide(BigDecimal.ONE.add(standardized.bigDecimalValue()),
                    MathContext.DECIMAL128);
            return floorOrCapRecoveredQuotient(factory, baseAmountValue, dampedQuotient);
        } else {
            damped = baseAmountValue.dividedBy(factory.one().plus(standardized));
        }
        if (!Num.isFinite(damped)) {
            // The true damped quotient exceeds the context factory's range:
            // when the coerced base itself overflowed, saturate at the factory
            // ceiling; otherwise size at the context epsilon floor.
            return Num.isFinite(baseAmountValue) ? epsilonFloor(factory, baseAmountValue)
                    : saturationMagnitude(factory);
        }
        if (damped.isZero()) {
            return epsilonFloor(factory, baseAmountValue);
        }
        return capToDoubleRange(factory, damped);
    }

    private static Num coerceToContextFactory(NumFactory factory, Num value) {
        if (value.getNumFactory() == factory) {
            return value;
        }
        if (factory.one().getDelegate() instanceof BigDecimal) {
            // BigDecimal-backed destination: convert via BigDecimal so mantissa
            // digits and magnitude are preserved; a finite value outside the
            // double range (for example 1e400) never round-trips through a
            // primitive double.
            return factory.numOf(value.bigDecimalValue());
        }
        double doubleValue = value.doubleValue();
        // A finite source value whose double representation overflows is
        // saturated to the largest double-representable magnitude, keeping the
        // coerced value finite for double-backed destinations.
        return factory.numOf(Double.isFinite(doubleValue) ? doubleValue : Double.MAX_VALUE);
    }

    /**
     * Coerces the configured base amount into the backtest context's
     * {@link NumFactory}. The constructor validated it in the capability factory,
     * whose precision can differ from the context's: the lossless raw snapshot is
     * converted here so the context factory's exact configuration rounds the
     * configured value itself, not an already-capability-rounded copy of it.
     */
    private Num coerceBaseAmountToContextFactory(NumFactory factory) {
        // The constructor keeps the configured amount positive and finite in
        // decimal terms, but a primitive-backed capability factory can still
        // overflow it (for example DoubleNumFactory.numOf(new BigDecimal("1e400")));
        // the fast path therefore also requires a finite value and falls
        // through to the saturation path otherwise.
        if (factory == baseAmount.getNumFactory() && Num.isFinite(baseAmount)) {
            return baseAmount;
        }
        if (factory.one().getDelegate() instanceof BigDecimal) {
            // BigDecimal-backed destination: convert via BigDecimal so mantissa
            // digits and magnitude are preserved; a finite value outside the
            // double range (for example 1e400) never round-trips through a
            // primitive double.
            return factory.numOf(rawBaseAmount);
        }
        double doubleValue = rawBaseAmount.doubleValue();
        // A finite configured value whose double representation overflows is
        // saturated to the largest double-representable magnitude, keeping the
        // coerced value finite for double-backed destinations.
        return factory.numOf(Double.isFinite(doubleValue) ? doubleValue : Double.MAX_VALUE);
    }

    /**
     * Coerces the configured control limit into the context's {@link NumFactory}
     * from its lossless raw snapshot, mirroring
     * {@link #coerceBaseAmountToContextFactory(NumFactory)}.
     */
    private Num coerceControlLimitToContextFactory(NumFactory factory) {
        if (factory.one().getDelegate() instanceof BigDecimal) {
            return factory.numOf(rawControlLimit);
        }
        double doubleValue = rawControlLimit.doubleValue();
        return factory.numOf(Double.isFinite(doubleValue) ? doubleValue : Double.MAX_VALUE);
    }

    private static BigDecimal requirePositiveFiniteRaw(Number value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value instanceof Double || value instanceof Float) {
            if (!Double.isFinite(value.doubleValue())) {
                throw new IllegalArgumentException(name + " must be finite");
            }
        }
        BigDecimal raw;
        if (value instanceof BigDecimal) {
            raw = (BigDecimal) value;
        } else if (value instanceof BigInteger) {
            raw = new BigDecimal((BigInteger) value);
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long) {
            // Integral types convert directly so no precision is lost through
            // the doubleValue() round trip (Long.MAX_VALUE would round up by
            // one ulp).
            raw = BigDecimal.valueOf(value.longValue());
        } else {
            raw = BigDecimal.valueOf(value.doubleValue());
        }
        if (raw.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return raw;
    }

    /**
     * The largest magnitude the context factory can represent within the primitive
     * double range, which backtest managers validate through
     * {@link Num#doubleValue()}: the double ceiling for double-backed factories; a
     * just-below-ceiling value for decimal-backed factories, whose significant
     * digits round {@code Double.MAX_VALUE} itself past the double range; and the
     * float ceiling when the factory's backing primitive overflows to infinity or
     * NaN.
     */
    private static Num saturationMagnitude(NumFactory factory) {
        Num ceiling = factory.numOf(Double.MAX_VALUE);
        if (!Double.isFinite(ceiling.doubleValue())) {
            // Retry below the rounding margin: a decimal-backed factory with
            // fewer significant digits than Double.MAX_VALUE's shortest
            // representation rounds it up beyond the double range.
            ceiling = factory.numOf(Double.MAX_VALUE / 2);
        }
        if (!Double.isFinite(ceiling.doubleValue())) {
            ceiling = factory.numOf(Float.MAX_VALUE);
        }
        return ceiling;
    }

    /**
     * A positive decimal quotient can still underflow the context factory to zero;
     * floor those at the context epsilon instead of returning zero.
     */
    private static Num floorOrCapRecoveredQuotient(NumFactory factory, Num baseAmountValue, BigDecimal dampedQuotient) {
        Num recovered = factory.numOf(dampedQuotient);
        if (recovered.isZero()) {
            return epsilonFloor(factory, baseAmountValue);
        }
        return capToDoubleRange(factory, recovered);
    }

    private static Num capToDoubleRange(NumFactory factory, Num amount) {
        return Double.isFinite(amount.doubleValue()) ? amount : saturationMagnitude(factory);
    }

    /**
     * The context epsilon bounded by the coerced base amount. When the base amount
     * overflows the context factory to a non-finite value, the factory ceiling
     * stands in so the floor itself stays finite and the {@link PositionSizer}
     * contract is preserved.
     */
    private static Num epsilonFloor(NumFactory factory, Num baseAmountValue) {
        return factory.epsilon().min(Num.isFinite(baseAmountValue) ? baseAmountValue : saturationMagnitude(factory));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " baseAmount: " + rawBaseAmount + " controlLimit: " + rawControlLimit;
    }
}
