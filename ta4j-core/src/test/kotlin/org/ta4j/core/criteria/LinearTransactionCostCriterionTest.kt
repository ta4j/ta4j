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
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import java.util.function.Function

class LinearTransactionCostCriterionTest(numFunction: Function<Number?, Num>) : AbstractCriterionTest(
    CriterionFactory { params: Array<out Any?> ->
        LinearTransactionCostCriterion(
            params[0] as Double, params[1] as Double,
            params[2] as Double
        )
    }, numFunction
) {
    private val xls: ExternalCriterionTest

    init {
        xls = XLSCriterionTest(this.javaClass, "LTC.xls", 16, 6, numFunction)
    }

    @Test
    @Throws(Exception::class)
    fun externalData() {
        val xlsSeries = xls.getSeries()!!
        val xlsTradingRecord = xls.getTradingRecord()
        var value: Num
        value = getCriterion(1000.0, 0.005, 0.2)!!.calculate(xlsSeries, xlsTradingRecord!!)
        TestUtils.assertNumEquals(xls.getFinalCriterionValue(1000.0, 0.005, 0.2).doubleValue(), value)
        TestUtils.assertNumEquals(843.5492, value)
        value = getCriterion(1000.0, 0.1, 1.0)!!.calculate(xlsSeries, xlsTradingRecord)
        TestUtils.assertNumEquals(xls.getFinalCriterionValue(1000.0, 0.1, 1.0).doubleValue(), value)
        TestUtils.assertNumEquals(1122.4410, value)
    }

    @Test
    fun dummyData() {
        val series = MockBarSeries(numFunction, 100.0, 150.0, 200.0, 100.0, 50.0, 100.0)
        val tradingRecord: TradingRecord = BaseTradingRecord()
        tradingRecord.operate(0)
        tradingRecord.operate(1)
        var criterion = getCriterion(1000.0, 0.005, 0.2)!!.calculate(series, tradingRecord)
        TestUtils.assertNumEquals(12.861, criterion)
        tradingRecord.operate(2)
        tradingRecord.operate(3)
        criterion = getCriterion(1000.0, 0.005, 0.2)!!.calculate(series, tradingRecord)
        TestUtils.assertNumEquals(24.3759, criterion)
        tradingRecord.operate(5)
        criterion = getCriterion(1000.0, 0.005, 0.2)!!.calculate(series, tradingRecord)
        TestUtils.assertNumEquals(28.2488, criterion)
    }

    @Test
    fun fixedCost() {
        val series = MockBarSeries(numFunction, 100.0, 105.0, 110.0, 100.0, 95.0, 105.0)
        val tradingRecord: TradingRecord = BaseTradingRecord()
        var criterion: Num
        tradingRecord.operate(0)
        tradingRecord.operate(1)
        criterion = getCriterion(1000.0, 0.0, 1.3)!!.calculate(series, tradingRecord)
        TestUtils.assertNumEquals(2.6, criterion)
        tradingRecord.operate(2)
        tradingRecord.operate(3)
        criterion = getCriterion(1000.0, 0.0, 1.3)!!.calculate(series, tradingRecord)
        TestUtils.assertNumEquals(5.2, criterion)
        tradingRecord.operate(0)
        criterion = getCriterion(1000.0, 0.0, 1.3)!!.calculate(series, tradingRecord)
        TestUtils.assertNumEquals(6.5, criterion)
    }

    @Test
    fun fixedCostWithOnePosition() {
        val series = MockBarSeries(numFunction, 100.0, 95.0, 100.0, 80.0, 85.0, 70.0)
        val position = Position()
        var criterion: Num
        criterion = getCriterion(1000.0, 0.0, 0.75)!!.calculate(series, position)
        TestUtils.assertNumEquals(0.0, criterion)
        position.operate(1)
        criterion = getCriterion(1000.0, 0.0, 0.75)!!.calculate(series, position)
        TestUtils.assertNumEquals(0.75, criterion)
        position.operate(3)
        criterion = getCriterion(1000.0, 0.0, 0.75)!!.calculate(series, position)
        TestUtils.assertNumEquals(1.5, criterion)
        position.operate(4)
        criterion = getCriterion(1000.0, 0.0, 0.75)!!.calculate(series, position)
        TestUtils.assertNumEquals(1.5, criterion)
    }

    @Test
    fun betterThan() {
        val criterion: AnalysisCriterion = LinearTransactionCostCriterion(1000.0, 0.5)
        Assert.assertTrue(criterion.betterThan(numOf(3.1)!!, numOf(4.2)!!))
        Assert.assertFalse(criterion.betterThan(numOf(2.1)!!, numOf(1.9)!!))
    }
}