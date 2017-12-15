package org.ta4j.core;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.function.Function;

import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockIndicator;

public abstract class IndicatorTest {

    private Class<?> testClass;
    Constructor<?> constructor;
    private String xlsFileName;
    private int xlsIndicatorIndex;
    
    /**
     * constructor
     * @param indicatorClass the indicator class (used to locate the indicator constructor)
     * @param xlsFileName the name of the xls file
     * @param xlsIndicatorIndex the zero-based column number of the expected indicator values in the xls
     * setupXlsTesting must be called @Before calling any of the xls methods
     * @throws Exception
     */
    public IndicatorTest(Class<RSIIndicator> indicatorClass, String xlsFileName, int xlsIndicatorIndex) throws Exception {
        this.testClass = this.getClass();
        // indicators extending this class must have a constructor that takes (TimeSeries series, T... params)
        // or this will throw an exception
        this.constructor = indicatorClass.getConstructor(TimeSeries.class, Object[].class);
        this.xlsFileName = xlsFileName;
        this.xlsIndicatorIndex = xlsIndicatorIndex;
    }
    /**
     * 
     * @param params array of indicator parameters
     * @return indicator based on xls series and xls calculated values from params
     * @throws Exception
     */
    public <T> Indicator<Decimal> XlsIndicator(T... params) throws Exception {
        List<Decimal> values = XlsTestsUtils.getXlsValues(testClass, xlsFileName, xlsIndicatorIndex, params);
        return new MockIndicator(XlsTestsUtils.getXlsSeries(testClass, xlsFileName), values);
    }
    /**
     * 
     * @param params array of indicator parameters
     * @return indicator based on xls series and indicator calculated values from params
     * @throws Exception
     */
    public TimeSeries getXlsSeries() throws Exception {
        return XlsTestsUtils.getXlsSeries(testClass, xlsFileName);
    }
//    public <T> Indicator<Decimal> XlsIndicator(TimeSeries series, T... params) throws Exception {
//        return (Indicator<Decimal>) constructor.newInstance(XlsTestsUtils.getXlsSeries(testClass, xlsFileName), params);
//    }
    public <T> Indicator<Decimal> TestIndicator(TimeSeries series, T... params) throws Exception {
        return (Indicator<Decimal>) constructor.newInstance(series, params);
    }
    /**
     * 
     * @param expected indicator of expected values (eg from getXlsIndicator)
     * @param actual indicator of actual values (eg from indicator constructor)
     */
    public void assertIndicatorEquals(Indicator<Decimal> expected, Indicator<Decimal> actual) {
        org.junit.Assert.assertEquals("Size does not match,",
                expected.getTimeSeries().getBarCount(), actual.getTimeSeries().getBarCount());
        for (int i = 0; i < expected.getTimeSeries().getBarCount(); i++) {
            org.junit.Assert.assertEquals(String.format("Values at index <%d> does not match,", i),
                    expected.getValue(i).doubleValue(), actual.getValue(i).doubleValue(), TATestsUtils.TA_OFFSET);
        }
    }
}
