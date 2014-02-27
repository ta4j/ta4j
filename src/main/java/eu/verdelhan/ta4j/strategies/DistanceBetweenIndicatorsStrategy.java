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
package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Indicator;

/**
 * Distance between {@link Indicator indicators} strategy.
 * <p>
 * Enter: when the distance between the two indicators is above the difference<br>
 * Exit: when the distance between the two indicators is below the difference
 */
public class DistanceBetweenIndicatorsStrategy extends AbstractStrategy {

    private Indicator<? extends Number> upper;

    private Indicator<? extends Number> lower;

    private double distance;

    private double difference;

	/**
	 * Constructor.
	 * @param upper the upper indicator
	 * @param lower the lower indicator
	 * @param distance the distance
	 * @param difference the difference
	 */
    public DistanceBetweenIndicatorsStrategy(Indicator<? extends Number> upper, Indicator<? extends Number> lower,
            double distance, double difference) {
        this.upper = upper;
        this.lower = lower;
        this.distance = distance;
        this.difference = difference;
    }

    @Override
    public boolean shouldEnter(int index) {
        if ((upper.getValue(index).doubleValue() - lower.getValue(index).doubleValue()) >= ((difference + 1.0) * distance)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldExit(int index) {
        if ((upper.getValue(index).doubleValue() - lower.getValue(index).doubleValue()) <= ((1.0 - difference) * distance)) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s upper: %s lower: %s", this.getClass().getSimpleName(), upper, lower);
    }
}
