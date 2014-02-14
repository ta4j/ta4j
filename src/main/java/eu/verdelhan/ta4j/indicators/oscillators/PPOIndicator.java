
package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.trackers.EMAIndicator;

/**
 * Percentage price oscillator (PPOIndicator) indicator.
 */
public class PPOIndicator implements Indicator<Double> {

    private final EMAIndicator shortTermEma;

    private final EMAIndicator longTermEma;

    public PPOIndicator(Indicator<? extends Number> indicator, int shortTimeFrame, int longTimeFrame) {
        if (shortTimeFrame > longTimeFrame) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }
        shortTermEma = new EMAIndicator(indicator, shortTimeFrame);
        longTermEma = new EMAIndicator(indicator, longTimeFrame);
    }

	@Override
	public Double getValue(int index) {
		return (shortTermEma.getValue(index) - longTermEma.getValue(index)) / longTermEma.getValue(index) * 100;
	}
}
