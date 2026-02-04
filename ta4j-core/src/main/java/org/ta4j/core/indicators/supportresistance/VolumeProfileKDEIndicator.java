/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supportresistance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Estimates the smoothed volume-at-price profile for a bar series using a
 * kernel density estimate (KDE).
 *
 * <p>
 * The indicator evaluates the KDE at the current bar's price to expose the
 * liquidity available near that level. For detailed profiling, callers can
 * query the density at any price or ask for the most liquid price in the
 * look-back window. See <a href=
 * "https://www.investopedia.com/terms/v/volume-profile.asp">Investopedia:
 * Volume Profile</a> for an overview of the trading concept.
 *
 * @since 0.22.2
 */
public class VolumeProfileKDEIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> priceIndicator;
    private final Indicator<Num> volumeIndicator;
    private final int lookbackLength;
    private final Num bandwidth;
    private final transient boolean gaussianKernel;
    private final transient double gaussianBandwidth;
    private final transient double gaussianCoefficient;

    /**
     * Constructor using {@link ClosePriceIndicator}, {@link VolumeIndicator} with
     * {@code barCount = 1}, unlimited history and tolerance-derived bandwidth.
     *
     * @param series    the backing bar series
     * @param bandwidth the kernel bandwidth (non-negative)
     * @since 0.22.2
     */
    public VolumeProfileKDEIndicator(BarSeries series, Num bandwidth) {
        this(new ClosePriceIndicator(series), new VolumeIndicator(series, 1), 0, bandwidth);
    }

    /**
     * Constructor using custom price and volume indicators.
     *
     * @param priceIndicator  the price samples used for the KDE
     * @param volumeIndicator the volume weights associated with each price
     * @param lookbackLength  number of bars to consider (non-positive for the full
     *                        history)
     * @param bandwidth       kernel bandwidth (non-negative)
     * @since 0.22.2
     */
    public VolumeProfileKDEIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator, int lookbackLength,
            Num bandwidth) {
        super(priceIndicator);
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator must not be null");
        this.volumeIndicator = Objects.requireNonNull(volumeIndicator, "volumeIndicator must not be null");
        BarSeries series = Objects.requireNonNull(priceIndicator.getBarSeries(),
                "priceIndicator must reference a bar series");
        if (volumeIndicator.getBarSeries() != series) {
            throw new IllegalArgumentException("volumeIndicator must share the same bar series as priceIndicator");
        }
        this.lookbackLength = lookbackLength;
        this.bandwidth = Objects.requireNonNull(bandwidth, "bandwidth must not be null");
        if (isInvalid(bandwidth) || bandwidth.isLessThan(series.numFactory().zero())) {
            throw new IllegalArgumentException("bandwidth must be greater than or equal to zero");
        }
        this.gaussianKernel = !bandwidth.isZero();
        if (gaussianKernel) {
            this.gaussianBandwidth = bandwidth.doubleValue();
            this.gaussianCoefficient = 1d / (gaussianBandwidth * Math.sqrt(2d * Math.PI));
        } else {
            this.gaussianBandwidth = Double.NaN;
            this.gaussianCoefficient = Double.NaN;
        }
    }

    @Override
    protected Num calculate(int index) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return NaN;
        }
        Num price = priceIndicator.getValue(index);
        if (isInvalid(price)) {
            return NaN;
        }
        List<Sample> samples = collectSamples(index);
        if (samples.isEmpty()) {
            return NaN;
        }
        return evaluateDensity(samples, price);
    }

    /**
     * Returns the KDE density evaluated at the supplied price.
     *
     * @param index the bar index
     * @param price the price to evaluate
     * @return the estimated density or {@code NaN} when unavailable
     * @since 0.22.2
     */
    public Num getDensityAtPrice(int index, Num price) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return NaN;
        }
        if (isInvalid(price)) {
            return NaN;
        }
        List<Sample> samples = collectSamples(index);
        if (samples.isEmpty()) {
            return NaN;
        }
        return evaluateDensity(samples, price);
    }

    /**
     * Returns the price level with the highest estimated liquidity in the look-back
     * window.
     *
     * <p>
     * Ties prefer the lower price to reflect how traders often anchor to the lower
     * bound of a high-volume node when mapping potential support zones.
     *
     * @param index the bar index
     * @return the modal price or {@code NaN} when no samples are available
     * @since 0.22.2
     */
    public Num getModePrice(int index) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return NaN;
        }
        List<Sample> samples = collectSamples(index);
        if (samples.isEmpty()) {
            return NaN;
        }
        Num bestPrice = null;
        Num bestDensity = null;
        for (Sample sample : samples) {
            Num density = evaluateDensity(samples, sample.price);
            if (bestDensity == null || density.isGreaterThan(bestDensity)) {
                bestDensity = density;
                bestPrice = sample.price;
            } else if (density.isEqual(bestDensity) && bestPrice != null && sample.price.isLessThan(bestPrice)) {
                bestPrice = sample.price;
            }
        }
        return bestPrice == null ? NaN : bestPrice;
    }

    private List<Sample> collectSamples(int index) {
        BarSeries series = getBarSeries();
        if (series == null || index < series.getBeginIndex()) {
            return List.of();
        }
        int startIndex = computeStartIndex(index, series);
        List<Sample> samples = new ArrayList<>();
        for (int i = startIndex; i <= index; i++) {
            Num price = priceIndicator.getValue(i);
            Num volume = volumeIndicator.getValue(i);
            if (isInvalid(price) || isInvalid(volume)) {
                continue;
            }
            Num weight = volume.abs();
            if (weight.isZero()) {
                continue;
            }
            samples.add(new Sample(price, weight));
        }
        return samples;
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(0, lookbackLength - 1);
    }

    private int computeStartIndex(int index, BarSeries series) {
        if (lookbackLength <= 0) {
            return series.getBeginIndex();
        }
        int desiredStart = index - lookbackLength + 1;
        return Math.max(series.getBeginIndex(), desiredStart);
    }

    private Num evaluateDensity(List<Sample> samples, Num price) {
        NumFactory factory = getBarSeries().numFactory();
        if (gaussianKernel) {
            double priceValue = price.doubleValue();
            double density = 0d;
            for (Sample sample : samples) {
                double diff = priceValue - sample.priceValue;
                double exponent = -0.5d * Math.pow(diff / gaussianBandwidth, 2d);
                double kernel = gaussianCoefficient * Math.exp(exponent);
                density += sample.weightValue * kernel;
            }
            return factory.numOf(density);
        }
        Num density = factory.zero();
        for (Sample sample : samples) {
            if (price.minus(sample.price).isZero()) {
                density = density.plus(sample.weight);
            }
        }
        return density;
    }

    private static final class Sample {
        private final Num price;
        private final Num weight;
        private final double priceValue;
        private final double weightValue;

        private Sample(Num price, Num weight) {
            this.price = price;
            this.weight = weight;
            this.priceValue = price.doubleValue();
            this.weightValue = weight.doubleValue();
        }
    }

    private static boolean isInvalid(Num value) {
        if (Num.isNaNOrNull(value)) {
            return true;
        }
        return Double.isNaN(value.doubleValue());
    }
}
