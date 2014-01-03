package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class ClosePrice implements Indicator<Double> {

	private TimeSeries data;

	public ClosePrice(TimeSeries data) {
		this.data = data;
	}

	@Override
	public Double getValue(int index) {
		return data.getTick(index).getClosePrice();
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

}
