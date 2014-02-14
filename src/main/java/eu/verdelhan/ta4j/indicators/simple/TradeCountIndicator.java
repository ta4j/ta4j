package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;

public class TradeCountIndicator implements Indicator<Integer> {

	private TimeSeries data;

	public TradeCountIndicator(TimeSeries data) {
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