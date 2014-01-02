package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class Volume implements Indicator<Double> {

	private TimeSeries data;

	public Volume(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getVolume();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}