package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class TrueRange implements Indicator<Double>{

	private TimeSeries series;

	public TrueRange(TimeSeries series) {
		this.series = series;
		this.getValue(0);
	}
	public String getName() {
		return getClass().getSimpleName();
	}
	
	
	public Double getValue(int index) {
		
		double ts = series.getTick(index).getMaxPrice() - series.getTick(index).getMinPrice();
		double ys = index == 0? 0 : series.getTick(index).getMaxPrice() - series.getTick(index - 1).getClosePrice();
		double yst = index == 0? 0 : series.getTick(index - 1).getClosePrice() - series.getTick(index).getMinPrice();
		double max = Math.max(Math.abs(ts), Math.abs(ys));
		
		return Math.max(max, Math.abs(yst));
		
	}
	

}
