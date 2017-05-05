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
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.helpers.SmoothedAverageGainIndicator;
import eu.verdelhan.ta4j.indicators.helpers.SmoothedAverageLossIndicator;

/**
 * Relative strength index indicator.
 * <p>
 * This calculation of RSI is based on accumulative moving average
 * as described in Wilder's original paper from 1978
 *
 * <p>See reference
 * <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:relative_strength_index_rsi">
 * RSI calculation</a>.
 *
 * @see RSIIndicator
 * @since 0.9
 */
public class SmoothedRSIIndicator extends RSIIndicator {

    /** Minimum number of ticks needed for smoothing */
    private static final Integer SMOOTH_MIN_TICKS = 150;

    public SmoothedRSIIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(new SmoothedAverageGainIndicator(indicator, timeFrame),
                new SmoothedAverageLossIndicator(indicator, timeFrame));
    }

    @Override
    protected Decimal calculate(int index) {
        if (index < SMOOTH_MIN_TICKS) {
            log.warn(
                "Requesting index : {}. Smoothed RSI needs {} ticks before calculated index in data series to get the best results",
                index,
                SMOOTH_MIN_TICKS);
        }
        return super.calculate(index);
    }
}
