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
package org.ta4j.core.indicators.volume;

import org.ta4j.core.num.Num;

/**
 * Volume-weighted standard deviation of price within a VWAP window.
 * <p>
 * This indicator is useful for building VWAP bands that highlight value areas
 * and extensions away from the anchored or rolling VWAP.
 *
 * @since 0.19
 */
public class VWAPStandardDeviationIndicator extends AbstractVWAPIndicator {

    private final AbstractVWAPIndicator reference;

    /**
     * Constructor.
     *
     * @param reference the VWAP indicator sharing the same price, volume and window
     *                  definition
     *
     * @since 0.19
     */
    public VWAPStandardDeviationIndicator(AbstractVWAPIndicator reference) {
        super(reference.priceIndicator, reference.volumeIndicator);
        this.reference = reference;
    }

    @Override
    protected int resolveWindowStartIndex(int index) {
        return reference.getWindowStartIndex(index);
    }

    @Override
    protected Num map(VWAPValues values) {
        Num mean = values.mean();
        Num expectedSquare = values.weightedSquareMean();
        Num variance = expectedSquare.minus(mean.multipliedBy(mean));
        if (variance.isNegative()) {
            variance = values.getFactory().zero();
        }
        return variance.sqrt();
    }

    @Override
    public int getCountOfUnstableBars() {
        return reference.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " reference: " + reference;
    }
}
