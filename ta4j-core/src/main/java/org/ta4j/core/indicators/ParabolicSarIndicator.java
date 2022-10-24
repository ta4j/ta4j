/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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

import java.util.HashMap;
import java.util.Map;

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

    private final Map<Integer, Boolean> is_up_trend_map = new HashMap<>();
    private final Map<Integer, Num> last_extreme = new HashMap<>();
    private final Map<Integer, Num> last_af = new HashMap<>();
    private final Num maxAcceleration;
    private final Num accelerationIncrement;
    private final Num accelerationStart;
    private LowPriceIndicator lowPriceIndicator;
    private HighPriceIndicator highPriceIndicator;

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
        accelerationIncrement = increment;
        accelerationStart = aF;
    }

    @Override
    protected Num calculate(int index) {
        Num sar = NaN;
        boolean is_up_trend = false;

        if (index == getBarSeries().getBeginIndex()) {
            last_extreme.put(0, getBarSeries().getBar(index).getClosePrice());
            last_af.put(0, sar.numOf(0));
            is_up_trend_map.put(0, false);
            return sar; // no trend detection possible for the first value
        } else if (index == getBarSeries().getBeginIndex() + 1) {// start trend detection
            is_up_trend = getBarSeries().getBar(index - 1)
                    .getClosePrice()
                    .isLessThan(getBarSeries().getBar(index).getClosePrice());

            last_af.put(index, accelerationStart);
            is_up_trend_map.put(index, is_up_trend);
            if (is_up_trend) { // up trend
                sar = new LowestValueIndicator(lowPriceIndicator, 2).getValue(index - 1); // put the lowest low value of
                                                                                          // two
                last_extreme.put(index, new HighestValueIndicator(highPriceIndicator, 2).getValue(index - 1));
            } else { // down trend
                sar = new HighestValueIndicator(highPriceIndicator, 2).getValue(index - 1); // put the highest high
                                                                                            // value of
                last_extreme.put(index, new LowestValueIndicator(lowPriceIndicator, 2).getValue(index - 1));
            }
            return sar;
        }

        Num priorSar = getValue(index - 1);

        is_up_trend = is_up_trend_map.get(index - 1);

        Num currentExtremePoint = last_extreme.get(index - 1);
        Num cur_high = highPriceIndicator.getValue(index);
        Num cur_low = lowPriceIndicator.getValue(index);
        Num cur_af = last_af.get(index - 1);
        sar = priorSar.plus(cur_af.multipliedBy((currentExtremePoint.minus(priorSar))));

        if (is_up_trend) { // if up trend
            if (cur_low.isLessThan(sar)) { // check if sar touches the low price
                sar = currentExtremePoint;

                last_af.put(index, accelerationStart);
                last_extreme.put(index, cur_low);
                is_up_trend = false;

            } else { // up trend is going on
                if (cur_high.isGreaterThan(currentExtremePoint)) {
                    currentExtremePoint = cur_high;
                    cur_af = incrementAcceleration(index);
                }
                last_extreme.put(index, currentExtremePoint);
                last_af.put(index, cur_af);
            }
        } else { // downtrend
            if (cur_high.isGreaterThanOrEqual(sar)) { // check if switch to up trend
                sar = currentExtremePoint;

                last_af.put(index, accelerationStart);
                last_extreme.put(index, cur_high);
                is_up_trend = true;

            } else { // down trend io going on
                if (cur_low.isLessThan(currentExtremePoint)) {
                    currentExtremePoint = cur_low;
                    cur_af = incrementAcceleration(index);
                }
                last_extreme.put(index, currentExtremePoint);
                last_af.put(index, cur_af);

            }
        }

        if (is_up_trend) {
            Num lowestPriceOfTwoPreviousBars = new LowestValueIndicator(lowPriceIndicator, 2).getValue(index - 1);
            if (sar.isGreaterThan(lowestPriceOfTwoPreviousBars)) {
                sar = lowestPriceOfTwoPreviousBars;
            }
        } else {
            Num highestPriceOfTwoPreviousBars = new HighestValueIndicator(highPriceIndicator, 2).getValue(index - 1);
            if (sar.isLessThan(highestPriceOfTwoPreviousBars)) {
                sar = highestPriceOfTwoPreviousBars;
            }
        }
        is_up_trend_map.put(index, is_up_trend);
        return sar;
    }

    /**
     * Increments the acceleration factor.
     */
    private Num incrementAcceleration(int index) {
        Num cur_af = last_af.get(index - 1);
        cur_af = cur_af.plus(accelerationIncrement);
        if (cur_af.isGreaterThan(maxAcceleration)) {
            cur_af = maxAcceleration;
        }
        return cur_af;
    }
}
