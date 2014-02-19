package eu.verdelhan.ta4j.mocks;

import eu.verdelhan.ta4j.Tick;
import org.joda.time.DateTime;

/**
 * A mock tick with sample data.
 */
public class MockTick implements Tick {

	private DateTime beginTime = new DateTime();

	private DateTime endTime = new DateTime();

	private double openPrice = 0d;

	private double closePrice = 0d;

	private double maxPrice = 0d;

	private double minPrice = 0d;

	private double amount = 0d;

	private double volume = 0d;

	private int trades = 0;

	public MockTick(double closePrice) {
		this.closePrice = closePrice;
	}

	public MockTick(DateTime endTime, double closePrice) {
		this.closePrice = closePrice;
		this.endTime = endTime;
	}

	public MockTick(double openPrice, double closePrice, double maxPrice, double minPrice) {
		this.openPrice = openPrice;
		this.closePrice = closePrice;
		this.maxPrice = maxPrice;
		this.minPrice = minPrice;
	}

	public MockTick(DateTime endTime, double openPrice, double closePrice, double maxPrice, double minPrice, double amount, double volume, int trades) {
		this.endTime = endTime;
		this.openPrice = openPrice;
		this.closePrice = closePrice;
		this.maxPrice = maxPrice;
		this.minPrice = minPrice;
		this.amount = amount;
		this.volume = volume;
		this.trades = trades;
	}

	public DateTime getBeginTime() {
		return beginTime;
	}

	public DateTime getEndTime() {
		return endTime;
	}

	public String getDateName() {
		return "";
	}

	public String getSimpleDateName() {
		return "";
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

	public double getMinPrice() {
		return minPrice;
	}

}
