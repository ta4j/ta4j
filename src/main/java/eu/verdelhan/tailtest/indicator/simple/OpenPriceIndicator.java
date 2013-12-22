package net.sf.tail.indicator.simple;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;

public class OpenPriceIndicator implements Indicator<Double> {

	private TimeSeries data;

	public OpenPriceIndicator(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getOpenPrice();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}