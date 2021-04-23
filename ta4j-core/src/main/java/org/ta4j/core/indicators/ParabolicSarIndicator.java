/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Parabolic SAR indicator.
 *
 * @see <a href=
 *      "https://www.investopedia.com/trading/introduction-to-parabolic-sar/">
 *      https://www.investopedia.com/trading/introduction-to-parabolic-sar/</a>
 * @see <a href="https://www.investopedia.com/terms/p/parabolicindicator.asp">
 *      https://www.investopedia.com/terms/p/parabolicindicator.asp</a>
 */
public class ParabolicSarIndicator extends RecursiveCachedIndicator<Num> {

    private final Num maxAcceleration;
    private final Num accelerationIncrement;
    private final Num accelerationStart;
    private Num accelerationFactor;
    private boolean currentTrend; // true if uptrend, false otherwise
    private int startTrendIndex = 0; // index of start bar of the current trend
    private LowPriceIndicator lowPriceIndicator;
    private HighPriceIndicator highPriceIndicator;
    private Num currentExtremePoint; // the extreme point of the current calculation
    private Num minMaxExtremePoint; // depending on trend the maximum or minimum extreme point value of trend

    /**
     * Constructor with default parameters
     *
     * @param series the bar series for this indicator
     */
    public ParabolicSarIndicator(BarSeries series) {
        this(series, series.numOf(0.02), series.numOf(0.2), series.numOf(0.02));

    }

    /**
     * Constructor with custom parameters and default increment value
     *
     * @param series the bar series for this indicator
     * @param aF     acceleration factor
     * @param maxA   maximum acceleration
     */
    public ParabolicSarIndicator(BarSeries series, Num aF, Num maxA) {
        this(series, aF, maxA, series.numOf(0.02));
    }

    /**
     * Constructor with custom parameters
     *
     * @param series    the bar series for this indicator
     * @param aF        acceleration factor
     * @param maxA      maximum acceleration
     * @param increment the increment step
     */
    public ParabolicSarIndicator(BarSeries series, Num aF, Num maxA, Num increment) {
        super(series);
        highPriceIndicator = new HighPriceIndicator(series);
        lowPriceIndicator = new LowPriceIndicator(series);
        maxAcceleration = maxA;
        accelerationFactor = aF;
        accelerationIncrement = increment;
        accelerationStart = aF;
    }

    @Override
    protected Num calculate(int index) {
        Num sar = NaN;
        if (index == getBarSeries().getBeginIndex()) {
            return sar; // no trend detection possible for the first value
        } else if (index == getBarSeries().getBeginIndex() + 1) {// start trend detection
            currentTrend = getBarSeries().getBar(getBarSeries().getBeginIndex())
                    .getClosePrice()
                    .isLessThan(getBarSeries().getBar(index).getClosePrice());
            if (!currentTrend) { // down trend
                sar = new HighestValueIndicator(highPriceIndicator, 2).getValue(index); // put the highest high value of
                                                                                        // two first bars
                currentExtremePoint = sar;
                minMaxExtremePoint = currentExtremePoint;
            } else { // up trend
                sar = new LowestValueIndicator(lowPriceIndicator, 2).getValue(index); // put the lowest low value of two
                                                                                      // first bars
                currentExtremePoint = sar;
                minMaxExtremePoint = currentExtremePoint;

            }
            return sar;
        }

        Num priorSar = getValue(index - 1);
        if (currentTrend) { // if up trend
            sar = priorSar.plus(accelerationFactor.multipliedBy((currentExtremePoint.minus(priorSar))));
            currentTrend = lowPriceIndicator.getValue(index).isGreaterThan(sar);
            if (!currentTrend) { // check if sar touches the low price
                if (minMaxExtremePoint.isGreaterThan(highPriceIndicator.getValue(index)))
                    sar = minMaxExtremePoint; // sar starts at the highest extreme point of previous up trend
                else
                    sar = highPriceIndicator.getValue(index);
                currentTrend = false; // switch to down trend and reset values
                startTrendIndex = index;
                accelerationFactor = accelerationStart;
                currentExtremePoint = getBarSeries().getBar(index).getLowPrice(); // put point on max
                minMaxExtremePoint = currentExtremePoint;
            } else { // up trend is going on
                Num lowestPriceOfTwoPreviousBars = new LowestValueIndicator(lowPriceIndicator,
                        Math.min(2, index - startTrendIndex)).getValue(index - 1);
                if (sar.isGreaterThan(lowestPriceOfTwoPreviousBars))
                    sar = lowestPriceOfTwoPreviousBars;
                currentExtremePoint = new HighestValueIndicator(highPriceIndicator, index - startTrendIndex + 1)
                        .getValue(index);
                if (currentExtremePoint.isGreaterThan(minMaxExtremePoint)) {
                    incrementAcceleration();
                    minMaxExtremePoint = currentExtremePoint;
                }

            }
        } else { // downtrend
            sar = priorSar.minus(accelerationFactor.multipliedBy(((priorSar.minus(currentExtremePoint)))));
            currentTrend = highPriceIndicator.getValue(index).isGreaterThanOrEqual(sar);
            if (currentTrend) { // check if switch to up trend
                if (minMaxExtremePoint.isLessThan(lowPriceIndicator.getValue(index)))
                    sar = minMaxExtremePoint; // sar starts at the lowest extreme point of previous down trend
                else
                    sar = lowPriceIndicator.getValue(index);
                accelerationFactor = accelerationStart;
                startTrendIndex = index;
                currentExtremePoint = getBarSeries().getBar(index).getHighPrice();
                minMaxExtremePoint = currentExtremePoint;
            } else { // down trend io going on
                Num highestPriceOfTwoPreviousBars = new HighestValueIndicator(highPriceIndicator,
                        Math.min(2, index - startTrendIndex)).getValue(index - 1);
                if (sar.isLessThan(highestPriceOfTwoPreviousBars))
                    sar = highestPriceOfTwoPreviousBars;
                currentExtremePoint = new LowestValueIndicator(lowPriceIndicator, index - startTrendIndex + 1)
                        .getValue(index);
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