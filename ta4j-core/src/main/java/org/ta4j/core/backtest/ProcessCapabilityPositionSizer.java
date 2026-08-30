/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;

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
 * The capability indicator must be bound to a bar series holding the same bars
 * that are being backtested; entry indexes are evaluated against it without
 * further checks. All arithmetic runs in the backtest context's
 * {@link NumFactory}: the capability statistic and the sizing parameters are
 * coerced into it, so mixed-factory compositions do not fail at sizing time.
 *
 * @since 0.24.2
 */
public final class ProcessCapabilityPositionSizer implements PositionSizer {

    private final Indicator<Num> capabilityIndicator;
    private final Num baseAmount;
    private final Num controlLimit;

    /**
     * Constructor.
     *
     * @param capabilityIndicator the process capability statistic to monitor; must
     *                            not be null and must be bound to a
     *                            {@link org.ta4j.core.BarSeries}
     * @param baseAmount          the position amount when the statistic is zero;
     *                            must be > 0 and finite in the capability
     *                            indicator's {@link NumFactory}
     * @param controlLimit        the statistical control limit; must be > 0 and
     *                            finite in the capability indicator's
     *                            {@link NumFactory}
     */
    public ProcessCapabilityPositionSizer(Indicator<Num> capabilityIndicator, Number baseAmount, Number controlLimit) {
        this.capabilityIndicator = Objects.requireNonNull(capabilityIndicator, "capabilityIndicator must not be null");
        NumFactory factory = capabilityIndicator.getBarSeries().numFactory();
        this.baseAmount = requirePositiveFinite(baseAmount, "baseAmount", factory);
        this.controlLimit = requirePositiveFinite(controlLimit, "controlLimit", factory);
    }

    @Override
    public Num amount(PositionSizer.Context context) {
        NumFactory factory = context.numFactory();
        Num statistic = capabilityIndicator.getValue(context.entryIndex());
        Num baseAmountValue = factory.produces(baseAmount) ? baseAmount : factory.numOf(baseAmount.doubleValue());
        if (!Num.isFinite(statistic)) {
            return baseAmountValue;
        }
        Num statisticValue = factory.produces(statistic) ? statistic : factory.numOf(statistic.doubleValue());
        if (!Num.isFinite(statisticValue)) {
            return baseAmountValue;
        }
        Num controlLimitValue = factory.produces(controlLimit) ? controlLimit
                : factory.numOf(controlLimit.doubleValue());
        Num standardized = statisticValue.max(factory.zero()).dividedBy(controlLimitValue);
        Num damped = baseAmountValue.multipliedBy(factory.one().dividedBy(factory.one().plus(standardized)));
        if (!Num.isFinite(damped) || damped.isZero()) {
            return factory.epsilon().min(baseAmountValue);
        }
        return damped;
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
