package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;


public class TypicalPriceIndicator implements Indicator<Double> {

	private TimeSeries data;

	public TypicalPriceIndicator(TimeSeries data) {
		this.data = data;
	}

	@Override
	public Double getValue(int index) {
		double maxPrice = data.getTick(index).getMaxPrice();
		double minPrice = data.getTick(index).getMinPrice();
		double closePrice = data.getTick(index).getClosePrice();
		return (maxPrice + minPrice + closePrice) / 3d;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
