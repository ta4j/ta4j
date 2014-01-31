package eu.verdelhan.tailtest.tick;

import eu.verdelhan.tailtest.Tick;
import java.math.BigDecimal;
import org.joda.time.DateTime;

/**
 * Contém todos os possíveis atributos registrados de uma detrminada ação em um
 * único período de tempo.
 * 
 */
public class DefaultTick implements Tick {

	private DateTime beginTime;

	private DateTime endTime;

	private BigDecimal openPrice;

	private BigDecimal closePrice;

	private BigDecimal maxPrice;

	private BigDecimal minPrice;

	private BigDecimal amount;

	private BigDecimal volume;

	private int trades;

	public DefaultTick(DateTime beginTime, DateTime endTime) {
		this.beginTime = beginTime;
		this.endTime = endTime;
	}

	@Override
	public BigDecimal getClosePrice() {
		return closePrice;
	}

	@Override
	public BigDecimal getOpenPrice() {
		return openPrice;
	}

	@Override
	public int getTrades() {
		return trades;
	}

	@Override
	public BigDecimal getMaxPrice() {
		return maxPrice;
	}

	@Override
	public BigDecimal getAmount() {
		return amount;
	}

	@Override
	public BigDecimal getVolume() {
		return volume;
	}

	/**
	 * Adds a trade at the end of tick period.
	 * @param tradeAmount the tradable amount
	 * @param tradePrice the price
	 */
	public void addTrade(BigDecimal tradeAmount, BigDecimal tradePrice) {
		if (openPrice == null) {
			openPrice = tradePrice;
		}
		closePrice = tradePrice;

		if (maxPrice == null) {
			maxPrice = tradePrice;
		} else {
			maxPrice = (maxPrice.compareTo(tradePrice) < 0) ? tradePrice : maxPrice;
		}
		if (minPrice == null) {
			minPrice = tradePrice;
		} else {
			minPrice = (minPrice.compareTo(tradePrice) > 0) ? tradePrice : minPrice;
		}
		if (amount == null) {
			amount = tradeAmount;
		} else {
			amount = amount.add(tradeAmount);
		}
		if (volume == null) {
			volume = tradeAmount.multiply(tradePrice);
		} else {
			volume = volume.add(tradeAmount.multiply(tradePrice));
		}
		trades++;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof DefaultTick) {
			DefaultTick tick = (DefaultTick) obj;
			return (hashCode() == tick.hashCode() && (closePrice == tick
					.getClosePrice()))
					&& (endTime.equals(tick.getEndTime()))
					&& (maxPrice == tick.getMaxPrice())
					&& (minPrice == tick.getMinPrice())
					&& (openPrice == tick.getOpenPrice())
					&& (trades == tick.getTrades())
					&& (amount == tick.getAmount()) && (volume == tick.getVolume());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return 7 * endTime.hashCode();
	}

	public BigDecimal getMinPrice() {
		return minPrice;
	}

	public DateTime getBeginTime() {
		return beginTime;
	}

	public DateTime getEndTime() {
		return endTime;
	}

	public boolean inPeriod(DateTime timestamp) {
		return timestamp == null ? false : (!timestamp.isBefore(beginTime) && timestamp.isBefore(endTime));
	}

	@Override
	public String toString() {
		return String.format("[time: %1$td/%1$tm/%1$tY %1$tH:%1$tM:%1$tS, close price: %2$f]", endTime
				.toGregorianCalendar(), closePrice);
	}

	@Override
	public String getDateName() {
		return endTime.toString("hh:mm dd/MM/yyyy");
	}

	@Override
	public String getSimpleDateName() {
		return endTime.toString("dd/MM/yyyy");
	}
}