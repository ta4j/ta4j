package org.ta4j.core;

public interface ExternalIndicatorTest {

    public TimeSeries getSeries() throws Exception;

    public <P> Indicator<Decimal> getIndicator(P... params) throws Exception;

}
