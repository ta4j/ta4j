/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * Cross indicator.
 * <p>
 * Boolean indicator which monitors two-indicators crossings.
 */
public class CrossIndicator extends CachedIndicator<Boolean> {

    /** Upper indicator */
    private final Indicator<Decimal> up;
    /** Lower indicator */
    private final Indicator<Decimal> low;

    /**
     * Constructor.
     * @param up the upper indicator
     * @param low the lower indicator
     */
    public CrossIndicator(Indicator<Decimal> up, Indicator<Decimal> low) {
        // TODO: check if up series is equal to low series
        super(up);
        this.up = up;
        this.low = low;
    }

    @Override
    protected Boolean calculate(int index) {

        int i = index;
        if (i == 0 || up.getValue(i).isGreaterThanOrEqual(low.getValue(i))) {
            return false;
        }

        i--;
        if (up.getValue(i).isGreaterThan(low.getValue(i))) {
            return true;
        } else {

            while (i > 0 && up.getValue(i).isEqual(low.getValue(i))) {
                i--;
            }
            return (i != 0) && (up.getValue(i).isGreaterThan(low.getValue(i)));
        }
    }

    /**
     * @return the initial lower indicator
     */
    public Indicator<Decimal> getLow() {
        return low;
    }

    /**
     * @return the initial upper indicator
     */
    public Indicator<Decimal> getUp() {
        return up;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + low + " " + up;
    }
}
