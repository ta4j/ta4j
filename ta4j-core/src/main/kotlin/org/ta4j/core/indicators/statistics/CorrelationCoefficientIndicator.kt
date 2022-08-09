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
package org.ta4j.core.indicators.statistics

import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.num.*

/**
 * Correlation coefficient indicator.
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:correlation_coeffici](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:correlation_coeffici)
 */
/**
 * Constructor.
 *
 * @param indicator1 the first indicator
 * @param indicator2 the second indicator
 * @param barCount   the time frame
 */
class CorrelationCoefficientIndicator(indicator1: Indicator<Num>, indicator2: Indicator<Num>, barCount: Int) :
    CachedIndicator<Num>(indicator1) {
    private val   variance1 = VarianceIndicator(indicator1, barCount)
    private val   variance2 = VarianceIndicator(indicator2, barCount)
    private val   covariance = CovarianceIndicator(indicator1, indicator2, barCount)

    override fun calculate(index: Int): Num {
        val cov = covariance[index]
        val var1 = variance1[index]
        val var2 = variance2[index]
        val multipliedSqrt = (var1 * var2).sqrt()
        return cov / multipliedSqrt
    }
}
