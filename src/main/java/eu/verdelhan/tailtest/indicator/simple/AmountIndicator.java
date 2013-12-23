package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

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