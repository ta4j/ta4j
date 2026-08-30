/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.averages.EWMAIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import java.util.Objects;

/**
 * Exponentially weighted moving variance (EWMA variance).
 *
 * <p>
 * Tracks the current variance of the monitored indicator with
 *
 * <pre>
 * sigma^2_t = decayFactor * sigma^2_{t-1} + (1 - decayFactor) * (X_t - mu_{t-1})^2
 * </pre>
 *
 * where {@code mu} is an {@link EWMAIndicator} over the same {@code barCount}
 * and {@code decayFactor}, and the recursion is seeded with the rolling
 * population variance {@link VarianceIndicator#ofPopulation(Indicator, int)
 * VarianceIndicator.ofPopulation(indicator, barCount)}. The seed also
 * re-anchors the recursion whenever the previous state is non-finite.
 *
 * <p>
 * The value is {@code NaN} while warming up, with
 * {@code getCountOfUnstableBars() = indicator.getCountOfUnstableBars() +
 * barCount - 1}, and non-finite inputs or extreme regime changes whose derived
 * deviation overflows the numeric representation yield {@code NaN} until the
 * next finite bar re-seeds the variance. Smaller decay factors react faster to
 * volatility changes at the cost of a noisier estimate.
 *
 * <p>
 * Combined with {@link CusumIndicator} this provides the volatility leg of a
 * statistical control-limit kill switch, e.g. the control limit
 * {@code H = NumericIndicator.of(ewmaVariance).sqrt().multipliedBy(h)} with
 * {@code h} around four or five.
 *
 * @since 0.24.2
 */
public class EwmaVarianceIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final double decayFactor;
    private final transient EWMAIndicator meanIndicator;
    private final transient VarianceIndicator initialVarianceIndicator;
    private final transient Num decay;
    private final transient Num oneMinusDecay;

    /**
     * Constructor.
     *
     * @param indicator   the indicator whose variance is monitored
     * @param barCount    the seed window length; must be >= 1
     * @param decayFactor the EWMA decay factor; must be in (0, 1)
     */
    public EwmaVarianceIndicator(Indicator<Num> indicator, int barCount, double decayFactor) {
        super(validateParameters(indicator, barCount, decayFactor));
        this.indicator = indicator;
        this.barCount = barCount;
        this.decayFactor = decayFactor;
        this.meanIndicator = new EWMAIndicator(indicator, barCount, decayFactor);
        this.initialVarianceIndicator = VarianceIndicator.ofPopulation(indicator, barCount);
        this.decay = indicator.getBarSeries().numFactory().numOf(decayFactor);
        this.oneMinusDecay = indicator.getBarSeries().numFactory().one().minus(this.decay);
    }

    private static Indicator<Num> validateParameters(Indicator<Num> indicator, int barCount, double decayFactor) {
        Objects.requireNonNull(indicator, "indicator must not be null");
        if (indicator.getBarSeries() == null) {
            throw new IllegalArgumentException("indicator must be bound to a BarSeries");
        }
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be >= 1");
        }
        if (Double.isNaN(decayFactor) || decayFactor <= 0 || decayFactor >= 1) {
            throw new IllegalArgumentException("decayFactor must be in (0, 1)");
        }

        int unstableBars = indicator.getCountOfUnstableBars();
        if (barCount - 1 > Integer.MAX_VALUE - unstableBars) {
            throw new IllegalArgumentException("barCount must not overflow the unstable-bar count");
        }
        return indicator;
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (index - beginIndex < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        if (index == beginIndex) {
            Num current = indicator.getValue(index);
            if (!Num.isFinite(current)) {
                return NaN.NaN;
            }
            return initialVarianceIndicator.getValue(index);
        }
        Num current = indicator.getValue(index);
        if (!Num.isFinite(current)) {
            return NaN.NaN;
        }
        Num previousVariance = getValue(index - 1);
        Num previousMean = meanIndicator.getValue(index - 1);
        if (!Num.isFinite(previousVariance) || !Num.isFinite(previousMean)) {
            return initialVarianceIndicator.getValue(index);
        }
        Num deviation = current.minus(previousMean);
        Num updatedVariance = previousVariance.multipliedBy(decay)
                .plus(deviation.multipliedBy(deviation).multipliedBy(oneMinusDecay));
        return Num.isFinite(deviation) && Num.isFinite(updatedVariance) ? updatedVariance : NaN.NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " decayFactor: " + decayFactor;
    }
}
