/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.statistics.ZScoreIndicator;
import org.ta4j.core.num.Num;

/**
 * Rolling z-score that measures how stretched an indicator is versus a
 * reference signal.
 *
 * <p>
 * The indicator computes the deviation between a source and reference
 * indicator, then normalizes that deviation by the rolling standard deviation
 * of the deviation series. Positive values indicate the source is extended
 * above the reference, negative values indicate it is extended below the
 * reference, and values near zero indicate little stretch.
 * </p>
 *
 * <p>
 * The default convenience constructors use close price and a simple moving
 * average, but callers may supply any source/reference pair such as VWAP,
 * anchored VWAP, or a band midpoint.
 * </p>
 *
 * @since 0.22.7
 */
public class StretchZScoreIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> sourceIndicator;
    private final Indicator<Num> referenceIndicator;
    private final Indicator<Num> deviationIndicator;
    private final StandardDeviationIndicator standardDeviationIndicator;
    private final ZScoreIndicator zScoreIndicator;
    private final int barCount;

    /**
     * Creates a close-price stretch indicator against a rolling SMA reference.
     *
     * @param series   the bar series
     * @param barCount the rolling window used for the stretch calculation
     * @since 0.22.7
     */
    public StretchZScoreIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    /**
     * Creates a stretch indicator against a rolling SMA reference built from the
     * supplied source indicator.
     *
     * @param sourceIndicator the source indicator to normalize
     * @param barCount        the rolling window used for the stretch calculation
     * @since 0.22.7
     */
    public StretchZScoreIndicator(Indicator<Num> sourceIndicator, int barCount) {
        this(sourceIndicator, new SMAIndicator(Objects.requireNonNull(sourceIndicator, "sourceIndicator"), barCount),
                barCount);
    }

    /**
     * Creates a stretch indicator for a custom source/reference pair.
     *
     * @param sourceIndicator    the indicator whose stretch should be measured
     * @param referenceIndicator the indicator acting as the reference level
     * @param barCount           the rolling window used for the stretch calculation
     * @since 0.22.7
     */
    public StretchZScoreIndicator(Indicator<Num> sourceIndicator, Indicator<Num> referenceIndicator, int barCount) {
        super(IndicatorUtils.requireSameSeries(sourceIndicator, referenceIndicator));
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be greater than zero");
        }
        this.sourceIndicator = Objects.requireNonNull(sourceIndicator, "sourceIndicator");
        this.referenceIndicator = Objects.requireNonNull(referenceIndicator, "referenceIndicator");
        this.barCount = barCount;
        this.deviationIndicator = NumericIndicator.of(sourceIndicator).minus(referenceIndicator);
        this.standardDeviationIndicator = new StandardDeviationIndicator(deviationIndicator, barCount);
        this.zScoreIndicator = new ZScoreIndicator(deviationIndicator, standardDeviationIndicator);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    protected Num calculate(int index) {
        return zScoreIndicator.getValue(index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    public int getCountOfUnstableBars() {
        return zScoreIndicator.getCountOfUnstableBars();
    }

    /**
     * @return the source indicator being normalized
     * @since 0.22.7
     */
    public Indicator<Num> getSourceIndicator() {
        return sourceIndicator;
    }

    /**
     * @return the reference indicator used as the stretch anchor
     * @since 0.22.7
     */
    public Indicator<Num> getReferenceIndicator() {
        return referenceIndicator;
    }

    /**
     * @return the deviation indicator ({@code source - reference})
     * @since 0.22.7
     */
    public Indicator<Num> getDeviationIndicator() {
        return deviationIndicator;
    }

    /**
     * @return the rolling standard deviation of the deviation series
     * @since 0.22.7
     */
    public StandardDeviationIndicator getStandardDeviationIndicator() {
        return standardDeviationIndicator;
    }

    /**
     * @return the rolling bar count used for the stretch calculation
     * @since 0.22.7
     */
    public int getBarCount() {
        return barCount;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
