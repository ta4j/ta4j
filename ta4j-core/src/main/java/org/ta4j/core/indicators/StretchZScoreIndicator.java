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
    private final transient Indicator<Num> deviationIndicator;
    private final transient StandardDeviationIndicator standardDeviationIndicator;
    private final transient ZScoreIndicator zScoreIndicator;
    private final int barCount;

    /**
     * Creates a close-price stretch indicator against a rolling SMA reference.
     *
     * @param series   the bar series
     * @param barCount the rolling window used for the stretch calculation
     * @since 0.22.7
     */
    public StretchZScoreIndicator(BarSeries series, int barCount) {
        this(validatedConfig(series, barCount));
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
        this(validatedConfig(sourceIndicator, barCount));
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
        this(validatedConfig(sourceIndicator, referenceIndicator, barCount));
    }

    private StretchZScoreIndicator(Config config) {
        super(config.series(), identityOfExact(StretchZScoreIndicator.class, config.identityParts()));
        this.sourceIndicator = config.sourceIndicator();
        this.referenceIndicator = config.referenceIndicator();
        this.barCount = config.barCount();
        this.deviationIndicator = config.deviationIndicator();
        this.standardDeviationIndicator = config.standardDeviationIndicator();
        this.zScoreIndicator = config.zScoreIndicator();
    }

    private static Config validatedConfig(BarSeries series, int barCount) {
        return validatedConfig(new ClosePriceIndicator(series), barCount);
    }

    private static Config validatedConfig(Indicator<Num> sourceIndicator, int barCount) {
        return validatedConfig(sourceIndicator, barCount, sourceIndicator, barCount);
    }

    private static Config validatedConfig(Indicator<Num> sourceIndicator, int barCount, Object... identityParts) {
        Indicator<Num> validatedSourceIndicator = Objects.requireNonNull(sourceIndicator, "sourceIndicator");
        return validatedConfig(validatedSourceIndicator, new SMAIndicator(validatedSourceIndicator, barCount), barCount,
                identityParts);
    }

    private static Config validatedConfig(Indicator<Num> sourceIndicator, Indicator<Num> referenceIndicator,
            int barCount) {
        return validatedConfig(sourceIndicator, referenceIndicator, barCount, sourceIndicator, referenceIndicator,
                barCount);
    }

    private static Config validatedConfig(Indicator<Num> sourceIndicator, Indicator<Num> referenceIndicator,
            int barCount, Object... identityParts) {
        BarSeries series = IndicatorUtils.requireSameSeries(sourceIndicator, referenceIndicator);
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be greater than zero");
        }
        Indicator<Num> validatedSourceIndicator = Objects.requireNonNull(sourceIndicator, "sourceIndicator");
        Indicator<Num> validatedReferenceIndicator = Objects.requireNonNull(referenceIndicator, "referenceIndicator");
        Indicator<Num> deviationIndicator = NumericIndicator.of(validatedSourceIndicator)
                .minus(validatedReferenceIndicator);
        StandardDeviationIndicator standardDeviationIndicator = new StandardDeviationIndicator(deviationIndicator,
                barCount);
        ZScoreIndicator zScoreIndicator = new ZScoreIndicator(deviationIndicator, standardDeviationIndicator);
        return new Config(series, validatedSourceIndicator, validatedReferenceIndicator, deviationIndicator,
                standardDeviationIndicator, zScoreIndicator, barCount, identityParts);
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
        Indicator<Num> deviation = NumericIndicator.of(sourceIndicator).minus(referenceIndicator);
        return new StandardDeviationIndicator(deviation, barCount);
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

    private record Config(BarSeries series, Indicator<Num> sourceIndicator, Indicator<Num> referenceIndicator,
            Indicator<Num> deviationIndicator, StandardDeviationIndicator standardDeviationIndicator,
            ZScoreIndicator zScoreIndicator, int barCount, Object[] identityParts) {

        private Config {
            identityParts = identityParts.clone();
        }

        @Override
        public Object[] identityParts() {
            return identityParts.clone();
        }
    }
}
