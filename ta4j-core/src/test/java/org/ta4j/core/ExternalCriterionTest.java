/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core;

import org.ta4j.core.num.Num;

public interface ExternalCriterionTest {

    /**
     * Gets the BarSeries used by an external criterion calculator.
     *
     * @return BarSeries from the external criterion calculator
     * @throws Exception if the external calculator throws an Exception
     */
    BarSeries getSeries() throws Exception;

    /**
     * Sends criterion parameters to an external criterion calculator and returns
     * the final value of the externally calculated criterion.
     *
     * @param params criterion parameters
     * @return Num final criterion value
     * @throws Exception if the external calculator throws an Exception
     */
    Num getFinalCriterionValue(Object... params) throws Exception;

    /**
     * Gets the trading record used by an external criterion calculator.
     *
     * @return TradingRecord from the external criterion calculator
     * @throws Exception if the external calculator throws an Exception
     */
    TradingRecord getTradingRecord() throws Exception;

}
