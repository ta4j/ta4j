/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core.analysis.criteria;

import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalCriterionTest;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.num.Num;

public class XLSCriterionTest implements ExternalCriterionTest {

    private Class<?> clazz;
    private String fileName;
    private int criterionColumn;
    private int statesColumn;
    private BarSeries cachedSeries = null;
    private final Function<Number, Num> numFunction;

    /**
     * Constructor.
     * 
     * @param clazz           class containing the file resources
     *                        包含文件资源的类
     *
     * @param fileName        file name of the file containing the workbook
     *                        包含工作簿的文件的文件名
     *
     * @param criterionColumn column number containing the calculated criterion    values
     *                        包含计算的标准值的列号
     *
     * @param statesColumn    column number containing the trading record states
     *                        包含交易记录状态的列号
     */
    public XLSCriterionTest(Class<?> clazz, String fileName, int criterionColumn, int statesColumn,
            Function<Number, Num> numFunction) {
        this.clazz = clazz;
        this.fileName = fileName;
        this.criterionColumn = criterionColumn;
        this.statesColumn = statesColumn;
        this.numFunction = numFunction;
    }

    /**
     * Gets the BarSeries from the XLS file. The BarSeries is cached so that subsequent calls do not execute getSeries.
     * * 从 XLS 文件中获取 BarSeries。 BarSeries 被缓存，因此后续调用不会执行 getSeries。
     * 
     * @return BarSeries from the file
     *          文件中的 BarSeries
     *
     * @throws Exception if getSeries throws IOException or DataFormatException
     * * @throws Exception 如果 getSeries 抛出 IOException 或 DataFormatException
     */
    public BarSeries getSeries() throws Exception {
        if (cachedSeries == null) {
            cachedSeries = XlsTestsUtils.getSeries(clazz, fileName, numFunction);
        }
        return cachedSeries;
    }

    /**
     * Gets the final criterion value from the XLS file given the parameters.
     * * 从给定参数的 XLS 文件中获取最终标准值。
     * 
     * @param params criterion parameters
     *               标准参数
     *
     * @return Num final criterion value Num 最终标准值
     * @throws Exception if getFinalCriterionValue throws IOException or  DataFormatException
     *          如果 getFinalCriterionValue 抛出 IOException 或 DataFormatException
     */
    public Num getFinalCriterionValue(Object... params) throws Exception {
        return XlsTestsUtils.getFinalCriterionValue(clazz, fileName, criterionColumn, getSeries().function(), params);
    }

    /**
     * Gets the trading record from the XLS file.
     * * 从 XLS 文件中获取交易记录。
     * 
     * @return TradingRecord from the file
     * * @return 来自文件的交易记录
     */
    public TradingRecord getTradingRecord() throws Exception {
        return XlsTestsUtils.getTradingRecord(clazz, fileName, statesColumn, getSeries().function());
    }

}
