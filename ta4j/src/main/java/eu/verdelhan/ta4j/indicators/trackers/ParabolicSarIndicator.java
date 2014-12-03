/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.indicators.trackers;


import eu.verdelhan.ta4j.TADecimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.helpers.HighestValueIndicator;
import eu.verdelhan.ta4j.indicators.helpers.LowestValueIndicator;
import eu.verdelhan.ta4j.indicators.simple.MaxPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MinPriceIndicator;

/**
 * Parabolic SAR indicator.
 * <p>
 */
public class ParabolicSarIndicator extends CachedIndicator<TADecimal> {

    private static final TADecimal DEFAULT_ACCELERATION = TADecimal.valueOf("0.02");
    private static final TADecimal ACCELERATION_THRESHOLD = TADecimal.valueOf("0.19");
    private static final TADecimal MAX_ACCELERATION = TADecimal.valueOf("0.2");
    private static final TADecimal ACCELERATION_INCREMENT = TADecimal.valueOf("0.02");

    private TADecimal acceleration = DEFAULT_ACCELERATION;

    private final TimeSeries series;

    private TADecimal extremePoint;

    private final LowestValueIndicator lowestValueIndicator;

    private final HighestValueIndicator highestValueIndicator;
    
    private final int timeFrame;
    
    public ParabolicSarIndicator(TimeSeries series, int timeFrame) {
        super(series);
        this.series = series;
        this.lowestValueIndicator = new LowestValueIndicator(new MinPriceIndicator(series), timeFrame);
        this.highestValueIndicator = new HighestValueIndicator(new MaxPriceIndicator(series), timeFrame);
        this.timeFrame = timeFrame;
    }

    @Override
    protected TADecimal calculate(int index) {

        if (index <= 1) {
            // Warning: should the min or the max price, according to the trend
            // But we don't know the trend yet, so we use the close price.
            extremePoint = series.getTick(index).getClosePrice();
            return extremePoint;
        }

        TADecimal n2ClosePrice = series.getTick(index - 2).getClosePrice();
        TADecimal n1ClosePrice = series.getTick(index - 1).getClosePrice();
        TADecimal nClosePrice = series.getTick(index).getClosePrice();

        TADecimal sar;
        if (n2ClosePrice.isGreaterThan(n1ClosePrice) && n1ClosePrice.isLessThan(nClosePrice)) {
            // Trend switch: \_/
            sar = extremePoint;
            extremePoint = highestValueIndicator.getValue(index);
            acceleration = DEFAULT_ACCELERATION;
        } else if (n2ClosePrice.isLessThan(n1ClosePrice) && n1ClosePrice.isGreaterThan(nClosePrice)) {
            // Trend switch: /¯\
            sar = extremePoint;
            extremePoint = lowestValueIndicator.getValue(index);
            acceleration = DEFAULT_ACCELERATION;

        } else if (nClosePrice.isLessThan(n1ClosePrice)) {
             // Downtrend: falling SAR
            TADecimal lowestValue = lowestValueIndicator.getValue(index);
            if (extremePoint.isGreaterThan(lowestValue)) {
                incrementAcceleration();
                extremePoint = lowestValue;
            }
            sar = calculateSar(index);

            TADecimal n2MaxPrice = series.getTick(index - 2).getMaxPrice();
            TADecimal n1MaxPrice = series.getTick(index - 1).getMaxPrice();
            TADecimal nMaxPrice = series.getTick(index).getMaxPrice();

            if (n1MaxPrice.isGreaterThan(sar)) {
                sar = n1MaxPrice;
            } else if (n2MaxPrice.isGreaterThan(sar)) {
                sar = n2MaxPrice;
            }
            if (nMaxPrice.isGreaterThan(sar)) {
                sar = series.getTick(index).getMinPrice();
            }

        } else {
             // Uptrend: rising SAR
            TADecimal highestValue = highestValueIndicator.getValue(index);
            if (extremePoint.isLessThan(highestValue)) {
                incrementAcceleration();
                extremePoint = highestValue;
            }
            sar = calculateSar(index);

            TADecimal n2MinPrice = series.getTick(index - 2).getMinPrice();
            TADecimal n1MinPrice = series.getTick(index - 1).getMinPrice();
            TADecimal nMinPrice = series.getTick(index).getMinPrice();

            if (n1MinPrice.isLessThan(sar)) {
                sar = n1MinPrice;
            } else if (n2MinPrice.isLessThan(sar)) {
                sar = n2MinPrice;
            }
            if (nMinPrice.isLessThan(sar)) {
                sar = series.getTick(index).getMaxPrice();
            }

        }
        return sar;
    }

    /**
     * Increments the acceleration factor.
     */
    private void incrementAcceleration() {
        if (acceleration.isGreaterThanOrEqual(ACCELERATION_THRESHOLD)) {
            acceleration = MAX_ACCELERATION;
        } else {
            acceleration = acceleration.plus(ACCELERATION_INCREMENT);
        }
    }

    /**
     * Calculates the SAR.
     * @param index the index
     * @return the SAR
     */
    private TADecimal calculateSar(int index) {
        TADecimal previousSar = getValue(index - 1);
        return extremePoint.multipliedBy(acceleration)
                .plus(TADecimal.ONE.minus(acceleration).multipliedBy(previousSar));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}
