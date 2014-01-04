package eu.verdelhan.tailtest;

import org.joda.time.DateTime;

/**
 * End tick of a period.
 * 
 */
public interface Tick {
    DateTime getDate();

    String getDateName();

    String getSimpleDateName();

    double getClosePrice();

    double getOpenPrice();

    int getTrades();

    double getMaxPrice();

    double getAmount();

    double getVolume();

    double getVariation();

    double getMinPrice();

    double getPreviousPrice();
}
