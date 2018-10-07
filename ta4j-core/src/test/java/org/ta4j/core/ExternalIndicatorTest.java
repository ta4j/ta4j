package org.ta4j.core;

import org.ta4j.core.num.Num;

public interface ExternalIndicatorTest {

    /**
     * Gets the TimeSeries used by an external indicator calculator.
     * @return TimeSeries from the external indicator calculator
     * @throws Exception if the external calculator throws an Exception
     */
    TimeSeries getSeries() throws Exception;

    /**
     * Sends indicator parameters to an external indicator calculator and
     * returns the externally calculated indicator.
     * @param params indicator parameters
     * @return Indicator<Num> from the external indicator calculator
     * @throws Exception if the external calculator throws an Exception
     */
    Indicator<Num> getIndicator(Object... params) throws Exception;

}
