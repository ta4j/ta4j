package org.ta4j.core.indicators;

import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.XlsTestsUtils;
import org.ta4j.core.num.Num;

import java.util.function.Function;

public class XLSIndicatorTest implements ExternalIndicatorTest {

    private Class<?> clazz;
    private String fileName;
    private int column;
    private TimeSeries cachedSeries = null;
    private final Function<Number, Num> numFunction;

    /**
     * Constructor.
     * 
     * @param clazz class containing the file resources
     * @param fileName file name of the file containing the workbook
     * @param column column number containing the calculated indicator values
     */
    public XLSIndicatorTest(Class<?> clazz, String fileName, int column, Function<Number, Num> numFunction) {
        this.clazz = clazz;
        this.fileName = fileName;
        this.column = column;
        this.numFunction = numFunction;
    }

    /**
     * Gets the TimeSeries from the XLS file.
     * 
     * @return TimeSeries from the file
     * @throws Exception if getSeries throws IOException or DataFormatException
     */
    public TimeSeries getSeries() throws Exception {
        if (cachedSeries == null) {
            cachedSeries = XlsTestsUtils.getSeries(clazz, fileName,numFunction);
        }
        return cachedSeries;
    }

    /**
     * Gets the Indicator from the XLS file given the parameters.
     * 
     * @param params indicator parameters
     * @return Indicator from the file given the parameters
     * @throws Exception if getIndicator throws IOException or
     *             DataFormatException
     */
    public Indicator<Num> getIndicator(Object... params) throws Exception {
        return XlsTestsUtils.getIndicator(clazz, fileName, column, getSeries().function(), params);
    }

}
