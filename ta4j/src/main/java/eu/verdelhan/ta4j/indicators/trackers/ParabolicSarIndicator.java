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
package eu.verdelhan.ta4j.indicators.trackers;


import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.RecursiveCachedIndicator;
import eu.verdelhan.ta4j.indicators.helpers.HighestValueIndicator;
import eu.verdelhan.ta4j.indicators.helpers.LowestValueIndicator;
import eu.verdelhan.ta4j.indicators.simple.MaxPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MinPriceIndicator;

/**
 * Parabolic SAR indicator.
 * <p>
 */
public class ParabolicSarIndicator extends RecursiveCachedIndicator<Decimal> {

    private static final Decimal DEFAULT_ACCELERATION = Decimal.valueOf("0.02");
    private static final Decimal ACCELERATION_THRESHOLD = Decimal.valueOf("0.19");
    private static final Decimal MAX_ACCELERATION = Decimal.valueOf("0.2");
    private static final Decimal ACCELERATION_INCREMENT = Decimal.valueOf("0.02");

    private Decimal acceleration = DEFAULT_ACCELERATION;

    private final TimeSeries series;

    private Decimal extremePoint;

    private final LowestValueIndicator lowestValueIndicator;

    private final HighestValueIndicator highestValueIndicator;
    
    public ParabolicSarIndicator(TimeSeries series, int timeFrame) {
        super(series);
        this.series = series;
        this.lowestValueIndicator = new LowestValueIndicator(new MinPriceIndicator(series), timeFrame);
        this.highestValueIndicator = new HighestValueIndicator(new MaxPriceIndicator(series), timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {

        if (index <= 1) {
            // Warning: should the min or the max price, according to the trend
            // But we don't know the trend yet, so we use the close price.
            extremePoint = series.getTick(index).getClosePrice();
            return extremePoint;
        }

        Decimal n2ClosePrice = series.getTick(index - 2).getClosePrice();
        Decimal n1ClosePrice = series.getTick(index - 1).getClosePrice();
        Decimal nClosePrice = series.getTick(index).getClosePrice();

        Decimal sar;
        if (n2ClosePrice.isGreaterThan(n1ClosePrice) && n1ClosePrice.isLessThan(nClosePrice)) {
            // Trend switch: \_/
            sar = extremePoint == null ? lowestValueIndicator.getValue(index) : extremePoint;
            extremePoint = highestValueIndicator.getValue(index);
            acceleration = DEFAULT_ACCELERATION;
        } else if (n2ClosePrice.isLessThan(n1ClosePrice) && n1ClosePrice.isGreaterThan(nClosePrice)) {
            // Trend switch: /¯\
            sar = extremePoint == null ? highestValueIndicator.getValue(index) : extremePoint;
            extremePoint = lowestValueIndicator.getValue(index);
            acceleration = DEFAULT_ACCELERATION;

        } else if (nClosePrice.isLessThan(n1ClosePrice)) {
             // Downtrend: falling SAR
            Decimal lowestValue = lowestValueIndicator.getValue(index);
            if (extremePoint.isGreaterThan(lowestValue)) {
                incrementAcceleration();
                extremePoint = lowestValue;
            }
            sar = calculateSar(index);

            Decimal n2MaxPrice = series.getTick(index - 2).getMaxPrice();
            Decimal n1MaxPrice = series.getTick(index - 1).getMaxPrice();
            Decimal nMaxPrice = series.getTick(index).getMaxPrice();

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
            Decimal highestValue = highestValueIndicator.getValue(index);
            if (extremePoint.isLessThan(highestValue)) {
                incrementAcceleration();
                extremePoint = highestValue;
            }
            sar = calculateSar(index);

            Decimal n2MinPrice = series.getTick(index - 2).getMinPrice();
            Decimal n1MinPrice = series.getTick(index - 1).getMinPrice();
            Decimal nMinPrice = series.getTick(index).getMinPrice();

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
     * @param index the tick index
     * @return the SAR
     */
    private Decimal calculateSar(int index) {
        Decimal previousSar = getValue(index - 1);
        return extremePoint.multipliedBy(acceleration)
                .plus(Decimal.ONE.minus(acceleration).multipliedBy(previousSar));
    }
}
