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
 * hard cutoff.
 *
 * <p>
 * If the statistic is non-finite at the entry index (for example while the
 * indicator is warming up) the sizer fails open and returns {@code baseAmount}.
 * The capability indicator must be bound to a bar series holding the same bars
 * that are being backtested; entry indexes are evaluated against it without
 * further checks.
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
     *                            must be > 0
     * @param controlLimit        the statistical control limit; must be > 0
     */
    public ProcessCapabilityPositionSizer(Indicator<Num> capabilityIndicator, Number baseAmount, Number controlLimit) {
        this.capabilityIndicator = Objects.requireNonNull(capabilityIndicator, "capabilityIndicator must not be null");
        NumFactory factory = capabilityIndicator.getBarSeries().numFactory();
        this.baseAmount = factory.numOf(requirePositiveFinite(baseAmount, "baseAmount"));
        this.controlLimit = factory.numOf(requirePositiveFinite(controlLimit, "controlLimit"));
    }

    @Override
    public Num amount(PositionSizer.Context context) {
        Num statistic = capabilityIndicator.getValue(context.entryIndex());
        if (!Num.isFinite(statistic)) {
            return baseAmount;
        }
        Num standardized = statistic.max(context.numFactory().zero()).dividedBy(controlLimit);
        return baseAmount
                .multipliedBy(context.numFactory().one().dividedBy(context.numFactory().one().plus(standardized)));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " baseAmount: " + baseAmount + " controlLimit: " + controlLimit;
    }

    private static Number requirePositiveFinite(Number value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        double doubleValue = value.doubleValue();
        if (!Double.isFinite(doubleValue) || doubleValue <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }
}
