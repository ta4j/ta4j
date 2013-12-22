package net.sf.tail.indicator.simple;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;

public class TradeIndicator implements Indicator<Integer> {

	private TimeSeries data;

	public TradeIndicator(TimeSeries data) {
		this.data = data;
	}

	public Integer getValue(int index) {
		return data.getTick(index).getTrades();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}