package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.HighestValue;
import eu.verdelhan.ta4j.indicators.simple.ClosePrice;
import java.math.BigDecimal;

/**
 * Aroon up indicator.
 */
public class AroonUp implements Indicator<Double> {

	private final int timeFrame;

	private final ClosePrice closePriceIndicator;

	private final HighestValue<BigDecimal> highestClosePriceIndicator;

	public AroonUp(TimeSeries timeSeries, int timeFrame) {
		this.timeFrame = timeFrame;
		closePriceIndicator = new ClosePrice(timeSeries);
		highestClosePriceIndicator = new HighestValue<BigDecimal>(closePriceIndicator, timeFrame);
	}

	@Override
	public Double getValue(int index) {
		int realTimeFrame = Math.min(timeFrame, index + 1);

		// Getting the number of ticks since the highest close price
		int endIndex = index - realTimeFrame;
		int nbTicks = 0;
		for (int i = index; i > endIndex; i--) {
			if (closePriceIndicator.getValue(i)
					.compareTo(highestClosePriceIndicator.getValue(index)) == 0) {
				break;
			}
			nbTicks++;
		}
		
		return (realTimeFrame - nbTicks) / realTimeFrame * 100d;
	}
}
