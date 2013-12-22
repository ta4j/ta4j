package net.sf.tail.indicator.simple;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;

public class ClosePriceIndicator implements Indicator<Double> {

	private TimeSeries data;

	public ClosePriceIndicator(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getClosePrice();
	}

	public String getName() {
		return getClass().getSimpleName();
	}

}
