package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class TradeCount implements Indicator<Integer> {

	private TimeSeries data;

	public TradeCount(TimeSeries data) {
		this.data = data;
	}

	public Integer getValue(int index) {
		return data.getTick(index).getTrades();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}