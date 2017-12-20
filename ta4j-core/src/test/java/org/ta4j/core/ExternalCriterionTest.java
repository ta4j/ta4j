package org.ta4j.core;

public interface ExternalCriterionTest {

    public TimeSeries getSeries() throws Exception;

    public <P> Decimal getFinalCriterionValue(P... params) throws Exception;

    public TradingRecord getTradingRecord() throws Exception;

}
