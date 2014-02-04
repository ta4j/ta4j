package eu.verdelhan.ta4j.mocks;

import eu.verdelhan.ta4j.Tick;
import java.math.BigDecimal;
import org.joda.time.DateTime;

/**
 * A mock tick with sample data.
 */
public class MockTick implements Tick {

	private DateTime beginTime = new DateTime();

	private DateTime endTime = new DateTime();

	private BigDecimal openPrice = BigDecimal.ZERO;

	private BigDecimal closePrice = BigDecimal.ZERO;

	private BigDecimal maxPrice = BigDecimal.ZERO;

	private BigDecimal minPrice = BigDecimal.ZERO;

	private BigDecimal amount = BigDecimal.ZERO;

	private BigDecimal volume = BigDecimal.ZERO;

	private int trades;

	public MockTick(double closePrice) {
		this.closePrice = new BigDecimal(closePrice);
	}

	public MockTick(DateTime endTime, double closePrice) {
		this.closePrice = new BigDecimal(closePrice);
		this.endTime = endTime;
	}

	public MockTick(double openPrice, double closePrice, double maxPrice, double minPrice) {
		this.openPrice = new BigDecimal(openPrice);
		this.closePrice = new BigDecimal(closePrice);
		this.maxPrice = new BigDecimal(maxPrice);
		this.minPrice = new BigDecimal(minPrice);
	}

	public MockTick(DateTime endTime, double openPrice, double closePrice, double maxPrice, double minPrice, double amount, double volume, int trades) {
		this.endTime = endTime;
		this.openPrice = new BigDecimal(openPrice);
		this.closePrice = new BigDecimal(closePrice);
		this.maxPrice = new BigDecimal(maxPrice);
		this.minPrice = new BigDecimal(minPrice);
		this.amount = new BigDecimal(amount);
		this.volume = new BigDecimal(volume);
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

	public BigDecimal getMinPrice() {
		return minPrice;
	}

}
