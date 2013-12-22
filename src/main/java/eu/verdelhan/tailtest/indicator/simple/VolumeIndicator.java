package net.sf.tail.indicator.simple;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;

public class VolumeIndicator implements Indicator<Double> {

	private TimeSeries data;

	public VolumeIndicator(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getVolume();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}