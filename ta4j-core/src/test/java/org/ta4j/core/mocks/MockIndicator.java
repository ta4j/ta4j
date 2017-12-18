package org.ta4j.core.mocks;

import java.util.List;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;


public class MockIndicator implements Indicator<Decimal> {
    
    private TimeSeries series;
    private List<Decimal> values;

    public MockIndicator(TimeSeries series, List<Decimal> values) {
        this.series = series;
        this.values = values;
    }
    @Override
    public Decimal getValue(int index) {
        return values.get(index);
    }

    @Override
    public TimeSeries getTimeSeries() {
        return series;
    }

}
