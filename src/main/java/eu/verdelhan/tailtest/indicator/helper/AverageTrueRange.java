package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.cache.CachedIndicator;

public class AverageTrueRange extends CachedIndicator<Double> {

	private final int timeFrame;
	private final TrueRange tr;
	public AverageTrueRange(TimeSeries series, int timeFrame) {
		this.timeFrame = timeFrame;
		this.tr = new TrueRange(series);
	}
	
	@Override
	protected Double calculate(int index) {
		if(index == 0)
			return 1d;
		return ((getValue(index - 1) * (timeFrame-1)) / timeFrame) + tr.getValue(index) / timeFrame;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + this.timeFrame;
	}

}
