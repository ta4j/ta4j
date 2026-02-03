/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.supportresistance;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Estimates resistance levels by clustering bar closes within a configurable
 * tolerance.
 *
 * @since 0.19
 */
public class PriceClusterResistanceIndicator extends AbstractPriceClusterIndicator {

    /**
     * Constructor using {@link ClosePriceIndicator}, unlimited history and zero
     * tolerance.
     *
     * @param series the backing bar series
     * @since 0.19
     */
    public PriceClusterResistanceIndicator(BarSeries series) {
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
     * @since 0.19
     */
    public PriceClusterResistanceIndicator(BarSeries series, int lookbackCount, Num tolerance) {
        this(new ClosePriceIndicator(series), new VolumeIndicator(series, 1), lookbackCount, tolerance, tolerance);
    }

    /**
     * Constructor using a custom price indicator, unlimited history and zero
     * tolerance.
     *
     * @param priceIndicator the price indicator to cluster
     * @since 0.19
     */
    public PriceClusterResistanceIndicator(Indicator<Num> priceIndicator) {
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
     * @since 0.19
     */
    public PriceClusterResistanceIndicator(Indicator<Num> priceIndicator, int lookbackCount, Num tolerance) {
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
     * @since 0.19
     */
    public PriceClusterResistanceIndicator(Indicator<Num> priceIndicator, Indicator<Num> weightIndicator,
            int lookbackCount, Num tolerance) {
        super(priceIndicator, weightIndicator, lookbackCount, tolerance);
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
     * @since 0.19
     */
    public PriceClusterResistanceIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator,
            int lookbackCount, Num tolerance, Num bandwidth) {
        this(priceIndicator, new VolumeProfileKDEIndicator(priceIndicator, volumeIndicator, lookbackCount, bandwidth),
                lookbackCount, tolerance);
    }

    private static VolumeIndicator defaultVolumeIndicator(Indicator<Num> priceIndicator) {
        return new VolumeIndicator(requireSeries(priceIndicator), 1);
    }

    private static BarSeries requireSeries(Indicator<Num> indicator) {
        return Objects.requireNonNull(indicator.getBarSeries(), "indicator must reference a bar series");
    }

    private static Num zeroTolerance(Indicator<Num> indicator) {
        return requireSeries(indicator).numFactory().zero();
    }

    @Override
    protected boolean preferLowerPriceOnTie() {
        return false;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
