/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supportresistance;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Identifies resistance levels by counting how often prices bounce lower from
 * the same zone.
 *
 * <p>
 * A bounce is recorded each time the price direction flips from up-to-down;
 * bounces are grouped into price buckets to tolerate small price differences.
 *
 * @since 0.22.2
 */
public class BounceCountResistanceIndicator extends AbstractBounceCountIndicator {

    /**
     * Constructor using {@link ClosePriceIndicator} and unlimited history.
     *
     * @param series     the backing bar series
     * @param bucketSize the absolute bucket size for grouping bounce prices
     * @since 0.22.2
     */
    public BounceCountResistanceIndicator(BarSeries series, Num bucketSize) {
        super(series, bucketSize);
    }

    /**
     * Constructor using a custom price indicator and unlimited history.
     *
     * @param priceIndicator the price indicator to analyse
     * @param bucketSize     the absolute bucket size for grouping bounce prices
     * @since 0.22.2
     */
    public BounceCountResistanceIndicator(Indicator<Num> priceIndicator, Num bucketSize) {
        super(priceIndicator, bucketSize);
    }

    /**
     * Constructor with full configuration.
     *
     * @param priceIndicator the price indicator to analyse
     * @param lookbackCount  the number of bars to evaluate (non-positive for the
     *                       full history)
     * @param bucketSize     the absolute bucket size for grouping bounce prices
     * @since 0.22.2
     */
    public BounceCountResistanceIndicator(Indicator<Num> priceIndicator, int lookbackCount, Num bucketSize) {
        super(priceIndicator, lookbackCount, bucketSize);
    }

    /**
     * Implements prefer lower price on tie.
     */
    @Override
    protected boolean preferLowerPriceOnTie() {
        return false;
    }

    /**
     * Verifies that record bounce.
     */
    @Override
    protected boolean shouldRecordBounce(int previousDirection, int newDirection) {
        return previousDirection > 0 && newDirection < 0;
    }

    /**
     * Returns the number of unstable bars required before values become reliable.
     */
    @Override
    public int getCountOfUnstableBars() {
        return super.getCountOfUnstableBars();
    }
}
