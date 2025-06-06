/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalCriterionTest;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class XLSCriterionTest implements ExternalCriterionTest {

    private final Class<?> clazz;
    private final String fileName;
    private final int criterionColumn;
    private final int statesColumn;
    private BarSeries cachedSeries = null;
    private final NumFactory numFactory;

    /**
     * Constructor.
     *
     * @param clazz           class containing the file resources
     * @param fileName        file name of the file containing the workbook
     * @param criterionColumn column number containing the calculated criterion
     *                        values
     * @param statesColumn    column number containing the trading record states
     */
    public XLSCriterionTest(Class<?> clazz, String fileName, int criterionColumn, int statesColumn,
            NumFactory numFactory) {
        this.clazz = clazz;
        this.fileName = fileName;
        this.criterionColumn = criterionColumn;
        this.statesColumn = statesColumn;
        this.numFactory = numFactory;
    }

    /**
     * Gets the BarSeries from the XLS file. The BarSeries is cached so that
     * subsequent calls do not execute getSeries.
     *
     * @return BarSeries from the file
     * @throws Exception if getSeries throws IOException or DataFormatException
     */
    @Override
    public BarSeries getSeries() throws Exception {
        if (cachedSeries == null) {
            cachedSeries = XlsTestsUtils.getSeries(clazz, fileName, numFactory);
        }
        return cachedSeries;
    }

    /**
     * Gets the final criterion value from the XLS file given the parameters.
     *
     * @param params criterion parameters
     * @return Num final criterion value
     * @throws Exception if getFinalCriterionValue throws IOException or
     *                   DataFormatException
     */
    @Override
    public Num getFinalCriterionValue(Object... params) throws Exception {
        return XlsTestsUtils.getFinalCriterionValue(clazz, fileName, criterionColumn, getSeries().numFactory(), params);
    }

    /**
     * Gets the trading record from the XLS file.
     *
     * @return TradingRecord from the file
     */
    @Override
    public TradingRecord getTradingRecord() throws Exception {
        return XlsTestsUtils.getTradingRecord(clazz, fileName, statesColumn, getSeries().numFactory());
    }

}
