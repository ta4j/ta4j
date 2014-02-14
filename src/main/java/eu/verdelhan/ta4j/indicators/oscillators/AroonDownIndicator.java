package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.LowestValueIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import java.math.BigDecimal;

/**
 * Aroon down indicator.
 */
public class AroonDownIndicator implements Indicator<Double> {

	private final int timeFrame;

	private final ClosePriceIndicator closePriceIndicator;

	private final LowestValueIndicator<BigDecimal> lowestClosePriceIndicator;

	public AroonDownIndicator(TimeSeries timeSeries, int timeFrame) {
		this.timeFrame = timeFrame;
		closePriceIndicator = new ClosePriceIndicator(timeSeries);
		lowestClosePriceIndicator = new LowestValueIndicator<BigDecimal>(closePriceIndicator, timeFrame);
	}

	@Override
	public Double getValue(int index) {
		int realTimeFrame = Math.min(timeFrame, index + 1);

		// Getting the number of ticks since the lowest close price
		int endIndex = index - realTimeFrame;
		int nbTicks = 0;
		for (int i = index; i > endIndex; i--) {
			if (closePriceIndicator.getValue(i)
					.compareTo(lowestClosePriceIndicator.getValue(index)) == 0) {
				break;
			}
			nbTicks++;
		}
		
		return (realTimeFrame - nbTicks) / realTimeFrame * 100d;
	}
}
