package org.ta4j.core.indicators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

/**
 * Abstract {@link Indicator indicator}.
 * </p>
 */
public abstract class AbstractIndicator<T> implements Indicator<T> {

    /** The logger */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private TimeSeries series;

    /**
     * Constructor.
     * @param series the related time series
     */
    public AbstractIndicator(TimeSeries series) {
        this.series = series;
    }

    @Override
    public TimeSeries getTimeSeries() {
        return series;
    }



    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public Num numOf(Number number){
        return series.numOf(number);
    }


}
