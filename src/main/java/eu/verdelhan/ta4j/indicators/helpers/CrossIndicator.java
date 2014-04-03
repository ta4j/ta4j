/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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

/**
 * Cross indicator.
 * <p>
 * Boolean indicator which monitors two-indicators crossings.
 */
public class CrossIndicator implements Indicator<Boolean> {

    /** Upper indicator */
    private final Indicator<? extends Number> up;
    /** Lower indicator */
    private final Indicator<? extends Number> low;

    /**
     * Constructor.
     * @param up the upper indicator
     * @param low the lower indicator
     */
    public CrossIndicator(Indicator<? extends Number> up, Indicator<? extends Number> low) {
        this.up = up;
        this.low = low;
    }

    @Override
    public Boolean getValue(int index) {

        int i = index;
        if (i == 0 || up.getValue(i).doubleValue() >= (low.getValue(i).doubleValue())) {
            return false;
        }

        i = i - 1;
        if (up.getValue(i).doubleValue() > low.getValue(i).doubleValue()) {
            return true;
        } else {

            while (i > 0 && up.getValue(i).doubleValue() == low.getValue(i).doubleValue()) {
                i = i - 1;
            }
            return (i != 0) && (up.getValue(i).doubleValue() > low.getValue(i).doubleValue());
        }
    }

    /**
     * @return the initial lower indicator
     */
    public Indicator<? extends Number> getLow() {
        return low;
    }

    /**
     * @return the initial upper indicator
     */
    public Indicator<? extends Number> getUp() {
        return up;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + low + " " + up;
    }
}
