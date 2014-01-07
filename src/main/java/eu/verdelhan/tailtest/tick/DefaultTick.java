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

	public DefaultTick(DateTime data, double closePrice) {
		this.closePrice = new BigDecimal(closePrice);
		this.date = data;
	}

	public DefaultTick(DateTime data, double openPrice, double closePrice, double maxPrice, double minPrice, double variation,
			double previousPrice, double amount, double volume, int trades) {
		super();
		this.date = data;
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

	public DefaultTick(DateTime data, double openPrice, double closePrice, double maxPrice, double minPrice) {
		super();
		this.date = data;
		this.openPrice = new BigDecimal(openPrice);
		this.closePrice = new BigDecimal(closePrice);
		this.maxPrice = new BigDecimal(maxPrice);
		this.minPrice = new BigDecimal(minPrice);
	}

	public DefaultTick(double openPrice, double closePrice, double maxPrice, double minPrice) {
		super();
		this.openPrice = new BigDecimal(openPrice);
		this.closePrice = new BigDecimal(closePrice);
		this.maxPrice = new BigDecimal(maxPrice);
		this.minPrice = new BigDecimal(minPrice);
	}
	
	public DefaultTick(double d, DateTime dateTime) {
		this.closePrice = new BigDecimal(d);
		this.date = dateTime;
	}

	public BigDecimal getClosePrice() {
		return closePrice;
	}

	public BigDecimal getOpenPrice() {
		return openPrice;
	}

	public int getTrades() {
		return trades;
	}

	public BigDecimal getMaxPrice() {
		return maxPrice;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getVolume() {
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

	public String getDateName() {
		return this.date.toString("hh:mm dd/MM/yyyy");
	}

	public String getSimpleDateName() {
		return this.date.toString("dd/MM/yyyy");
	}
}