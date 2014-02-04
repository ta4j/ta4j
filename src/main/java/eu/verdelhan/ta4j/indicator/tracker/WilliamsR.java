package eu.verdelhan.ta4j.indicator.tracker;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.helper.HighestValue;
import eu.verdelhan.ta4j.indicator.helper.LowestValue;
import eu.verdelhan.ta4j.indicator.simple.ClosePrice;
import eu.verdelhan.ta4j.indicator.simple.MaxPrice;
import eu.verdelhan.ta4j.indicator.simple.MinPrice;
import java.math.BigDecimal;

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
		HighestValue<BigDecimal> highestHigh = new HighestValue<BigDecimal>(maxPriceIndicator, timeFrame);
		LowestValue<BigDecimal> lowestMin = new LowestValue<BigDecimal>(minPriceIndicator, timeFrame);

		double highestHighPrice = highestHigh.getValue(index).doubleValue();
		double lowestLowPrice = lowestMin.getValue(index).doubleValue();

		return ((highestHighPrice - indicator.getValue(index).doubleValue()) / (highestHighPrice - lowestLowPrice))
				* -100d;
	}

	@Override
	public String toString() {
		return getClass().getName();
	}
}
