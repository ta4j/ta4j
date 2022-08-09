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
package org.ta4j.core.indicators

import org.ta4j.core.Indicator
import org.ta4j.core.num.*

/**
 * Know Sure Thing (KST) RCMA1 = X1-Period SMA of Y1-Period Rate-of-Change RCMA2
 * = X2-Period SMA of Y2-Period Rate-of-Change RCMA3 = X3-Period SMA of
 * Y3-Period Rate-of-Change RCMA4 = X4-Period SMA of Y4-Period Rate-of-Change
 * KST = (RCMA1 x 1) + (RCMA2 x 2) + (RCMA3 x 3) + (RCMA4 x 4)
 *
 * @see [
 * https://school.stockcharts.com/doku.php?id=technical_indicators:know_sure_thing_kst
](https://school.stockcharts.com/doku.php?id=technical_indicators:know_sure_thing_kst) *
 */
class KSTIndicator : CachedIndicator<Num> {
    private var RCMA1: SMAIndicator
    private var RCMA2: SMAIndicator
    private var RCMA3: SMAIndicator
    private var RCMA4: SMAIndicator

    /**
     *
     * @param indicator the indicator. Default parameters: RCMA1 = 10-Period SMA of
     * 10-Period Rate-of-Change RCMA2 = 10-Period SMA of 15-Period
     * Rate-of-Change RCMA3 = 10-Period SMA of 20-Period
     * Rate-of-Change RCMA4 = 15-Period SMA of 30-Period
     * Rate-of-Change
     */
    constructor(indicator: Indicator<Num>) : super(indicator) {
        RCMA1 = SMAIndicator(ROCIndicator(indicator, 10), 10)
        RCMA2 = SMAIndicator(ROCIndicator(indicator, 15), 10)
        RCMA3 = SMAIndicator(ROCIndicator(indicator, 20), 10)
        RCMA4 = SMAIndicator(ROCIndicator(indicator, 30), 15)
    }

    /**
     *
     * @param indicator        the indicator.
     * @param rcma1SMABarCount RCMA1 SMA period.
     * @param rcma1ROCBarCount RCMA1 ROC period.
     * @param rcma2SMABarCount RCMA2 SMA period.
     * @param rcma2ROCBarCount RCMA2 ROC period.
     * @param rcma3SMABarCount RCMA3 SMA period.
     * @param rcma3ROCBarCount RCMA3 ROC period.
     * @param rcma4SMABarCount RCMA4 SMA period.
     * @param rcma4ROCBarCount RCMA4 ROC period.
     */
    constructor(
        indicator: Indicator<Num>, rcma1SMABarCount: Int, rcma1ROCBarCount: Int, rcma2SMABarCount: Int,
        rcma2ROCBarCount: Int, rcma3SMABarCount: Int, rcma3ROCBarCount: Int, rcma4SMABarCount: Int,
        rcma4ROCBarCount: Int
    ) : super(indicator) {
        RCMA1 = SMAIndicator(ROCIndicator(indicator, rcma1ROCBarCount), rcma1SMABarCount)
        RCMA2 = SMAIndicator(ROCIndicator(indicator, rcma2ROCBarCount), rcma2SMABarCount)
        RCMA3 = SMAIndicator(ROCIndicator(indicator, rcma3ROCBarCount), rcma3SMABarCount)
        RCMA4 = SMAIndicator(ROCIndicator(indicator, rcma4ROCBarCount), rcma4SMABarCount)
    }

    override fun calculate(index: Int): Num {
        val RCMA1Multiplier = numOf(1)
        val RCMA2Multiplier = numOf(2)
        val RCMA3Multiplier = numOf(3)
        val RCMA4Multiplier = numOf(4)

        return  RCMA1[index] * RCMA1Multiplier+
                RCMA2[index] * RCMA2Multiplier+
                RCMA3[index] * RCMA3Multiplier+
                RCMA4[index] * RCMA4Multiplier
    }
}