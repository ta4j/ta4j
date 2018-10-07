package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;

/**
 * A fixed boolean indicator.
 */
public class FixedBooleanIndicator extends FixedIndicator<Boolean> {

    private static final long serialVersionUID = 3841241374807117753L;

    /**
     * Constructor.
     * @param values the values to be returned by this indicator
     */
    public FixedBooleanIndicator(TimeSeries series, Boolean... values) {
        super(series, values);
    }
}
