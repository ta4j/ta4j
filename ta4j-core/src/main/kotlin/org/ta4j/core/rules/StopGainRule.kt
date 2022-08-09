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
package org.ta4j.core.rules

import org.ta4j.core.TradingRecord
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.num.*

/**
 * A stop-gain rule.
 *
 * Satisfied when the close price reaches the gain threshold.
 */
class StopGainRule(
    /**
     * The close price indicator
     */
    private val closePrice: ClosePriceIndicator,
    /**
     * The gain percentage
     */
    private val gainPercentage: Num
) : AbstractRule() {
    /**
     * Constant value for 100
     */
    private val HUNDRED: Num = closePrice.numOf(100)

    /**
     * Constructor.
     *
     * @param closePrice     the close price indicator
     * @param gainPercentage the gain percentage
     */
    constructor(closePrice: ClosePriceIndicator, gainPercentage: Number) : this(
        closePrice,
        closePrice.numOf(gainPercentage)
    )

    /** This rule uses the `tradingRecord`.  */
    override fun isSatisfied(index: Int, tradingRecord: TradingRecord?): Boolean {
        var satisfied = false
        // No trading history or no position opened, no loss
        if (tradingRecord != null) {
            val currentPosition = tradingRecord.getCurrentPosition()
            if (currentPosition.isOpened) {
                val entryPrice = currentPosition.entry!!.netPrice
                val currentPrice = closePrice[index]
                satisfied = if (currentPosition.entry!!.isBuy) {
                    isBuyGainSatisfied(entryPrice, currentPrice)
                } else {
                    isSellGainSatisfied(entryPrice, currentPrice)
                }
            }
        }
        traceIsSatisfied(index, satisfied)
        return satisfied
    }

    private fun isBuyGainSatisfied(entryPrice: Num?, currentPrice: Num?): Boolean {
        val lossRatioThreshold = (HUNDRED + gainPercentage) / HUNDRED
        val threshold = entryPrice!! * lossRatioThreshold
        return currentPrice!!.isGreaterThanOrEqual(threshold)
    }

    private fun isSellGainSatisfied(entryPrice: Num?, currentPrice: Num?): Boolean {
        val lossRatioThreshold = (HUNDRED - gainPercentage) / HUNDRED
        val threshold = entryPrice!! * lossRatioThreshold
        return currentPrice!!.isLessThanOrEqual(threshold)
    }
}