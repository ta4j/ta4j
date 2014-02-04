package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;

public class TradeCount implements Indicator<Integer> {

	private TimeSeries data;

	public TradeCount(TimeSeries data) {
		this.data = data;
	}

	@Override
	public Integer getValue(int index) {
		return data.getTick(index).getTrades();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}