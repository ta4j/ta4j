package eu.verdelhan.tailtest.indicator.oscilator;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.helper.HighestValueIndicator;
import eu.verdelhan.tailtest.indicator.helper.LowestValueIndicator;
import eu.verdelhan.tailtest.indicator.simple.ClosePriceIndicator;
import eu.verdelhan.tailtest.indicator.simple.MaxPriceIndicator;
import eu.verdelhan.tailtest.indicator.simple.MinPriceIndicator;

/**
 * 
 * Receives timeSeries and timeFrame and calculates the StochasticOscilatorK
 * over ClosePriceIndicator, or receives an indicator, MaxPriceIndicator and
 * MinPriceIndicator and returns StochasticOsiclatorK over this indicator.
 * 
 * @author tgthies
 * 
 */
public class StochasticOscilatorK implements Indicator<Double> {
	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	private MaxPriceIndicator maxPriceIndicator;

	private MinPriceIndicator minPriceIndicator;

	public StochasticOscilatorK(TimeSeries timeSeries, int timeFrame) {
		this(new ClosePriceIndicator(timeSeries), timeFrame, new MaxPriceIndicator(timeSeries), new MinPriceIndicator(
				timeSeries));
	}

	public StochasticOscilatorK(Indicator<? extends Number> indicator, int timeFrame,
			MaxPriceIndicator maxPriceIndicator, MinPriceIndicator minPriceIndicator) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
		this.maxPriceIndicator = maxPriceIndicator;
		this.minPriceIndicator = minPriceIndicator;
	}

	public Double getValue(int index) {
		HighestValueIndicator highestHigh = new HighestValueIndicator(maxPriceIndicator, timeFrame);
		LowestValueIndicator lowestMin = new LowestValueIndicator(minPriceIndicator, timeFrame);

		double highestHighPrice = highestHigh.getValue(index);
		double lowestLowPrice = lowestMin.getValue(index);

		return ((indicator.getValue(index).doubleValue() - lowestLowPrice) / (highestHighPrice - lowestLowPrice)) * 100d;
	}

	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
