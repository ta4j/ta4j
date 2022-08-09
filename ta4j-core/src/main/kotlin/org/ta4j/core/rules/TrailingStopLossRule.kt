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

import org.ta4j.core.Indicator
import org.ta4j.core.TradingRecord
import org.ta4j.core.indicators.helpers.HighestValueIndicator
import org.ta4j.core.indicators.helpers.LowestValueIndicator
import org.ta4j.core.num.*
import kotlin.math.min

/**
 * A trailing stop-loss rule
 *
 * Satisfied when the price reaches the trailing loss threshold.
 */
class TrailingStopLossRule
/**
 * Constructor.
 *
 * @param priceIndicator      the (close price) indicator
 * @param lossPercentage the loss percentage
 */ @JvmOverloads constructor(
    /**
     * The price indicator
     */
    private val priceIndicator: Indicator<Num>,
    /** the loss-distance as percentage  */
    private val lossPercentage: Num,
    /** The barCount  */
    private val barCount: Int = Int.MAX_VALUE
) : AbstractRule() {
    /**
     * the current stop loss price activation
     */
    var currentStopLossLimitActivation: Num? = null
        private set
    /**
     * Constructor.
     *
     * @param priceIndicator      the (close price) indicator
     * @param lossPercentage the loss percentage
     * @param barCount       number of bars to look back for the calculation
     */
    /** This rule uses the `tradingRecord`.  */
    override fun isSatisfied(index: Int, tradingRecord: TradingRecord?): Boolean {
        var satisfied = false
        // No trading history or no position opened, no loss
        if (tradingRecord != null) {
            val currentPosition = tradingRecord.getCurrentPosition()
            if (currentPosition.isOpened) {
                val currentPrice = priceIndicator[index]
                val positionIndex = currentPosition.entry!!.index
                satisfied = if (currentPosition.entry!!.isBuy) {
                    isBuySatisfied(currentPrice, index, positionIndex)
                } else {
                    isSellSatisfied(currentPrice, index, positionIndex)
                }
            }
        }
        traceIsSatisfied(index, satisfied)
        return satisfied
    }

    private fun isBuySatisfied(currentPrice: Num?, index: Int, positionIndex: Int): Boolean {
        val highest = HighestValueIndicator(
            priceIndicator,
            getValueIndicatorBarCount(index, positionIndex)
        )
        val highestCloseNum = highest[index]
        val lossRatioThreshold =
            highestCloseNum.numOf(100).minus(lossPercentage).div(highestCloseNum.numOf(100))
        currentStopLossLimitActivation = highestCloseNum.times(lossRatioThreshold)
        return currentPrice!!.isLessThanOrEqual(currentStopLossLimitActivation)
    }

    private fun isSellSatisfied(currentPrice: Num?, index: Int, positionIndex: Int): Boolean {
        val lowest = LowestValueIndicator(
            priceIndicator,
            getValueIndicatorBarCount(index, positionIndex)
        )
        val lowestCloseNum = lowest[index]
        val lossRatioThreshold = lowestCloseNum.numOf(100).plus(lossPercentage).div(lowestCloseNum.numOf(100))
        currentStopLossLimitActivation = lowestCloseNum.times(lossRatioThreshold)
        return currentPrice!!.isGreaterThanOrEqual(currentStopLossLimitActivation)
    }

    private fun getValueIndicatorBarCount(index: Int, positionIndex: Int): Int {
        return min(index - positionIndex + 1, barCount)
    }

    override fun traceIsSatisfied(index: Int, isSatisfied: Boolean) {
        if (log.isTraceEnabled) {
            log.trace(
                "{}#isSatisfied({}): {}. Current price: {}, Current stop loss activation: {}",
                javaClass.simpleName, index, isSatisfied, priceIndicator[index],
                currentStopLossLimitActivation
            )
        }
    }
}