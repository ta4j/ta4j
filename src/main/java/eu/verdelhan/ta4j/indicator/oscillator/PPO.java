
package eu.verdelhan.ta4j.indicator.oscillator;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicator.tracker.EMA;

/**
 * Percentage price oscillator (PPO) indicator.
 */
public class PPO implements Indicator<Double> {

    private final EMA shortTermEma;

    private final EMA longTermEma;

    public PPO(Indicator<? extends Number> indicator, int shortTimeFrame, int longTimeFrame) {
        if (shortTimeFrame > longTimeFrame) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }
        shortTermEma = new EMA(indicator, shortTimeFrame);
        longTermEma = new EMA(indicator, longTimeFrame);
    }

	@Override
	public Double getValue(int index) {
		return (shortTermEma.getValue(index) - longTermEma.getValue(index)) / longTermEma.getValue(index) * 100;
	}
}
