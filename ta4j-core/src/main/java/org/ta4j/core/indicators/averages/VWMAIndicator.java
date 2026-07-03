/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import java.util.Objects;
import java.util.function.BiFunction;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Variable Weighted Moving Average (VWMA) indicator.
 *
 * Variable Weighted Moving Average (VWMA) is a type of moving average where
 * each data point's weight is dynamically adjusted based on market conditions
 * or other factors such as price, volume, or volatility. This makes the VWMA a
 * powerful tool for analyzing trends in varying market conditions, offering
 * both responsiveness and stability.
 *
 * VWMA_t = (Sum(Weight_i * Price_i)) / (Sum(Weight_i))
 *
 */
public class VWMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> priceIndicator;
    private final Indicator<Num> volumeIndicator;
    private final Indicator<Num> volumeWeightedIndicator;

    /**
     * Constructor.
     *
     * @param priceIndicator price based indicator
     * @param barCount       the time frame
     */
    public VWMAIndicator(Indicator<Num> priceIndicator, int barCount) {
        this(defaultConfig(priceIndicator, barCount));
    }

    /**
     * Constructor allowing custom averaging strategies.
     *
     * @param priceIndicator  price based indicator
     * @param volumeIndicator volume based indicator
     * @param barCount        the time frame
     * @param averageFactory  factory creating moving-average indicators for the
     *                        provided {@link Indicator} and {@code barCount}
     * @since 0.19
     */
    public VWMAIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator, int barCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> averageFactory) {
        this(validatedConfig(priceIndicator, volumeIndicator, barCount, averageFactory));
    }

    private VWMAIndicator(Config config) {
        super(config.series());
        this.priceIndicator = config.priceIndicator();
        this.volumeIndicator = config.volumeIndicator();
        this.volumeWeightedIndicator = config.volumeWeightedIndicator();
        this.barCount = config.barCount();
    }

    private static Config defaultConfig(Indicator<Num> priceIndicator, int barCount) {
        Indicator<Num> validatedPriceIndicator = Objects.requireNonNull(priceIndicator,
                "priceIndicator must not be null");
        BarSeries series = Objects.requireNonNull(validatedPriceIndicator.getBarSeries(),
                "priceIndicator must reference a bar series");
        return validatedConfig(validatedPriceIndicator, new VolumeIndicator(series), barCount, SMAIndicator::new);
    }

    private static Config validatedConfig(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator, int barCount,
            BiFunction<Indicator<Num>, Integer, Indicator<Num>> averageFactory) {
        Indicator<Num> validatedPriceIndicator = Objects.requireNonNull(priceIndicator,
                "priceIndicator must not be null");
        Indicator<Num> validatedVolumeIndicator = Objects.requireNonNull(volumeIndicator,
                "volumeIndicator must not be null");
        BiFunction<Indicator<Num>, Integer, Indicator<Num>> validatedAverageFactory = Objects
                .requireNonNull(averageFactory, "averageFactory must not be null");
        BarSeries series = Objects.requireNonNull(validatedPriceIndicator.getBarSeries(),
                "priceIndicator must reference a bar series");
        if (series != validatedVolumeIndicator.getBarSeries()) {
            throw new IllegalArgumentException("Price and volume indicators must share the same bar series");
        }

        Indicator<Num> weightedPriceSum = BinaryOperationIndicator.product(validatedPriceIndicator,
                validatedVolumeIndicator);
        Indicator<Num> weightedPriceAverage = Objects.requireNonNull(
                validatedAverageFactory.apply(weightedPriceSum, barCount),
                "averageFactory must return a price-volume average");
        Indicator<Num> volumeAverage = Objects.requireNonNull(
                validatedAverageFactory.apply(validatedVolumeIndicator, barCount),
                "averageFactory must return a volume average");

        Indicator<Num> volumeWeightedIndicator = BinaryOperationIndicator.quotient(weightedPriceAverage, volumeAverage);
        return new Config(series, validatedPriceIndicator, validatedVolumeIndicator, volumeWeightedIndicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return volumeWeightedIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        int baseUnstableBars = Math.max(priceIndicator.getCountOfUnstableBars(),
                volumeIndicator.getCountOfUnstableBars());
        return Math.max(baseUnstableBars, volumeWeightedIndicator.getCountOfUnstableBars());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    private record Config(BarSeries series, Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator,
            Indicator<Num> volumeWeightedIndicator, int barCount) {
    }
}
