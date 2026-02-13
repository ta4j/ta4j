/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Z-score indicator.
 *
 * <p>
 * Calculates the z-score for an observation relative to a reference mean and
 * standard deviation:
 *
 * <pre>
 * z = deviation / standardDeviation
 * </pre>
 *
 * <p>
 * The inputs are supplied as indicators so callers can plug in any deviation
 * definition (for example, {@code price - SMA}) and any standard deviation
 * definition. The z-score is undefined when the standard deviation is zero or
 * when either input is {@code NaN}.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Standard_score">Wikipedia:
 *      Standard score</a>
 * @since 0.22.2
 */
public class ZScoreIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> deviationIndicator;
    private final Indicator<Num> standardDeviationIndicator;

    /**
     * Constructor.
     *
     * @param deviationIndicator         deviation from the reference mean (for
     *                                   example {@code value - mean})
     * @param standardDeviationIndicator standard deviation of the observation set
     * @since 0.22.2
     */
    public ZScoreIndicator(Indicator<Num> deviationIndicator, Indicator<Num> standardDeviationIndicator) {
        super(requireSameSeries(deviationIndicator, standardDeviationIndicator));
        this.deviationIndicator = Objects.requireNonNull(deviationIndicator, "deviationIndicator must not be null");
        this.standardDeviationIndicator = Objects.requireNonNull(standardDeviationIndicator,
                "standardDeviationIndicator must not be null");
    }

    /**
     * Calculates the indicator value at the requested index.
     */
    @Override
    protected Num calculate(int index) {
        BarSeries series = getBarSeries();
        if (series == null || index < series.getBeginIndex()) {
            return NaN.NaN;
        }
        if (index < series.getBeginIndex() + getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num deviation = deviationIndicator.getValue(index);
        Num standardDeviation = standardDeviationIndicator.getValue(index);
        if (Num.isNaNOrNull(deviation) || Num.isNaNOrNull(standardDeviation) || standardDeviation.isZero()) {
            return NaN.NaN;
        }
        return deviation.dividedBy(standardDeviation);
    }

    /**
     * Returns the number of unstable bars required before values become reliable.
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(deviationIndicator.getCountOfUnstableBars(),
                standardDeviationIndicator.getCountOfUnstableBars());
    }

    /**
     * Returns a string representation of this component.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " deviation: " + deviationIndicator + " std: " + standardDeviationIndicator;
    }

    private static BarSeries requireSameSeries(Indicator<?> deviationIndicator,
            Indicator<?> standardDeviationIndicator) {
        Objects.requireNonNull(deviationIndicator, "deviationIndicator must not be null");
        Objects.requireNonNull(standardDeviationIndicator, "standardDeviationIndicator must not be null");
        BarSeries series = Objects.requireNonNull(deviationIndicator.getBarSeries(),
                "deviationIndicator must reference a bar series");
        if (!Objects.equals(series, standardDeviationIndicator.getBarSeries())) {
            throw new IllegalArgumentException("Indicators must share the same bar series");
        }
        return series;
    }
}
