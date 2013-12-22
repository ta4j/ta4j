package net.sf.tail.indicator.simple;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;

public class MinPriceIndicator implements Indicator<Double> {

	private TimeSeries data;

	public MinPriceIndicator(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getMinPrice();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}
