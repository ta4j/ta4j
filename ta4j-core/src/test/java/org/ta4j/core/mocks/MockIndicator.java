package org.ta4j.core.mocks;

import java.util.List;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;


public class MockIndicator implements Indicator<Decimal> {

    private TimeSeries series;
    private List<Decimal> values;

    /**
     * Constructor.
     * 
     * @param series TimeSeries of the Indicator
     * @param values Indicator values
     */
    public MockIndicator(TimeSeries series, List<Decimal> values) {
        this.series = series;
        this.values = values;
    }

    /**
     * Gets a value from the Indicator
     * 
     * @param index Indicator value to get
     * @return Decimal Indicator value at index
     */
    public Decimal getValue(int index) {
        return values.get(index);
    }

    /**
     * Gets the Indicator TimeSeries.
     * 
     * @return TimeSeries of the Indicator
     */
    public TimeSeries getTimeSeries() {
        return series;
    }

}
