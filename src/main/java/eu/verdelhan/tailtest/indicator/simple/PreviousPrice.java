package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class PreviousPrice implements Indicator<Double> {

	private TimeSeries data;

	public PreviousPrice(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getPreviousPrice();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}
