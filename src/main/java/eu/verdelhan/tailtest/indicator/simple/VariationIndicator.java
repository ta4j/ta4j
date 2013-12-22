package net.sf.tail.indicator.simple;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;

public class VariationIndicator implements Indicator<Double> {

	private TimeSeries data;

	public VariationIndicator(TimeSeries data) {
		this.data = data;
	}

	public Double getValue(int index) {
		return data.getTick(index).getVariation();
	}

	public String getName() {
		return getClass().getSimpleName();
	}
}
