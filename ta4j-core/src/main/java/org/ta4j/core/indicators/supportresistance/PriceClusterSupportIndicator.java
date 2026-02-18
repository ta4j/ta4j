/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supportresistance;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Estimates support levels by clustering bar closes within a configurable
 * tolerance.
 *
 * @since 0.22.3
 */
public class PriceClusterSupportIndicator extends AbstractPriceClusterIndicator {

    @SuppressWarnings("unused")
    private final Num bandwidth;

    /**
     * Constructor using {@link ClosePriceIndicator}, unlimited history and zero
     * tolerance.
     *
     * @param series the backing bar series
     * @since 0.22.3
     */
    public PriceClusterSupportIndicator(BarSeries series) {
        this(new ClosePriceIndicator(series), new VolumeIndicator(series, 1), 0, series.numFactory().zero(),
                series.numFactory().zero());
    }

    /**
     * Constructor using {@link ClosePriceIndicator} and the provided parameters.
     *
     * @param series        the backing bar series
     * @param lookbackCount the number of bars to evaluate (non-positive for the
     *                      full history)
     * @param tolerance     the absolute tolerance for bucket membership
     * @since 0.22.3
     */
    public PriceClusterSupportIndicator(BarSeries series, int lookbackCount, Num tolerance) {
        this(new ClosePriceIndicator(series), new VolumeIndicator(series, 1), lookbackCount, tolerance, tolerance);
    }

    /**
     * Constructor using a custom price indicator, unlimited history and zero
     * tolerance.
     *
     * @param priceIndicator the price indicator to cluster
     * @since 0.22.3
     */
    public PriceClusterSupportIndicator(Indicator<Num> priceIndicator) {
        this(priceIndicator, defaultVolumeIndicator(priceIndicator), 0, zeroTolerance(priceIndicator),
                zeroTolerance(priceIndicator));
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator to cluster
     * @param lookbackCount  the number of bars to evaluate (non-positive for the
     *                       full history)
     * @param tolerance      the absolute tolerance for bucket membership
     * @since 0.22.3
     */
    public PriceClusterSupportIndicator(Indicator<Num> priceIndicator, int lookbackCount, Num tolerance) {
        this(priceIndicator, defaultVolumeIndicator(priceIndicator), lookbackCount, tolerance, tolerance);
    }

    /**
     * Constructor using a custom weight indicator.
     *
     * @param priceIndicator  the price indicator to cluster
     * @param weightIndicator the weight indicator supplying cluster weights
     * @param lookbackCount   the number of bars to evaluate (non-positive for the
     *                        full history)
     * @param tolerance       the absolute tolerance for bucket membership
     * @since 0.22.3
     */
    public PriceClusterSupportIndicator(Indicator<Num> priceIndicator, Indicator<Num> weightIndicator,
            int lookbackCount, Num tolerance) {
        super(priceIndicator, weightIndicator, lookbackCount, tolerance);
        this.bandwidth = null;
    }

    /**
     * Constructor using custom price and volume indicators plus independent KDE
     * bandwidth.
     *
     * @param priceIndicator  the price indicator to cluster
     * @param volumeIndicator the volume indicator supplying weights
     * @param lookbackCount   the number of bars to evaluate (non-positive for the
     *                        full history)
     * @param tolerance       the absolute tolerance for bucket membership
     * @param bandwidth       the bandwidth passed to the volume profile KDE
     * @since 0.22.3
     */
    public PriceClusterSupportIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator,
            int lookbackCount, Num tolerance, Num bandwidth) {
        super(priceIndicator, new VolumeProfileKDEIndicator(priceIndicator, volumeIndicator, lookbackCount, bandwidth),
                volumeIndicator, lookbackCount, tolerance);
        this.bandwidth = bandwidth;
    }

    /**
     * Builds the default volume indicator.
     */
    private static VolumeIndicator defaultVolumeIndicator(Indicator<Num> priceIndicator) {
        return new VolumeIndicator(requireSeries(priceIndicator), 1);
    }

    /**
     * Validates and returns series.
     */
    private static BarSeries requireSeries(Indicator<Num> indicator) {
        return Objects.requireNonNull(indicator.getBarSeries(), "indicator must reference a bar series");
    }

    /**
     * Implements zero tolerance.
     */
    private static Num zeroTolerance(Indicator<Num> indicator) {
        return requireSeries(indicator).numFactory().zero();
    }

    /**
     * Implements prefer lower price on tie.
     */
    @Override
    protected boolean preferLowerPriceOnTie() {
        return true;
    }

    /**
     * Returns the number of unstable bars required before values become reliable.
     */
    @Override
    public int getCountOfUnstableBars() {
        return super.getCountOfUnstableBars();
    }
}
