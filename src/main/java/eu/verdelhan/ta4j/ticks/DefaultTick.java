package eu.verdelhan.ta4j.ticks;


import eu.verdelhan.ta4j.Tick;
import org.joda.time.DateTime;

/**
 * Contém todos os possíveis atributos registrados de uma detrminada ação em um
 * único período de tempo.
 * 
 */
public class DefaultTick implements Tick {

	private DateTime beginTime;

	private DateTime endTime;

	private double openPrice = -1;

	private double closePrice = -1;

	private double maxPrice = -1;

	private double minPrice = -1;

	private double amount = 0d;

	private double volume = 0d;

	private int trades = 0;

	public DefaultTick(DateTime beginTime, DateTime endTime) {
		this.beginTime = beginTime;
		this.endTime = endTime;
	}

	@Override
	public double getClosePrice() {
		return closePrice;
	}

	@Override
	public double getOpenPrice() {
		return openPrice;
	}

	@Override
	public int getTrades() {
		return trades;
	}

	@Override
	public double getMaxPrice() {
		return maxPrice;
	}

	@Override
	public double getAmount() {
		return amount;
	}

	@Override
	public double getVolume() {
		return volume;
	}

	/**
	 * Adds a trade at the end of tick period.
	 * @param tradeAmount the tradable amount
	 * @param tradePrice the price
	 */
	public void addTrade(double tradeAmount, double tradePrice) {
		if (openPrice < 0) {
			openPrice = tradePrice;
		}
		closePrice = tradePrice;

		if (maxPrice < 0) {
			maxPrice = tradePrice;
		} else {
			maxPrice = (maxPrice < tradePrice) ? tradePrice : maxPrice;
		}
		if (minPrice < 0) {
			minPrice = tradePrice;
		} else {
			minPrice = (minPrice > tradePrice) ? tradePrice : minPrice;
		}
		amount += tradeAmount;
		volume += tradeAmount * tradePrice;
		trades++;
	}

	public double getMinPrice() {
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