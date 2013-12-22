package net.sf.tail.indicator.simple;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;

public class PreviousPriceIndicator implements Indicator<Double> {

	private TimeSeries data;

	public PreviousPriceIndicator(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getPreviousPrice();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}
