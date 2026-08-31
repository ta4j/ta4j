/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;
import java.math.BigDecimal;
import java.math.BigInteger;

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
 * representation, the damped amount underflows to zero and the sizer returns
 * the lesser of the context factory's epsilon and {@code baseAmount}, keeping
 * the amount positive and never exceeding the configured amount, so the
 * backtest does not abort on an invalid size.
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
 * configured base amount is retained losslessly (as its exact value, not the
 * capability factory's rounded copy of it) and coerced through the context
 * factory at sizing time, so a coarse capability factory never irreversibly
 * rounds the configured amount. A {@code BigDecimal}-backed context receives
 * the statistic and the base amount through their {@code BigDecimal} delegate,
 * preserving mantissa digits and magnitude beyond the double range (for example
 * a {@code 1e400} statistic sizes at the exact damped amount
 * {@code baseAmount / (1 + 1e400)}); the double-overflow epsilon floor applies
 * only when the context itself cannot represent the ratio.
 * <p>
 * Backtest managers validate the returned amount through
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
    private final Num controlLimit;

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
     *                            finite in the capability indicator's
     *                            {@link NumFactory}
     */
    public ProcessCapabilityPositionSizer(Indicator<Num> capabilityIndicator, Number baseAmount, Number controlLimit) {
        this.capabilityIndicator = Objects.requireNonNull(capabilityIndicator, "capabilityIndicator must not be null");
        this.rawBaseAmount = requirePositiveFiniteRaw(baseAmount, "baseAmount");
        this.baseAmount = capabilityIndicator.getBarSeries().numFactory().numOf(this.rawBaseAmount);
        this.controlLimit = requirePositiveFinite(controlLimit, "controlLimit",
                capabilityIndicator.getBarSeries().numFactory());
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
        // Standardize in the indicator's factory before coercing: both
        // operands can underflow to zero in the context factory while their
        // ratio remains exact (statistic == controlLimit == 1e-400 must size
        // at half the base amount, not the full amount).
        Num standardized = statistic.dividedBy(controlLimit);
        if (!Num.isFinite(standardized)) {
            // The true ratio overflows the indicator's factory: the process
            // is far outside control, so size at the context epsilon.
            return epsilonFloor(factory, baseAmountValue);
        }
        Num standardizedValue;
        if (standardized.getNumFactory() == factory) {
            standardizedValue = standardized;
        } else if (!(factory.one().getDelegate() instanceof BigDecimal)
                && !Double.isFinite(standardized.doubleValue())) {
            // The true ratio overflows a double-backed context: the process is
            // far outside control, so size at the context epsilon floor. A
            // BigDecimal-backed context keeps the ratio exact instead.
            return epsilonFloor(factory, baseAmountValue);
        } else {
            standardizedValue = coerceToContextFactory(factory, standardized);
        }
        Num damped;
        if (!Num.isFinite(baseAmountValue) && Double.isFinite(rawBaseAmount.doubleValue())
                && Double.isFinite(standardized.doubleValue())) {
            // A finite configured base that overflows the narrower context
            // factory (for example a 1e39 base in a float-backed context): the
            // damped quotient is still representable, so compute it in double
            // space and coerce the result instead of propagating a non-finite
            // base into the Num arithmetic.
            damped = factory.numOf(rawBaseAmount.doubleValue() / (1.0 + standardized.doubleValue()));
        } else if (!Num.isFinite(standardizedValue) && Double.isFinite(standardized.doubleValue())) {
            // The true ratio is finite as a primitive double but overflows the
            // narrower context factory (for example a 1e39 ratio in a
            // float-backed context). The damped quotient is still representable,
            // so compute it in double space and coerce the result instead of
            // collapsing to the context epsilon.
            damped = factory.numOf(baseAmountValue.doubleValue() / (1.0 + standardized.doubleValue()));
        } else {
            damped = baseAmountValue.multipliedBy(factory.one().dividedBy(factory.one().plus(standardizedValue)));
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
        return getClass().getSimpleName() + " baseAmount: " + baseAmount + " controlLimit: " + controlLimit;
    }

    private static Num requirePositiveFinite(Number value, String name, NumFactory factory) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value instanceof Double || value instanceof Float) {
            if (!Double.isFinite(value.doubleValue())) {
                throw new IllegalArgumentException(name + " must be finite");
            }
        }
        Num num = factory.numOf(value);
        if (!Num.isFinite(num) || !num.isPositive()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return num;
    }
}
