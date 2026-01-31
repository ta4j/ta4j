/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class XLSIndicatorTest implements ExternalIndicatorTest {

    private final Class<?> clazz;
    private final String fileName;
    private final int column;
    private BarSeries cachedSeries = null;
    private final NumFactory numFactory;

    /**
     * Constructor.
     *
     * @param clazz    class containing the file resources
     * @param fileName file name of the file containing the workbook
     * @param column   column number containing the calculated indicator values
     */
    public XLSIndicatorTest(Class<?> clazz, String fileName, int column, NumFactory numFactory) {
        this.clazz = clazz;
        this.fileName = fileName;
        this.column = column;
        this.numFactory = numFactory;
    }

    /**
     * Gets the BarSeries from the XLS file.
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
     * Gets the Indicator from the XLS file given the parameters.
     *
     * @param params indicator parameters
     * @return Indicator from the file given the parameters
     * @throws Exception if getIndicator throws IOException or DataFormatException
     */
    @Override
    public Indicator<Num> getIndicator(Object... params) throws Exception {
        return XlsTestsUtils.getIndicator(clazz, fileName, column, getSeries().numFactory(), params);
    }

}
