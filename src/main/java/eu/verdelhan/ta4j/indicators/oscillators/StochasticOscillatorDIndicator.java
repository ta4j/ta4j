package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * 
 * Receive StochasticOscillatorKIndicator and returns his SMAIndicator(3)
 * 
 */
public class StochasticOscillatorDIndicator implements Indicator<Double> {

	private Indicator<? extends Number> indicator;

	public StochasticOscillatorDIndicator(StochasticOscillatorKIndicator k) {
		indicator = new SMAIndicator(k, 3);
	}

	public StochasticOscillatorDIndicator(Indicator<? extends Number> indicator) {
		this.indicator = indicator;
	}

	@Override
	public Double getValue(int index) {
		return indicator.getValue(index).doubleValue();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " " + indicator;
	}
}
