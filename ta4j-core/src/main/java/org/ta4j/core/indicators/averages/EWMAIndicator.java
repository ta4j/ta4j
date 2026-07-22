/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.frequency.SampleSummary;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Exponentially weighted moving average indicator with an explicit decay
 * factor.
 *
 * <p>
 * The update is {@code previous * decayFactor + current * (1 - decayFactor)}.
 * The first stable value is initialized with a simple moving average over the
 * configured warm-up window.
 *
 * @since 0.22.9
 */
public class EWMAIndicator extends AbstractEMAIndicator {

    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator   source indicator
     * @param barCount    number of observations used for initialization
     * @param decayFactor EWMA decay factor in {@code (0, 1)}
     * @since 0.22.9
     */
    public EWMAIndicator(Indicator<Num> indicator, int barCount, double decayFactor) {
        super(EWMAIndicator.class, validateIndicator(indicator), validateBarCount(barCount),
                1d - validateDecayFactor(decayFactor));
        this.indicator = indicator;
    }

    @Override
    protected Num initialValue(int index, Num current) {
        List<Num> values = new ArrayList<>(getBarCount());
        int startIndex = index - getBarCount() + 1;
        for (int i = startIndex; i <= index; i++) {
            Num value = indicator.getValue(i);
            if (!Num.isFinite(value)) {
                return NaN.NaN;
            }
            values.add(value);
        }
        return SampleSummary.fromValues(values.stream(), getBarSeries().numFactory()).mean();
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + getBarCount() - 1;
    }

    private static Indicator<Num> validateIndicator(Indicator<Num> indicator) {
        return Objects.requireNonNull(indicator, "indicator must not be null");
    }

    private static int validateBarCount(int barCount) {
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be >= 1");
        }
        return barCount;
    }

    private static double validateDecayFactor(double decayFactor) {
        if (Double.isNaN(decayFactor) || decayFactor <= 0d || decayFactor >= 1d) {
            throw new IllegalArgumentException("decayFactor must be in (0, 1)");
        }
        return decayFactor;
    }
}
