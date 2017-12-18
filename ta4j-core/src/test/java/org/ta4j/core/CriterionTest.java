package org.ta4j.core;

import java.io.IOException;
import java.util.List;
import java.util.zip.DataFormatException;

import org.ta4j.core.mocks.MockTradingRecord;

import static org.ta4j.core.XlsTestsUtils.*;

public abstract class CriterionTest {

    CriterionFactory criterionFactory;
    private int criterionIndex;
    private int statesIndex;

    /**
     * Constructor sets up test object and initializes the xls utilities.
     * 
     * @param criterionFactory the criterion factory
     * @param fileName the name of the data file
     * @param criterionIndex the zero-based column number of the expected criterion values in the data
     * @throws IOException in init()
     */
    public CriterionTest(CriterionFactory criterionFactory, String fileName, int criterionIndex, int statesIndex) throws IOException {
        this.criterionFactory = criterionFactory;
        this.criterionIndex = criterionIndex;
        this.statesIndex = statesIndex;
        XlsTestsUtils.init(this.getClass(), fileName);
    }

    /**
     * Generates a criterion value based on parameters passed to a data source.
     * @param params criterion parameters
     * @return Decimal the criterion value
     * @throws DataFormatException in getValues()
     */
    public <P> Decimal getCriterion(P... params) throws DataFormatException {
        List<Decimal> values = XlsTestsUtils.getValues(criterionIndex, params);
        return values.get(values.size() - 1);
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
     * Generates a criterion value for a trading record from data and parameters.
     * @param data the data for the criterion
     * @param tradingRecord the trading record to analyze
     * @param params a set of criterion parameters
     * @return Decimal the criterion value
     */
    public <D,P> Decimal testCriterion(D data, TradingRecord tradingRecord, P... params) {
        AnalysisCriterion analysisCriterion = criterionFactory.createCriterion(params);
        return Decimal.valueOf(analysisCriterion.calculate((TimeSeries) data, tradingRecord));
    }

    /**
     * Generates a criterion value for a trade from time series data and parameters.
     * @param data the data for the criterion
     * @param tradingRecord the trading record to analyze
     * @param params a set of criterion parameters
     * @return Decimal the criterion value
     */
    public <D,P> Decimal testCriterion(D data, Trade trade, P... params) {
        AnalysisCriterion analysisCriterion = criterionFactory.createCriterion(params);
        return Decimal.valueOf(analysisCriterion.calculate((TimeSeries) data, trade));
    }

    /**
     * Generates a trading record from a states column
     * @return TradingRecord the trading record
     */
    public TradingRecord getTradingRecord() throws Exception {
        List<Decimal> states = getValues(statesIndex);
        return new MockTradingRecord(states);
    }
}
