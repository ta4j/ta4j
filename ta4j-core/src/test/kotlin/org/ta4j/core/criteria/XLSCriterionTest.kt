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

import org.ta4j.core.BarSeries
import org.ta4j.core.ExternalCriterionTest
import org.ta4j.core.TradingRecord
import org.ta4j.core.XlsTestsUtils
import org.ta4j.core.num.Num
import java.util.function.Function


class XLSCriterionTest
/**
 * Constructor.
 *
 * @param clazz           class containing the file resources
 * @param fileName        file name of the file containing the workbook
 * @param criterionColumn column number containing the calculated criterion
 * values
 * @param statesColumn    column number containing the trading record states
 */(
    private val clazz: Class<*>,
    private val fileName: String,
    private val criterionColumn: Int,
    private val statesColumn: Int,
    private val numFunction: Function<Number?, Num>
) : ExternalCriterionTest {
    private var cachedSeries: BarSeries? = null

    /**
     * Gets the BarSeries from the XLS file. The BarSeries is cached so that
     * subsequent calls do not execute getSeries.
     *
     * @return BarSeries from the file
     * @throws Exception if getSeries throws IOException or DataFormatException
     */
    @Throws(Exception::class)
    override fun getSeries(): BarSeries? {
        if (cachedSeries == null) {
            cachedSeries = XlsTestsUtils.getSeries(clazz, fileName, numFunction)
        }
        return cachedSeries
    }

    /**
     * Gets the final criterion value from the XLS file given the parameters.
     *
     * @param params criterion parameters
     * @return Num final criterion value
     * @throws Exception if getFinalCriterionValue throws IOException or
     * DataFormatException
     */
    @Throws(Exception::class)
    override fun getFinalCriterionValue(vararg params: Any): Num {
        return XlsTestsUtils.getFinalCriterionValue(clazz, fileName, criterionColumn, getSeries()!!.function(), *params)
    }

    /**
     * Gets the trading record from the XLS file.
     *
     * @return TradingRecord from the file
     */
    @Throws(Exception::class)
    override fun getTradingRecord(): TradingRecord? {
        return XlsTestsUtils.getTradingRecord(clazz, fileName, statesColumn, getSeries()!!.function())
    }
}