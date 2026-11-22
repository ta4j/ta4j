/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Calculates the percentage change between the current and the previous value
 * of an indicator.
 *
 * <p>
 * This indicator uses <strong>rate of return</strong> representation
 * (additive). The formula calculates the percentage change as:
 *
 * <pre>
 * PercentageChange = ((currentValue - previousValue) / previousValue) * 100
 * </pre>
 *
 * <p>
 * This is equivalent to:
 *
 * <pre>
 * PercentageChange = (currentValue / previousValue - 1) * 100
 * </pre>
 *
 * <p>
 * <strong>Representation:</strong> Existing constructors return percentages,
 * independently of the global return representation policy. Constructors
 * accepting a {@link ReturnRepresentation} can instead return a decimal rate, a
 * multiplicative factor, or a logarithmic return. For a change from 100 to 110
 * these are respectively 0.1, 1.1, and ln(1.1), rather than 10. Non-finite
 * source values or arithmetic that cannot represent a return produce
 * {@code NaN}. An unrepresentable threshold comparison keeps the previous
 * notification as the baseline.
 *
 * <p>
 * <strong>Optional features:</strong>
 * <ul>
 * <li><strong>Previous indicator:</strong> When {@link #previousIndicator} is
 * specified, the percentage change is calculated between the current value of
 * {@link #indicator} and the previous value of {@link #previousIndicator} (at
 * index - 1). This allows comparing different indicators, such as comparing the
 * current open price with the previous close price.</li>
 * <li><strong>Percentage threshold:</strong> When {@link #percentageThreshold}
 * is greater than zero, the indicator tracks the last time the threshold was
 * exceeded and calculates the change from that point instead of from the
 * previous bar. The threshold is always a percentage, regardless of the output
 * representation.</li>
 * </ul>
 *
 * @since 0.20
 */
public class PercentageChangeIndicator extends CachedIndicator<Num> {

    private static final Indicator<?>[] NO_ADDITIONAL_SOURCES = new Indicator<?>[0];
    private static final int MAX_REPLAY_ATTEMPTS = 3;

    private final Indicator<Num> indicator;
    private final Indicator<Num> previousIndicator;
    private final Num percentageThreshold;
    private final ReturnRepresentation returnRepresentation;
    private final transient Object notificationLock = new Object();
    // Runtime replay state is not a constructor parameter.
    private transient Num lastNotification;
    private transient long notificationVersion;

    /**
     * Constructor to calculate percentage change from the previous value.
     *
     * @param indicator the indicator to calculate the percentage change for
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator) {
        this(indicator, null, indicator.getBarSeries().numFactory().zero());
    }

    /**
     * Constructs an indicator with the requested return representation.
     *
     * @param indicator            the indicator to compare with its previous value
     * @param returnRepresentation the non-null output representation
     * @since 0.29.1
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, ReturnRepresentation returnRepresentation) {
        this(indicator, null, indicator.getBarSeries().numFactory().zero(), returnRepresentation);
    }

    /**
     * Constructor with percentage threshold.
     * <p>
     * When {@code percentageThreshold} is greater than zero, the indicator tracks
     * the last time the threshold was exceeded and calculates the percentage change
     * from that point.
     *
     * @param indicator           the indicator to calculate the percentage change
     *                            for
     * @param percentageThreshold the threshold percentage. If greater than zero,
     *                            tracks changes from the last time this threshold
     *                            was exceeded. If zero, calculates from the
     *                            previous bar.
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Num percentageThreshold) {
        this(indicator, null, percentageThreshold);
    }

    /**
     * Constructor with percentage threshold (Number overload).
     *
     * @param indicator           the indicator to calculate the percentage change
     *                            for
     * @param percentageThreshold the threshold percentage. If greater than zero,
     *                            tracks changes from the last time this threshold
     *                            was exceeded. If zero, calculates from the
     *                            previous bar.
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Number percentageThreshold) {
        this(indicator, null, indicator.getBarSeries().numFactory().numOf(percentageThreshold));
    }

    /**
     * Constructs an indicator with a percentage threshold and output
     * representation.
     *
     * @param indicator            the indicator to calculate the change for
     * @param percentageThreshold  the threshold in percentage points
     * @param returnRepresentation the non-null output representation
     * @since 0.29.1
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Num percentageThreshold,
            ReturnRepresentation returnRepresentation) {
        this(indicator, null, percentageThreshold, returnRepresentation);
    }

    /**
     * Constructs an indicator with a numeric percentage threshold.
     *
     * @param indicator            the indicator to calculate the change for
     * @param percentageThreshold  the threshold in percentage points
     * @param returnRepresentation the non-null output representation
     * @since 0.29.1
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Number percentageThreshold,
            ReturnRepresentation returnRepresentation) {
        this(indicator, null, indicator.getBarSeries().numFactory().numOf(percentageThreshold), returnRepresentation);
    }

    /**
     * Constructor with optional previous indicator.
     * <p>
     * Calculates the percentage change between the current value of
     * {@code indicator} and the previous value of {@code previousIndicator} (at
     * index - 1).
     *
     * @param indicator         the indicator to calculate the percentage change for
     * @param previousIndicator the indicator to use for the previous value (at
     *                          index - 1). If {@code null}, uses the previous value
     *                          of {@code indicator}.
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Indicator<Num> previousIndicator) {
        this(indicator, previousIndicator, indicator.getBarSeries().numFactory().zero());
    }

    /**
     * Constructor with optional previous indicator and percentage threshold.
     * <p>
     * When {@code percentageThreshold} is greater than zero, the indicator tracks
     * the last time the threshold was exceeded and calculates the percentage change
     * from that point.
     *
     * @param indicator           the indicator to calculate the percentage change
     *                            for
     * @param previousIndicator   the indicator to use for the previous value (at
     *                            index - 1). If {@code null}, uses the previous
     *                            value of {@code indicator}.
     * @param percentageThreshold the threshold percentage. If greater than zero,
     *                            tracks changes from the last time this threshold
     *                            was exceeded. If zero, calculates from the
     *                            previous bar.
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Indicator<Num> previousIndicator,
            Num percentageThreshold) {
        this(indicator, previousIndicator, percentageThreshold, ReturnRepresentation.PERCENTAGE);
    }

    /**
     * Constructor with optional previous indicator and percentage threshold (Number
     * overload).
     *
     * @param indicator           the indicator to calculate the percentage change
     *                            for
     * @param previousIndicator   the indicator to use for the previous value (at
     *                            index - 1). If {@code null}, uses the previous
     *                            value of {@code indicator}.
     * @param percentageThreshold the threshold percentage. If greater than zero,
     *                            tracks changes from the last time this threshold
     *                            was exceeded. If zero, calculates from the
     *                            previous bar.
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Indicator<Num> previousIndicator,
            Number percentageThreshold) {
        this(indicator, previousIndicator, indicator.getBarSeries().numFactory().numOf(percentageThreshold));
    }

    /**
     * Constructs an indicator with an optional previous source, percentage
     * threshold, and output representation.
     *
     * @param indicator            the indicator to calculate the change for
     * @param previousIndicator    the previous-value source, or null to use the
     *                             same indicator
     * @param percentageThreshold  the threshold in percentage points
     * @param returnRepresentation the non-null output representation
     * @since 0.29.1
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Indicator<Num> previousIndicator,
            Num percentageThreshold, ReturnRepresentation returnRepresentation) {
        super(indicator, previousIndicator == null ? NO_ADDITIONAL_SOURCES : new Indicator<?>[] { previousIndicator });
        this.indicator = indicator;
        this.previousIndicator = previousIndicator;
        this.percentageThreshold = percentageThreshold;
        this.returnRepresentation = Objects.requireNonNull(returnRepresentation, "returnRepresentation");
    }

    /**
     * Constructs an indicator with an optional previous source and numeric
     * percentage threshold.
     *
     * @param indicator            the indicator to calculate the change for
     * @param previousIndicator    the previous-value source, or null to use the
     *                             same indicator
     * @param percentageThreshold  the threshold in percentage points
     * @param returnRepresentation the non-null output representation
     * @since 0.29.1
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Indicator<Num> previousIndicator,
            Number percentageThreshold, ReturnRepresentation returnRepresentation) {
        this(indicator, previousIndicator, indicator.getBarSeries().numFactory().numOf(percentageThreshold),
                returnRepresentation);
    }

    /**
     * Calculates the change between the current and previous values in the
     * configured return representation.
     *
     * @param index the index of the current bar
     * @return the represented return, or NaN if the value is not defined
     */
    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (beginIndex > index) {
            return NaN;
        }

        Num currentValue = indicator.getValue(index);
        Indicator<Num> sourceForPrevious = previousIndicator != null ? previousIndicator : indicator;
        Num previousValue = null;
        if (percentageThreshold.isZero() && index > beginIndex) {
            previousValue = sourceForPrevious.getValue(index - 1);
        }

        // Return NaN during unstable period
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        if (!Num.isFinite(currentValue) || currentValue.isZero()) {
            return NaN;
        }

        // If threshold tracking is enabled, calculate all previous values to get
        // the correct last notification value for this index
        if (!percentageThreshold.isZero()) {
            for (int attempt = 0; attempt < MAX_REPLAY_ATTEMPTS; attempt++) {
                Num notification;
                long version;
                synchronized (notificationLock) {
                    notification = lastNotification;
                    version = notificationVersion;
                }
                try {
                    for (int i = beginIndex; i < index; i++) {
                        notification = nextNotification(notification, i);
                    }
                } catch (TransientValueException transientRead) {
                    markResultTransient();
                    return NaN;
                }
                if (publishNotification(notification, version)) {
                    if (notification == null) {
                        return NaN;
                    }
                    return calculateReturn(currentValue, notification, true);
                }
            }
            // Every replay lost to an overlapping one; a stale result must not be
            // cached.
            markResultTransient();
            return NaN;
        }

        // Simple case: compare with previous bar

        if (previousValue == null) {
            previousValue = sourceForPrevious.getValue(index - 1);
        }

        if (!Num.isFinite(previousValue) || previousValue.isZero()) {
            return NaN;
        }

        return calculateReturn(currentValue, previousValue, false);
    }

    private Num calculateReturn(Num currentValue, Num baseline, boolean thresholdBased) {
        try {
            Num result = switch (returnRepresentation) {
            case PERCENTAGE -> thresholdBased ? fractionToPercentage(currentValue.dividedBy(baseline))
                    : calculateRateOfReturnPercentage(currentValue, baseline);
            case DECIMAL -> currentValue.minus(baseline).dividedBy(baseline);
            case MULTIPLICATIVE, LOG ->
                returnRepresentation.toRepresentationFromTotalReturn(currentValue.dividedBy(baseline));
            };
            return Num.isFinite(result) ? result : NaN;
        } catch (ArithmeticException ex) {
            return NaN;
        }
    }

    // Overlapping replays stage state locally and publish only if no other
    // replay landed since their snapshot; a failed replay publishes nothing.
    private boolean publishNotification(Num notification, long version) {
        synchronized (notificationLock) {
            if (notificationVersion != version) {
                return false;
            }
            lastNotification = notification;
            notificationVersion++;
            return true;
        }
    }

    private Num nextNotification(Num notification, int index) {
        Num value = readNonTransient(indicator::getValue, index);
        if (!Num.isFinite(value) || value.isZero()) {
            return notification;
        }
        if (notification == null) {
            return value;
        }

        try {
            Num changePercentage = fractionToPercentage(value.dividedBy(notification));
            return Num.isFinite(changePercentage) && changePercentage.abs().isGreaterThanOrEqual(percentageThreshold)
                    ? value
                    : notification;
        } catch (ArithmeticException ex) {
            return notification;
        }
    }

    /**
     * Converts a change fraction to a percentage change.
     *
     * @param changeFraction the fraction representing the change (e.g., 1.1 for 10%
     *                       increase)
     * @return the percentage change (e.g., 10.0 for 10% increase)
     */
    private Num fractionToPercentage(Num changeFraction) {
        final var hundred = getBarSeries().numFactory().hundred();
        return changeFraction.multipliedBy(hundred).minus(hundred);
    }

    /**
     * Calculates the rate of return as a percentage.
     *
     * @param currentValue  the current indicator value
     * @param previousValue the previous indicator value
     * @return the rate of return as a percentage (e.g., 10.0 for a 10% gain)
     */
    protected Num calculateRateOfReturnPercentage(Num currentValue, Num previousValue) {
        // Rate of return: ((current - previous) / previous) * 100
        // Equivalent to: (current / previous - 1) * 100
        Num change = currentValue.minus(previousValue);
        Num changeFraction = change.dividedBy(previousValue);
        return changeFraction.multipliedBy(getBarSeries().numFactory().hundred());
    }

    /** @return {@code 1} */
    @Override
    public int getCountOfUnstableBars() {
        int indicatorUnstableBars = indicator.getCountOfUnstableBars();
        int previousUnstableBars = previousIndicator == null ? indicatorUnstableBars
                : previousIndicator.getCountOfUnstableBars();
        return Math.max(indicatorUnstableBars, previousUnstableBars + 1);
    }
}
