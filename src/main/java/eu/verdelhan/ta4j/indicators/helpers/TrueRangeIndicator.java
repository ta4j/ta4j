package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;

public class TrueRangeIndicator implements Indicator<Double>{

	private TimeSeries series;

	public TrueRangeIndicator(TimeSeries series) {
		this.series = series;
	}
	
	@Override
	public Double getValue(int index) {
		double ts = series.getTick(index).getMaxPrice() - series.getTick(index).getMinPrice();
		double ys = index == 0? 0 : series.getTick(index).getMaxPrice() - series.getTick(index - 1).getClosePrice();
		double yst = index == 0? 0 : series.getTick(index - 1).getClosePrice() - series.getTick(index).getMinPrice();
		double max = Math.max(Math.abs(ts), Math.abs(ys));
		
		return Math.max(max, Math.abs(yst));
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
