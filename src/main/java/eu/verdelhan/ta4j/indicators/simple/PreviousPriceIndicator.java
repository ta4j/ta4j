package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;


public class PreviousPriceIndicator implements Indicator<Double> {

	private TimeSeries data;

	public PreviousPriceIndicator(TimeSeries data) {
		this.data = data;
	}

	@Override
	public Double getValue(int index) {
		return data.getTick(Math.max(0, index - 1)).getClosePrice();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
