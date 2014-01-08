package eu.verdelhan.tailtest.indicator.volume;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.cache.CachedIndicator;

public class OnBalanceVolume extends CachedIndicator<Double>{

	private final TimeSeries series;

	public OnBalanceVolume(TimeSeries series) {
		this.series = series;
	}

	@Override
	protected Double calculate(int index) {
		if(index == 0)
			return 0d;
		double yesterdayClose = series.getTick(index - 1).getClosePrice().doubleValue();
		double todayClose = series.getTick(index).getClosePrice().doubleValue();
		
		if(yesterdayClose > todayClose)
			return getValue(index - 1) - series.getTick(index).getVolume().doubleValue();
		if(yesterdayClose < todayClose)
			return getValue(index - 1) + series.getTick(index).getVolume().doubleValue();
		return getValue(index - 1);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

}
