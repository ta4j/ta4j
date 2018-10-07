package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicator;

/**
 * Constant indicator.
 * </p>
 */
public class ConstantIndicator<T> extends AbstractIndicator<T> {

    private static final long serialVersionUID = -186917236870375024L;
    private T value;

    public ConstantIndicator(TimeSeries series, T t) {
        super(series);
        this.value = t;
    }

    @Override
    public T getValue(int index) {
        return value;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " Value: " + value;
    }
}
