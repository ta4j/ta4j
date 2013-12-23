package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class MaxPriceIndicator implements Indicator<Double> {

	private TimeSeries data;

	public MaxPriceIndicator(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getMaxPrice();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}
