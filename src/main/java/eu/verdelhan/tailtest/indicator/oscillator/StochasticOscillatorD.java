package eu.verdelhan.tailtest.indicator.oscillator;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.indicator.tracker.SMA;

/**
 * 
 * Receive StochasticOscillatorK and returns his SMA(3)
 * 
 * @author tgthies
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

	public Double getValue(int index) {

		return indicator.getValue(index).doubleValue();
	}

	public String getName() {
		return getClass().getSimpleName() + " " + indicator.getName();
	}
}
