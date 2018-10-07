package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A fixed indicator.
 * @param <T> the type of returned value (Double, Boolean, etc.)
 */
public class FixedIndicator<T> extends AbstractIndicator<T> {

    private static final long serialVersionUID = -2946691798800328858L;
    private final List<T> values = new ArrayList<>();

    /**
     * Constructor.
     * @param values the values to be returned by this indicator
     */
    public FixedIndicator(TimeSeries series, T... values) {
        super(series);
        this.values.addAll(Arrays.asList(values));
    }
    
    public void addValue(T value) {
        this.values.add(value);
    }

    @Override
    public T getValue(int index) {
        return values.get(index);
    }

}
