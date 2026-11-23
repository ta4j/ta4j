/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Difference Percentage Indicator.
 *
 * <p>
 * Returns the percentage difference from the last time the
 * {@link #percentageThreshold threshold} was reached. If the threshold is
 * {@code 0} or not specified, only the percentage difference from the previous
 * value is returned.
 *
 * @deprecated Use {@link PercentageChangeIndicator} instead. This class has
 *             been consolidated into {@code PercentageChangeIndicator} which
 *             provides the same functionality with additional features.
 *             <p>
 *             Migration examples:
 *             <ul>
 *             <li>{@code new DifferencePercentageIndicator(indicator)} →
 *             {@code new PercentageChangeIndicator(indicator)}</li>
 *             <li>{@code new DifferencePercentageIndicator(indicator, threshold)}
 *             → {@code new PercentageChangeIndicator(indicator, null,
 *             threshold)}</li>
 *             <li>{@code new DifferencePercentageIndicator(indicator,
 *             previousIndicator)} → {@code new
 *             PercentageChangeIndicator(indicator, previousIndicator)}</li>
 *             <li>{@code new DifferencePercentageIndicator(indicator,
 *             previousIndicator, threshold)} → {@code new
 *             PercentageChangeIndicator(indicator, previousIndicator,
 *             threshold)}</li>
 *             </ul>
 *
 * @since 0.18
 */
@Deprecated
public class DifferencePercentageIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final Num percentageThreshold;
    private static final int MAX_REPLAY_ATTEMPTS = 3;

    private final transient Object notificationLock = new Object();
    private Num lastNotification;
    private transient long notificationVersion;

    private final transient NotificationReplay replay = new NotificationReplay();

    /**
     * Constructor to get the percentage difference from the previous value.
     *
     * @param indicator the {@link Indicator}
     */
    @Deprecated
    public DifferencePercentageIndicator(Indicator<Num> indicator) {
        this(indicator, indicator.getBarSeries().numFactory().zero());
    }

    /**
     * Constructor.
     *
     * @param indicator           the {@link Indicator}
     * @param percentageThreshold the threshold percentage
     */
    @Deprecated
    public DifferencePercentageIndicator(Indicator<Num> indicator, Number percentageThreshold) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(percentageThreshold));
    }

    /**
     * Constructor.
     *
     * @param indicator           the {@link Indicator}
     * @param percentageThreshold the threshold percentage
     */
    @Deprecated
    public DifferencePercentageIndicator(Indicator<Num> indicator, Num percentageThreshold) {
        super(indicator);
        this.indicator = indicator;
        this.percentageThreshold = percentageThreshold;
    }

    @Override
    protected int minimumCacheableIndexAfterHeadAdvance(int firstRetainedIndex) {
        synchronized (notificationLock) {
            lastNotification = null;
            notificationVersion++;
        }
        replay.reset();
        return super.minimumCacheableIndexAfterHeadAdvance(firstRetainedIndex);
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (beginIndex > index) {
            return NaN.NaN;
        }

        Num value = indicator.getValue(index);
        if (value.isNaN() || value.isZero()) {
            return NaN.NaN;
        }

        for (int attempt = 0; attempt < MAX_REPLAY_ATTEMPTS; attempt++) {
            Num notification;
            long version;
            synchronized (notificationLock) {
                notification = lastNotification;
                version = notificationVersion;
            }
            try {
                notification = replay.replay(getBarSeries(), notification, beginIndex, index, this::nextNotification);
            } catch (TransientValueException transientRead) {
                markResultTransient();
                return NaN.NaN;
            }
            if (publishNotification(notification, version)) {
                return notification == null ? NaN.NaN : fractionToPercentage(value.dividedBy(notification));
            }
        }
        // Every replay lost to a concurrent update; a stale result must not be cached.
        markResultTransient();
        return NaN.NaN;
    }

    public void setLastNotification(int index) {
        while (true) {
            Num notification;
            long version;
            synchronized (notificationLock) {
                notification = lastNotification;
                version = notificationVersion;
            }
            try {
                notification = nextNotification(notification, index);
            } catch (TransientValueException transientRead) {
                return;
            }
            if (publishNotification(notification, version)) {
                return;
            }
        }
    }

    // Replays stage state locally and publish only if no other update landed
    // since their snapshot, so neither a failed nor a stale replay can erase one.
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
        if (value.isNaN() || value.isZero()) {
            return notification;
        }
        if (notification == null) {
            notification = value;
        }

        Num changeFraction = value.dividedBy(notification);
        Num changePercentage = fractionToPercentage(changeFraction);

        return changePercentage.abs().isGreaterThanOrEqual(percentageThreshold) ? value : notification;
    }

    @Override
    @Deprecated
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + 1;
    }

    private Num fractionToPercentage(Num changeFraction) {
        final Num hundred = getBarSeries().numFactory().hundred();
        return changeFraction.multipliedBy(hundred).minus(hundred);
    }
}
