package net.sf.tail.tick;

import net.sf.tail.Tick;

import org.joda.time.DateTime;

/**
 * Contém todos os possíveis atributos registrados de uma detrminada ação em um
 * único período de tempo.
 * 
 * @author Marcio
 * 
 */
public class DefaultTick implements Tick {

	private DateTime date;

	private double openPrice;

	private double closePrice;

	private double maxPrice;

	private double minPrice;

	private double variation;

	private double previousPrice;

	private double amount;

	private double volume;

	private int trades;

	public DefaultTick(double closePrice) {
		super();
		this.closePrice = closePrice;
	}

	public DefaultTick(DateTime data, double closePrice) {
		super();
		this.closePrice = closePrice;
		this.date = data;
	}

	public DefaultTick(DateTime data, double openPrice, double closePrice, double maxPrice, double minPrice, double variation,
			double previousPrice, double amount, double volume, int trades) {
		super();
		this.date = data;
		this.openPrice = openPrice;
		this.closePrice = closePrice;
		this.maxPrice = maxPrice;
		this.minPrice = minPrice;
		this.variation = variation;
		this.previousPrice = previousPrice;
		this.amount = amount;
		this.volume = volume;
		this.trades = trades;
	}

	public DefaultTick(DateTime data, double openPrice, double closePrice, double maxPrice, double minPrice) {
		super();
		this.date = data;
		this.openPrice = openPrice;
		this.closePrice = closePrice;
		this.maxPrice = maxPrice;
		this.minPrice = minPrice;
	}

	public DefaultTick(double openPrice, double closePrice, double maxPrice, double minPrice) {
		super();
		this.openPrice = openPrice;
		this.closePrice = closePrice;
		this.maxPrice = maxPrice;
		this.minPrice = minPrice;
	}
	
	public DefaultTick(double d, DateTime dateTime) {
		this.closePrice = d;
		this.date = dateTime;
	}

	public double getClosePrice() {
		return closePrice;
	}

	public double getOpenPrice() {
		return openPrice;
	}

	public int getTrades() {
		return trades;
	}

	public double getMaxPrice() {
		return maxPrice;
	}

	public double getAmount() {
		return amount;
	}

	public double getVolume() {
		return volume;
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

	public double getVariation() {
		return variation;
	}

	public double getMinPrice() {
		return minPrice;
	}

	public double getPreviousPrice() {
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
	public String getDateName() {
		return this.date.toString("hh:mm dd/MM/yyyy");
	}
	public String getSimpleDateName() {
		return this.date.toString("dd/MM/yyyy");
	}

}
