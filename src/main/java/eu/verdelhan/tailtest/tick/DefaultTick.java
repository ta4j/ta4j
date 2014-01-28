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

	private DateTime date;

	private BigDecimal openPrice;

	private BigDecimal closePrice;

	private BigDecimal maxPrice;

	private BigDecimal minPrice;

	private BigDecimal variation;

	private BigDecimal previousPrice;

	private BigDecimal amount;

	private BigDecimal volume;

	private int trades;

	public DefaultTick(double closePrice) {
		this.closePrice = new BigDecimal(closePrice);
	}

	public DefaultTick(DateTime date, double closePrice) {
		this.closePrice = new BigDecimal(closePrice);
		this.date = date;
	}

	public DefaultTick(DateTime date, BigDecimal closePrice) {
		this.closePrice = closePrice;
		this.date = date;
	}

	public DefaultTick(DateTime date, double openPrice, double closePrice, double maxPrice, double minPrice, double variation,
			double previousPrice, double amount, double volume, int trades) {
		this.date = date;
		this.openPrice = new BigDecimal(openPrice);
		this.closePrice = new BigDecimal(closePrice);
		this.maxPrice = new BigDecimal(maxPrice);
		this.minPrice = new BigDecimal(minPrice);
		this.variation = new BigDecimal(variation);
		this.previousPrice = new BigDecimal(previousPrice);
		this.amount = new BigDecimal(amount);
		this.volume = new BigDecimal(volume);
		this.trades = trades;
	}

	public DefaultTick(DateTime date, double openPrice, double closePrice, double maxPrice, double minPrice) {
		this.date = date;
		this.openPrice = new BigDecimal(openPrice);
		this.closePrice = new BigDecimal(closePrice);
		this.maxPrice = new BigDecimal(maxPrice);
		this.minPrice = new BigDecimal(minPrice);
	}

	public DefaultTick(double openPrice, double closePrice, double maxPrice, double minPrice) {
		this.openPrice = new BigDecimal(openPrice);
		this.closePrice = new BigDecimal(closePrice);
		this.maxPrice = new BigDecimal(maxPrice);
		this.minPrice = new BigDecimal(minPrice);
	}
	
	public DefaultTick(double d, DateTime date) {
		this.closePrice = new BigDecimal(d);
		this.date = date;
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

	@Override
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
			return (hashCode() == tick.hashCode() && (variation == tick.getVariation()) && (closePrice == tick
					.getClosePrice()))
					&& (date.equals(tick.getDate()))
					&& (maxPrice == tick.getMaxPrice())
					&& (minPrice == tick.getMinPrice())
					&& (openPrice == tick.getOpenPrice())
					&& (previousPrice == getPreviousPrice())
					&& (trades == tick.getTrades())
					&& (amount == tick.getAmount()) && (volume == tick.getVolume());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return 7 * date.hashCode();
	}

	public BigDecimal getVariation() {
		return variation;
	}

	public BigDecimal getMinPrice() {
		return minPrice;
	}

	public BigDecimal getPreviousPrice() {
		return previousPrice;
	}

	public DateTime getDate() {
		return date;
	}

	@Override
	public String toString() {
		return String.format("[time: %1$td/%1$tm/%1$tY %1$tH:%1$tM:%1$tS, close price: %2$f]", date
				.toGregorianCalendar(), closePrice);
	}

	@Override
	public String getDateName() {
		return this.date.toString("hh:mm dd/MM/yyyy");
	}

	@Override
	public String getSimpleDateName() {
		return this.date.toString("dd/MM/yyyy");
	}
}