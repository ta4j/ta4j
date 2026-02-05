/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import java.util.Objects;
import java.util.function.BiFunction;

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
    private final Indicator<Num> volumeWeightedIndicator;

    /**
     * Constructor.
     *
     * @param priceIndicator price based indicator
     * @param barCount       the time frame
     */
    public VWMAIndicator(Indicator<Num> priceIndicator, int barCount) {
        this(priceIndicator, new VolumeIndicator(priceIndicator.getBarSeries()), barCount, SMAIndicator::new);
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
        super(priceIndicator.getBarSeries());
        Objects.requireNonNull(volumeIndicator, "volumeIndicator must not be null");
        Objects.requireNonNull(averageFactory, "averageFactory must not be null");
        if (priceIndicator.getBarSeries() != volumeIndicator.getBarSeries()) {
            throw new IllegalArgumentException("Price and volume indicators must share the same bar series");
        }

        final var weightedPriceSum = BinaryOperationIndicator.product(priceIndicator, volumeIndicator);
        final var weightedPriceAverage = Objects.requireNonNull(averageFactory.apply(weightedPriceSum, barCount),
                "averageFactory must return a price-volume average");
        final var volumeAverage = Objects.requireNonNull(averageFactory.apply(volumeIndicator, barCount),
                "averageFactory must return a volume average");

        this.volumeWeightedIndicator = BinaryOperationIndicator.quotient(weightedPriceAverage, volumeAverage);
        this.barCount = barCount;
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
        return volumeWeightedIndicator.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
