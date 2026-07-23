/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.MMAIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Average true range indicator.
 */
public class ATRIndicator extends AbstractIndicator<Num> {

    private final TRIndicator tr;
    private final transient int trueRangeUnstableBars;
    private final transient MMAIndicator averageTrueRangeIndicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public ATRIndicator(BarSeries series, int barCount) {
        this(new TRIndicator(series), barCount);
    }

    /**
     * Constructor.
     *
     * @param tr       the {@link TRIndicator}
     * @param barCount the time frame
     */
    public ATRIndicator(TRIndicator tr, int barCount) {
        this(validatedConfig(tr, barCount));
    }

    private ATRIndicator(Config config) {
        super(config.trueRangeIndicator().getBarSeries(),
                identityOfExact(ATRIndicator.class, config.trueRangeIndicator(), config.barCount()));
        this.tr = config.trueRangeIndicator();
        this.trueRangeUnstableBars = config.trueRangeUnstableBars();
        this.barCount = config.barCount();
        this.averageTrueRangeIndicator = new MMAIndicator(config.trueRangeIndicator(), config.barCount());
    }

    private static Config validatedConfig(TRIndicator tr, int barCount) {
        TRIndicator validatedTrueRange = Objects.requireNonNull(tr, "tr");
        return new Config(validatedTrueRange, validatedTrueRange.getCountOfUnstableBars(), barCount);
    }

    @Override
    public Num getValue(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return averageTrueRangeIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return trueRangeUnstableBars + getBarCount();
    }

    /** @return a true range indicator for this indicator's bar series */
    public TRIndicator getTRIndicator() {
        return new TRIndicator(getBarSeries());
    }

    /** @return the bar count of {@link #averageTrueRangeIndicator} */
    public int getBarCount() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + getBarCount();
    }

    private record Config(TRIndicator trueRangeIndicator, int trueRangeUnstableBars, int barCount) {
    }
}
