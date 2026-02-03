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
 * @since 0.19
 */
public class BounceCountResistanceIndicator extends AbstractBounceCountIndicator {

    /**
     * Constructor using {@link ClosePriceIndicator}
     * and unlimited history.
     *
     * @param series     the backing bar series
     * @param bucketSize the absolute bucket size for grouping bounce prices
     * @since 0.19
     */
    public BounceCountResistanceIndicator(BarSeries series, Num bucketSize) {
        super(series, bucketSize);
    }

    /**
     * Constructor using a custom price indicator and unlimited history.
     *
     * @param priceIndicator the price indicator to analyse
     * @param bucketSize     the absolute bucket size for grouping bounce prices
     * @since 0.19
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
     * @since 0.19
     */
    public BounceCountResistanceIndicator(Indicator<Num> priceIndicator, int lookbackCount, Num bucketSize) {
        super(priceIndicator, lookbackCount, bucketSize);
    }

    @Override
    protected boolean preferLowerPriceOnTie() {
        return false;
    }

    @Override
    protected boolean shouldRecordBounce(int previousDirection, int newDirection) {
        return previousDirection > 0 && newDirection < 0;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
