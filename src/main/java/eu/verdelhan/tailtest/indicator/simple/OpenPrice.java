package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class OpenPrice implements Indicator<Double> {

	private TimeSeries data;

	public OpenPrice(TimeSeries data) {
		this.data = data;
	}

	@Override
	public Double getValue(int index) {
		return data.getTick(index).getOpenPrice();
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}
}