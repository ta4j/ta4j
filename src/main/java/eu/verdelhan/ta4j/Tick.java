package eu.verdelhan.ta4j;


import org.joda.time.DateTime;

/**
 * End tick of a period.
 */
public interface Tick {

    DateTime getBeginTime();

    DateTime getEndTime();

    String getDateName();

    String getSimpleDateName();

    double getClosePrice();

    double getOpenPrice();

    int getTrades();

    double getMaxPrice();

    double getAmount();

    double getVolume();

    double getMinPrice();
}
