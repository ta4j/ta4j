package org.ta4j.core.mocks;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;


public class MockIndicator implements Indicator<Num> {

    private static final long serialVersionUID = -1083818948051189894L;
    private TimeSeries series;
    private List<Num> values;

    /**
     * Constructor.
     * 
     * @param series TimeSeries of the Indicator
     * @param values Indicator values
     */
    public MockIndicator(TimeSeries series, List<Num> values) {
        this.series = series;
        this.values = values;
    }

    /**
     * Constructor.
     * 
     * @param numFunction the {@link Function} to convert a number instance to the corresponding Num instance
     * @param values Indicator values
     */
    public MockIndicator(Function<Number, Num> numFunction, Num... values) {
        TimeSeries series = new MockTimeSeries(numFunction, values.length);
        this.series = series;
        List<Num> listValues = Arrays.asList(values);
        this.values = listValues;
    }

    /**
     * Gets a value from the Indicator
     * 
     * @param index Indicator value to get
     * @return Num Indicator value at index
     */
    public Num getValue(int index) {
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

    @Override
    public Num numOf(Number number) {
        return series.numOf(number);
    }

}
