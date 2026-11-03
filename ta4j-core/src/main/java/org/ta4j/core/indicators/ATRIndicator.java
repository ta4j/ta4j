/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.AbstractEMAIndicator;
import org.ta4j.core.indicators.averages.MMAIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Average true range indicator.
 */
public class ATRIndicator extends CachedIndicator<Num> {

    /**
     * Logical source retained so descriptor serialization preserves custom price
     * inputs.
     */
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
        this(config, new MMAIndicator(config.trueRangeIndicator(), config.barCount()));
    }

    private ATRIndicator(Config config, MMAIndicator averageTrueRangeIndicator) {
        super(averageTrueRangeIndicator);
        this.tr = config.trueRangeIndicator();
        this.trueRangeUnstableBars = config.trueRangeUnstableBars();
        this.barCount = config.barCount();
        this.averageTrueRangeIndicator = averageTrueRangeIndicator;
    }

    private static Config validatedConfig(TRIndicator tr, int barCount) {
        TRIndicator validatedTrueRange = Objects.requireNonNull(tr, "tr");
        return new Config(validatedTrueRange, validatedTrueRange.getCountOfUnstableBars(), barCount);
    }

    @Override
    protected Num calculate(int index) {
        Num averageTrueRange = averageTrueRangeIndicator.getValue(index);
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return averageTrueRange;
    }

    @Override
    public int getCountOfUnstableBars() {
        return trueRangeUnstableBars + getBarCount();
    }

    /**
     * @return {@code true}, because the Wilder average retains full-history state
     * @since 0.27.1
     */
    @Override
    protected boolean hasRecursiveDependencies() {
        return true;
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
