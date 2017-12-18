package org.ta4j.core;

import java.io.IOException;
import java.util.List;
import java.util.zip.DataFormatException;

import org.ta4j.core.mocks.MockIndicator;

public abstract class IndicatorTest {

    IndicatorFactory indicatorFactory;
    private int indicatorIndex;


    /**
     * Constructor sets up test object and initializes the xls utilities.
     * @param indicatorFactory the indicator factory
     * @param fileName the name of the data file
     * @param xlsIndicatorIndex the zero-based column number of the expected indicator values in the data
     * @throws IOException in init()
     */
    public IndicatorTest(IndicatorFactory indicatorFactory, String fileName, int indicatorIndex) throws IOException {
        this.indicatorFactory = indicatorFactory;
        this.indicatorIndex = indicatorIndex;
        XlsTestsUtils.init(this.getClass(), fileName);
    }

    /**
     * Generates an indicator based on parameters passed to a data source.
     * @param params indicator parameters
     * @return Indicator<Decimal> the indicator
     * @throws DataFormatException in getValues()
     */
    public <P> Indicator<Decimal> getIndicator(P... params) throws DataFormatException {
        List<Decimal> values = XlsTestsUtils.getValues(indicatorIndex, params);
        return new MockIndicator(XlsTestsUtils.getSeries(), values);
    }

    /**
     * Gets a TimeSeries from the data section of a data source.
     * @return TimeSeries from the data source
     * @throws DataFormatException in getSeries()
     */
    public TimeSeries getSeries() throws DataFormatException {
        return XlsTestsUtils.getSeries();
    }

    /**
     * Generates an indicator from data and parameters.
     * @param data the data for the indicator
     * @param params a set of indicator parameters
     * @return Indicator<Decimal> the new indicator
     */
    public <D,P> Indicator<Decimal> testIndicator(D data, P... params) {
        return indicatorFactory.createIndicator(data, params);
    }

}
