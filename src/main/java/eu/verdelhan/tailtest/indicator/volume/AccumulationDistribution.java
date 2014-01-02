package eu.verdelhan.tailtest.indicator.volume;

import eu.verdelhan.tailtest.Tick;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.cache.CachedIndicator;

public class AccumulationDistribution extends CachedIndicator<Double> {

	
	private TimeSeries series;

	public AccumulationDistribution(TimeSeries series) {
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
