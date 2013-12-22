package net.sf.tail.indicator.volume;

import net.sf.tail.Tick;
import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.cache.CachedIndicator;

public class AccumulationDistributionIndicator extends CachedIndicator<Double> {

	
	private TimeSeries series;

	public AccumulationDistributionIndicator(TimeSeries series) {
		this.series = series;
	}

	@Override
	protected Double calculate(int index) {
		if(index == 0)
			return 0d;
		Tick tick = series.getTick(index);
		
		return (((tick.getClosePrice() - tick.getMinPrice()) - (tick.getMaxPrice() - tick.getClosePrice())) * tick.getVolume()) / (tick.getMaxPrice() - tick.getMinPrice()) + getValue(index -1);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

}
