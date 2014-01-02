package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class MinPrice implements Indicator<Double> {

	private TimeSeries data;

	public MinPrice(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getMinPrice();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}
