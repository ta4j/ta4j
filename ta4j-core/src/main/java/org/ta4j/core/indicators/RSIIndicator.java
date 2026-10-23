/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.MMAIndicator;
import org.ta4j.core.indicators.helpers.GainIndicator;
import org.ta4j.core.indicators.helpers.LossIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Relative strength index indicator.
 *
 * <p>
 * Computed using the original Welles Wilder formula.
 */
public class RSIIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final transient MMAIndicator averageGainIndicator;
    private final transient MMAIndicator averageLossIndicator;
    private final int barCount;
    private final transient int unstableBars;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public RSIIndicator(Indicator<Num> indicator, int barCount) {
        this(indicator, barCount, new MMAIndicator(new GainIndicator(indicator), barCount),
                new MMAIndicator(new LossIndicator(indicator), barCount));
    }

    private RSIIndicator(Indicator<Num> indicator, int barCount, MMAIndicator averageGainIndicator,
            MMAIndicator averageLossIndicator) {
        super(averageGainIndicator, averageLossIndicator);
        this.indicator = indicator;
        this.averageGainIndicator = averageGainIndicator;
        this.averageLossIndicator = averageLossIndicator;
        this.barCount = barCount;
        this.unstableBars = barCount + indicator.getCountOfUnstableBars();
    }

    @Override
    protected Num calculate(int index) {
        Num averageGain = averageGainIndicator.getValue(index);
        Num averageLoss = averageLossIndicator.getValue(index);
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        // compute relative strength
        if (Num.isNaNOrNull(averageGain) || Num.isNaNOrNull(averageLoss)) {
            return NaN;
        }
        final var numFactory = getBarSeries().numFactory();
        if (averageLoss.isZero()) {
            return averageGain.isZero() ? numFactory.zero() : numFactory.hundred();
        }
        Num relativeStrength = averageGain.dividedBy(averageLoss);
        // compute relative strength index
        return numFactory.hundred().minus(numFactory.hundred().dividedBy(numFactory.one().plus(relativeStrength)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    /**
     * @return {@code true}, because Wilder averages retain full-history state
     * @since 0.27.1
     */
    @Override
    protected boolean hasRecursiveDependencies() {
        return true;
    }
}
