package eu.verdelhan.ta4j;

import org.joda.time.Period;

/**
 * Set of ticks separated by a predefined period (e.g. 15 minutes)
 */
public interface TimeSeries {

    /**
     * @param i an index
     * @return the tick at the i position
     */
    Tick getTick(int i);

	/**
	 * @return the number of ticks in the series
	 */
    int getSize();

	/**
	 * @return the begin index of the series
	 */
    int getBegin();

	/**
	 * @return the end index of the series
	 */
    int getEnd();

	/**
	 * @return the name of the series
	 */
    String getName();

	/**
	 * @return the period name of the series (e.g. "from 12:00 21/01/2014 to 12:15 21/01/2014")
	 */
    String getPeriodName();

	/**
	 * @return the period of the series
	 */
    Period getPeriod();
}
