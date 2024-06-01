/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.indicators;

import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.TestIndicator;
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class XLSIndicatorTest implements ExternalIndicatorTest {

  private final Class<?> clazz;
  private final String fileName;
  private final int column;
  private BacktestBarSeries cachedSeries = null;
  private final NumFactory numFactory;


  /**
   * Constructor.
   *
   * @param clazz class containing the file resources
   * @param fileName file name of the file containing the workbook
   * @param column column number containing the calculated indicator values
   */
  public XLSIndicatorTest(final Class<?> clazz, final String fileName, final int column, final NumFactory numFactory) {
    this.clazz = clazz;
    this.fileName = fileName;
    this.column = column;
    this.numFactory = numFactory;
  }


  /**
   * Gets the BarSeries from the XLS file.
   *
   * @return BarSeries from the file
   *
   * @throws Exception if getSeries throws IOException or DataFormatException
   */
  @Override
  public BacktestBarSeries getSeries() throws Exception {
    if (this.cachedSeries == null) {
      this.cachedSeries = XlsTestsUtils.getSeries(this.clazz, this.fileName, this.numFactory);
    }

    this.cachedSeries.rewind();
    return this.cachedSeries;
  }


  /**
   * Gets the Indicator from the XLS file given the parameters.
   *
   * @param params indicator parameters
   *
   * @return Indicator from the file given the parameters
   *
   * @throws Exception if getIndicator throws IOException or DataFormatException
   */
  @Override
  public TestIndicator<Num> getIndicator(final Object... params) throws Exception {
    return new TestIndicator<>(
        getSeries(),
        XlsTestsUtils.getIndicator(
            this.clazz,
            this.fileName,
            this.column,
            getSeries().numFactory(),
            params
        )
    );
  }

}
