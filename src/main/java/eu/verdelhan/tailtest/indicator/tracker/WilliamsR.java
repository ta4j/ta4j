package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.helper.HighestValue;
import eu.verdelhan.tailtest.indicator.helper.LowestValue;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.indicator.simple.MaxPrice;
import eu.verdelhan.tailtest.indicator.simple.MinPrice;

public class WilliamsR implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	private MaxPrice maxPriceIndicator;

	private MinPrice minPriceIndicator;

	public WilliamsR(TimeSeries timeSeries, int timeFrame) {
		this(new ClosePrice(timeSeries), timeFrame, new MaxPrice(timeSeries), new MinPrice(
				timeSeries));
	}

	public WilliamsR(Indicator<? extends Number> indicator, int timeFrame,
			MaxPrice maxPriceIndicator, MinPrice minPriceIndicator) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
		this.maxPriceIndicator = maxPriceIndicator;
		this.minPriceIndicator = minPriceIndicator;
	}

	@Override
	public Double getValue(int index) {
		HighestValue highestHigh = new HighestValue(maxPriceIndicator, timeFrame);
		LowestValue lowestMin = new LowestValue(minPriceIndicator, timeFrame);

		double highestHighPrice = highestHigh.getValue(index);
		double lowestLowPrice = lowestMin.getValue(index);

		return ((highestHighPrice - indicator.getValue(index).doubleValue()) / (highestHighPrice - lowestLowPrice))
				* -100d;
	}

	@Override
	public String getName() {
		return getClass().getName();
	}
}
