package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.cache.CachedIndicator;
import eu.verdelhan.tailtest.indicator.helper.HighestValue;
import eu.verdelhan.tailtest.indicator.helper.LowestValue;
import eu.verdelhan.tailtest.indicator.simple.MaxPrice;
import eu.verdelhan.tailtest.indicator.simple.MinPrice;

public class ParabolicSar extends CachedIndicator<Double> {

	private double acceleration;

	private final TimeSeries series;

	private double extremePoint;

	private final LowestValue lowestValueIndicator;

	private final HighestValue highestValueIndicator;
	
	private final int timeFrame;
	
	public ParabolicSar(TimeSeries series, int timeFrame) {
		this.acceleration = 0.02d;
		this.series = series;
		this.lowestValueIndicator = new LowestValue(new MinPrice(series), timeFrame);
		this.highestValueIndicator = new HighestValue(new MaxPrice(series), timeFrame);
		this.timeFrame = timeFrame;
	}

	@Override
	protected Double calculate(int index) {

		if (index <= 1) {
			extremePoint = series.getTick(index).getClosePrice();
			return extremePoint;
		}
		double sar;

		// trend switch
		if (series.getTick(index - 2).getClosePrice() > series.getTick(index - 1).getClosePrice() && series.getTick(
				index - 1).getClosePrice() < series.getTick(index).getClosePrice())
		{
			sar = extremePoint;
			extremePoint = highestValueIndicator.getValue(index);
			acceleration = 0.02;
		}
		// trend switch
		else if((series.getTick(index - 2).getClosePrice() < series.getTick(index - 1).getClosePrice() && series
						.getTick(index - 1).getClosePrice() > series.getTick(index).getClosePrice())) {
			
			sar = extremePoint;
			extremePoint = lowestValueIndicator.getValue(index);
			acceleration = 0.02;
			
		}
		//DownTrend
		else if (series.getTick(index - 1).getClosePrice() >= series.getTick(index).getClosePrice()) {
			double lowestValue = lowestValueIndicator.getValue(index);
			if (extremePoint > lowestValue) {
				acceleration = acceleration >= 0.19 ? 0.2 : acceleration + 0.02d;
				extremePoint = lowestValue;
			}
			sar = acceleration * (extremePoint - getValue(index - 1)) + getValue(index - 1);
			if (sar <= series.getTick(index - 1).getMaxPrice())
				sar = series.getTick(index - 1).getMaxPrice();
			else if (sar <= series.getTick(index - 2).getMaxPrice())
				sar = series.getTick(index - 2).getMaxPrice();
			if (sar <= series.getTick(index).getMaxPrice()) {
				sar = series.getTick(index).getMinPrice();
			}

		}
		//UpTrend
		else {
			double highestValue = highestValueIndicator.getValue(index);
			if (extremePoint < highestValue) {
				acceleration = acceleration >= 0.19 ? 0.2 : acceleration + 0.02;
				extremePoint = highestValue;
			}
			sar = acceleration * (extremePoint - getValue(index - 1)) + getValue(index - 1);
			if (sar >= series.getTick(index - 1).getMinPrice())
				sar = series.getTick(index - 1).getMinPrice();
			else if (sar >= series.getTick(index - 2).getMinPrice())
				sar = series.getTick(index - 2).getMinPrice();
			if (sar >= series.getTick(index).getMinPrice()) {
				sar = series.getTick(index).getMaxPrice();
			}

		}
		return sar;
	}

	@Override
	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}

}
