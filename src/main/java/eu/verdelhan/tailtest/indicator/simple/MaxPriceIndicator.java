package net.sf.tail.indicator.simple;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;

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
