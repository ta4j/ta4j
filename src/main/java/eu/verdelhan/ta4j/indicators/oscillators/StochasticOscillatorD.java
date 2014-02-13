package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.trackers.SMA;

/**
 * 
 * Receive StochasticOscillatorK and returns his SMA(3)
 * 
 */
public class StochasticOscillatorD implements Indicator<Double> {

	private Indicator<? extends Number> indicator;

	public StochasticOscillatorD(StochasticOscillatorK k) {
		indicator = new SMA(k, 3);
	}

	public StochasticOscillatorD(Indicator<? extends Number> indicator) {
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
