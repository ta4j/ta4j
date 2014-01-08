package eu.verdelhan.tailtest;

import org.joda.time.Period;

/**
 * Set of ticks separated by a predefined period (e.g. 15 minutes)
 * 
 */
public interface TimeSeries {

    /**
     * @param i an index
     * @return the tick at the i position
     */
    Tick getTick(int i);

    int getSize();

    int getBegin();

    int getEnd();

    String getName();

    String getPeriodName();

    Period getPeriod();
}
