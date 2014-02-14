package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.HighestValueIndicator;
import eu.verdelhan.ta4j.indicators.helpers.LowestValueIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MaxPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MinPriceIndicator;
import java.math.BigDecimal;

/**
 * 
 * Receives timeSeries and timeFrame and calculates the StochasticOscillatorKIndicator
 * over ClosePriceIndicator, or receives an indicator, MaxPriceIndicator and
 * MinPriceIndicator and returns StochasticOsiclatorK over this indicator.
 * 
 */
public class StochasticOscillatorKIndicator implements Indicator<Double> {
	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	private MaxPriceIndicator maxPriceIndicator;

	private MinPriceIndicator minPriceIndicator;

	public StochasticOscillatorKIndicator(TimeSeries timeSeries, int timeFrame) {
		this(new ClosePriceIndicator(timeSeries), timeFrame, new MaxPriceIndicator(timeSeries), new MinPriceIndicator(
				timeSeries));
	}

	public StochasticOscillatorKIndicator(Indicator<? extends Number> indicator, int timeFrame,
			MaxPriceIndicator maxPriceIndicator, MinPriceIndicator minPriceIndicator) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
		this.maxPriceIndicator = maxPriceIndicator;
		this.minPriceIndicator = minPriceIndicator;
	}

	@Override
	public Double getValue(int index) {
		HighestValueIndicator<BigDecimal> highestHigh = new HighestValueIndicator<BigDecimal>(maxPriceIndicator, timeFrame);
		LowestValueIndicator<BigDecimal> lowestMin = new LowestValueIndicator<BigDecimal>(minPriceIndicator, timeFrame);

		BigDecimal highestHighPrice = highestHigh.getValue(index);
		BigDecimal lowestLowPrice = lowestMin.getValue(index);

		return ((indicator.getValue(index).doubleValue() - lowestLowPrice.doubleValue()) / (highestHighPrice.doubleValue() - lowestLowPrice.doubleValue())) * 100d;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
