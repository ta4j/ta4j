package net.sf.tail.indicator.simple;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;

public class AmountIndicator implements Indicator<Double> {

	private TimeSeries data;

	public AmountIndicator(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getAmount();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}