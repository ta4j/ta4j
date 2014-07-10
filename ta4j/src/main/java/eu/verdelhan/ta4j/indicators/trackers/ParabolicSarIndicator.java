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
package eu.verdelhan.ta4j.indicators.trackers;


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
public class ParabolicSarIndicator extends CachedIndicator<Double> {

    private double acceleration;

    private final TimeSeries series;

    private double extremePoint;

    private final LowestValueIndicator lowestValueIndicator;

    private final HighestValueIndicator highestValueIndicator;
    
    private final int timeFrame;
    
    public ParabolicSarIndicator(TimeSeries series, int timeFrame) {
        this.acceleration = 0.02d;
        this.series = series;
        this.lowestValueIndicator = new LowestValueIndicator(new MinPriceIndicator(series), timeFrame);
        this.highestValueIndicator = new HighestValueIndicator(new MaxPriceIndicator(series), timeFrame);
        this.timeFrame = timeFrame;
    }

    @Override
    protected Double calculate(int index) {

        if (index <= 1) {
            // Warning: should the min or the max price, according to the trend
            // But we don't know the trend yet, so we use the close price.
            extremePoint = series.getTick(index).getClosePrice();
            return extremePoint;
        }

        double n2ClosePrice = series.getTick(index - 2).getClosePrice();
        double n1ClosePrice = series.getTick(index - 1).getClosePrice();
        double nClosePrice = series.getTick(index).getClosePrice();

        double sar;
        if (n2ClosePrice > n1ClosePrice && n1ClosePrice < nClosePrice) {
            // Trend switch: \_/
            sar = extremePoint;
            extremePoint = highestValueIndicator.getValue(index);
            acceleration = 0.02;
        } else if (n2ClosePrice < n1ClosePrice && n1ClosePrice > nClosePrice) {
            // Trend switch: /¯\
            sar = extremePoint;
            extremePoint = lowestValueIndicator.getValue(index);
            acceleration = 0.02;

        } else if (nClosePrice < n1ClosePrice) {
             // Downtrend: falling SAR
            double lowestValue = lowestValueIndicator.getValue(index);
            if (extremePoint > lowestValue) {
                acceleration = acceleration >= 0.19 ? 0.2 : acceleration + 0.02d;
                extremePoint = lowestValue;
            }
            sar = (extremePoint - getValue(index - 1)) * acceleration + getValue(index - 1);

            double n2MaxPrice = series.getTick(index - 2).getMaxPrice();
            double n1MaxPrice = series.getTick(index - 1).getMaxPrice();
            double nMaxPrice = series.getTick(index).getMaxPrice();

            if (n1MaxPrice > sar) {
                sar = n1MaxPrice;
            } else if (n2MaxPrice > sar) {
                sar = n2MaxPrice;
            }
            if (nMaxPrice > sar) {
                sar = series.getTick(index).getMinPrice();
            }

        } else {
             // Uptrend: rising SAR
            double highestValue = highestValueIndicator.getValue(index);
            if (extremePoint < highestValue) {
                acceleration = acceleration >= 0.19 ? 0.2 : acceleration + 0.02;
                extremePoint = highestValue;
            }
            sar = (extremePoint - getValue(index - 1)) * acceleration + getValue(index - 1);

            double n2MinPrice = series.getTick(index - 2).getMinPrice();
            double n1MinPrice = series.getTick(index - 1).getMinPrice();
            double nMinPrice = series.getTick(index).getMinPrice();

            if (n1MinPrice < sar) {
                sar = n1MinPrice;
            } else if (n2MinPrice < sar) {
                sar = n2MinPrice;
            }
            if (nMinPrice < sar) {
                sar = series.getTick(index).getMaxPrice();
            }

        }
        return sar;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}
