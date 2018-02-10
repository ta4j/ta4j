/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Parabolic SAR indicator.
 * team172011(Simon-Justus Wimmer), 18.09.2017
 */
public class ParabolicSarIndicator extends RecursiveCachedIndicator<Num> {

    private Num accelerationFactor;
    private final Num maxAcceleration;
    private final Num accelerationIncrement;
    private final Num accelarationStart;



    private final TimeSeries series;

    private boolean currentTrend; // true if uptrend, false otherwise
    private int startTrendIndex = 0; // index of start bar of the current trend
    private MinPriceIndicator minPriceIndicator;
    private MaxPriceIndicator maxPriceIndicator;
    private Num currentExtremePoint; // the extreme point of the current calculation
    private Num minMaxExtremePoint; // depending on trend the maximum or minimum extreme point value of trend

    /**
     * Constructor with default parameters
     * @param series the time series for this indicator
     */
    public ParabolicSarIndicator(TimeSeries series){
        this(series,series.numOf(0.02), series.numOf(0.2), series.numOf(0.02));

    }

    /**
     * Constructor with custom parameters and default increment value
     * @param series the time series for this indicator
     * @param aF acceleration factor
     * @param maxA maximum acceleration
     */
    public ParabolicSarIndicator(TimeSeries series, Num aF,Num maxA) {
        this(series, aF, maxA, series.numOf(0.02));
    }

    /**
     * Constructor with custom parameters
     * @param series the time series for this indicator
     * @param aF acceleration factor
     * @param maxA maximum acceleration
     * @param increment the increment step
     */
    public ParabolicSarIndicator(TimeSeries series, Num aF,Num maxA, Num increment) {
        super(series);
        this.series = series;
        maxPriceIndicator = new MaxPriceIndicator(series);
        minPriceIndicator = new MinPriceIndicator(series);
        maxAcceleration = maxA;
        accelerationFactor = aF;
        accelerationIncrement = increment;
        accelarationStart = aF;
    }

    @Override
    protected Num calculate(int index) {
        Num sar = NaN;
        if (index == series.getBeginIndex()) {
            return sar; // no trend detection possible for the first value
        } else if (index == series.getBeginIndex() + 1) {// start trend detection
            currentTrend = series.getBar(series.getBeginIndex()).getClosePrice().isLessThan(series.getBar(index).getClosePrice());
            if (!currentTrend) { // down trend
                sar = maxPriceIndicator.getValue(index); // put sar on max price of candlestick
                currentExtremePoint = sar;
                minMaxExtremePoint = currentExtremePoint;
            } else { // up trend
                sar = minPriceIndicator.getValue(index); // put sar on min price of candlestick
                currentExtremePoint = sar;
                minMaxExtremePoint = currentExtremePoint;

            }
            return sar;
        }

        Num priorSar = getValue(index-1);
        if (currentTrend) { // if up trend
            sar = priorSar.plus(accelerationFactor.multipliedBy((currentExtremePoint.minus(priorSar))));
            currentTrend = minPriceIndicator.getValue(index).isGreaterThan(sar);
            if (!currentTrend) { // check if sar touches the min price
                sar = minMaxExtremePoint; // sar starts at the highest extreme point of previous up trend
                currentTrend = false; // switch to down trend and reset values
                startTrendIndex = index;
                accelerationFactor = accelarationStart;
                currentExtremePoint = series.getBar(index).getMinPrice(); // put point on max
                minMaxExtremePoint = currentExtremePoint;
            } else { // up trend is going on
                currentExtremePoint = new HighestValueIndicator(maxPriceIndicator, index - startTrendIndex).getValue(index);
                if (currentExtremePoint.isGreaterThan(minMaxExtremePoint)) {
                    incrementAcceleration();
                    minMaxExtremePoint = currentExtremePoint;
                }

            }
        } else { // downtrend
            sar = priorSar.minus(accelerationFactor.multipliedBy(((priorSar.minus(currentExtremePoint)))));
            currentTrend = maxPriceIndicator.getValue(index).isGreaterThanOrEqual(sar);
            if (currentTrend) { // check if switch to up trend
                sar = minMaxExtremePoint; // sar starts at the lowest extreme point of previous down trend
                accelerationFactor = accelarationStart;
                startTrendIndex = index;
                currentExtremePoint = series.getBar(index).getMaxPrice();
                minMaxExtremePoint = currentExtremePoint;
            } else { // down trend io going on
                currentExtremePoint = new LowestValueIndicator(minPriceIndicator, index - startTrendIndex).getValue(index);
                if (currentExtremePoint.isLessThan(minMaxExtremePoint)) {
                    incrementAcceleration();
                    minMaxExtremePoint = currentExtremePoint;
                }
            }
        }
        return sar;

    }

    /**
     * Increments the acceleration factor.
     */
    private void incrementAcceleration() {
        if (accelerationFactor.isGreaterThanOrEqual(maxAcceleration)) {
            accelerationFactor = maxAcceleration;
        } else {
            accelerationFactor = accelerationFactor.plus(accelerationIncrement);
        }
    }
}