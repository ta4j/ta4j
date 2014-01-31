package eu.verdelhan.tailtest;

import java.math.BigDecimal;
import org.joda.time.DateTime;

/**
 * End tick of a period.
 */
public interface Tick {
    DateTime getBeginTime();

    DateTime getEndTime();

    String getDateName();

    String getSimpleDateName();

    BigDecimal getClosePrice();

    BigDecimal getOpenPrice();

    int getTrades();

    BigDecimal getMaxPrice();

    BigDecimal getAmount();

    BigDecimal getVolume();

    BigDecimal getVariation();

    BigDecimal getMinPrice();

	boolean inPeriod(DateTime timestamp);

	/**
	 * Adds a trade at the end of tick period.
	 * @param tradeAmount the tradable amount
	 * @param tradePrice the price
	 */
	void addTrade(BigDecimal tradeAmount, BigDecimal tradePrice);
}
