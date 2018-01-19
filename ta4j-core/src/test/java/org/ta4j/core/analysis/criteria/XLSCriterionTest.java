/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.analysis.criteria;

import org.ta4j.core.Decimal;
import org.ta4j.core.ExternalCriterionTest;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.XlsTestsUtils;

public class XLSCriterionTest implements ExternalCriterionTest {

    private Class<?> clazz;
    private String fileName;
    private int criterionColumn;
    private int statesColumn;
    private TimeSeries cachedSeries = null;

    public XLSCriterionTest(Class<?> clazz, String fileName, int criterionColumn, int statesColumn) throws Exception {
        this.clazz = clazz;
        this.fileName = fileName;
        this.criterionColumn = criterionColumn;
        this.statesColumn = statesColumn;
    }

    @Override
    public TimeSeries getSeries() throws Exception {
        if (cachedSeries == null) {
            cachedSeries = XlsTestsUtils.getSeries(clazz, fileName);
        }
        return cachedSeries;
    }

    public Decimal getFinalCriterionValue(Object... params) throws Exception {
        return XlsTestsUtils.getFinalCriterionValue(clazz, fileName, criterionColumn, params);
    }

    @Override
    public TradingRecord getTradingRecord() throws Exception {
        return XlsTestsUtils.getTradingRecord(clazz, fileName, statesColumn);
    }

}
