package net.sf.tail.indicator.volume;

import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.cache.CachedIndicator;

public class OnBalanceVolumeIndicator extends CachedIndicator<Double>{

	private final TimeSeries series;

	public OnBalanceVolumeIndicator(TimeSeries series) {
		this.series = series;
	}
	@Override
	protected Double calculate(int index) {
		if(index == 0)
			return 0d;
		double yesterdayClose = series.getTick(index - 1).getClosePrice();
		double todayClose = series.getTick(index).getClosePrice();
		
		if(yesterdayClose > todayClose)
			return getValue(index - 1) - series.getTick(index).getVolume();
		if(yesterdayClose < todayClose)
			return getValue(index - 1) + series.getTick(index).getVolume();
		return getValue(index - 1);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

}
