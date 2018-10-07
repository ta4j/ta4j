package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;

/**
 * A fixed decimal indicator.
 */
public class FixedDecimalIndicator extends FixedIndicator<Num> {

    private static final long serialVersionUID = 139320494945326149L;

    /**
     * Constructor.
     * @param values the values to be returned by this indicator
     */
    public FixedDecimalIndicator(TimeSeries series, double... values) {
        super(series);
        for (double value : values) {
            addValue(numOf(value));
        }
    }
    
    /**
     * Constructor.
     * @param values the values to be returned by this indicator
     */
    public FixedDecimalIndicator(TimeSeries series, String... values) {
        super(series);
        for (String value : values) {
            addValue(numOf(new BigDecimal(value)));
        }
    }
}
