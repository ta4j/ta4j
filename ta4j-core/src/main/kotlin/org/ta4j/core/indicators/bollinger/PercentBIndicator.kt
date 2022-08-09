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
package org.ta4j.core.indicators.bollinger

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator
import org.ta4j.core.num.*

/**
 * %B indicator.
 *
 * @see [](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_perce>
http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_perce</a>
) */
/**
 * Constructor.
 *
 * @param indicator an indicator (usually close price)
 * @param barCount  the time frame
 * @param k         the K multiplier (usually 2.0)
 */

class PercentBIndicator(private val indicator: Indicator<Num>, barCount: Int, k: Double) : CachedIndicator<Num>(
    indicator
) {
    private val bbu: BollingerBandsUpperIndicator
    private val bbl: BollingerBandsLowerIndicator

    init {
        val bbm = BollingerBandsMiddleIndicator(SMAIndicator(indicator, barCount))
        val sd = StandardDeviationIndicator(indicator, barCount)
        bbu = BollingerBandsUpperIndicator(bbm, sd, numOf(k))
        bbl = BollingerBandsLowerIndicator(bbm, sd, numOf(k))
    }

    override fun calculate(index: Int): Num {
        val value = indicator[index]
        val upValue = bbu[index]
        val lowValue = bbl[index]
        return (value - lowValue) / (upValue - lowValue)
    }
}