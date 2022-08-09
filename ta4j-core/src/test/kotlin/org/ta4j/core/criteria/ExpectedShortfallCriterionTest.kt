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
package org.ta4j.core.criteria

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.*
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.Num
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.math.ln

class ExpectedShortfallCriterionTest(numFunction: Function<Number?, Num>?) : AbstractCriterionTest(
    CriterionFactory { ExpectedShortfallCriterion(0.95) },
    Function { obj: Number? -> DoubleNum.Companion.valueOf(obj) }) {
    private lateinit var series: MockBarSeries
    @Test
    fun calculateOnlyWithGainPositions() {
        series = MockBarSeries(numFunction, 100.0, 105.0, 106.0, 107.0, 108.0, 115.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(
            buyAt(0, series), sellAt(2, series),
            buyAt(3, series), sellAt(5, series)
        )
        val varCriterion = getCriterion()
        TestUtils.assertNumEquals(numOf(0.0), varCriterion!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateWithASimplePosition() {
        // if only one position in tail, VaR = ES
        series = MockBarSeries(numFunction, 100.0, 104.0, 90.0, 100.0, 95.0, 105.0)
        val tradingRecord: TradingRecord = BaseTradingRecord(buyAt(0, series), sellAt(2, series))
        val esCriterion = getCriterion()
        TestUtils.assertNumEquals(numOf(ln(90.0 / 104)), esCriterion!!.calculate(series, tradingRecord))
    }

    @Test
    fun calculateOnlyWithLossPosition() {
        // regularly decreasing prices
        val prices = IntStream.rangeClosed(1, 100)
            .asDoubleStream()
            .boxed()
            .sorted(Collections.reverseOrder())
            .collect(Collectors.toList())
        series = MockBarSeries(numFunction, prices)
        val position = Position(
            buyAt(series.beginIndex, series),
            sellAt(series.endIndex, series)
        )
        val esCriterion = getCriterion()
        TestUtils.assertNumEquals(numOf(-0.35835189384561106), esCriterion!!.calculate(series, position))
    }

    @Test
    fun calculateWithNoBarsShouldReturn0() {
        series = MockBarSeries(numFunction, 100.0, 95.0, 100.0, 80.0, 85.0, 70.0)
        val varCriterion = getCriterion()
        TestUtils.assertNumEquals(numOf(0), varCriterion!!.calculate(series, BaseTradingRecord()))
    }

    @Test
    fun calculateWithBuyAndHold() {
        series = MockBarSeries(numFunction, 100.0, 99.0)
        val position = Position(buyAt(0, series), sellAt(1, series))
        val varCriterion = getCriterion()
        TestUtils.assertNumEquals(numOf(Math.log(99.0 / 100)), varCriterion!!.calculate(series, position))
    }

    @Test
    fun betterThan() {
        val criterion = getCriterion()
        Assert.assertTrue(criterion!!.betterThan(numOf(-0.1)!!, numOf(-0.2)!!))
        Assert.assertFalse(criterion.betterThan(numOf(-0.1)!!, numOf(0.0)!!))
    }
}