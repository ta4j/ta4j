package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMA;

public class AwesomeOscillator extends CachedIndicator<Double> {

	private SMA sma5;

	private SMA sma34;

	public AwesomeOscillator(Indicator<? extends Number> average, int timeFrameSma1, int timeFrameSma2) {
		this.sma5 = new SMA(average, timeFrameSma1);
		this.sma34 = new SMA(average, timeFrameSma2);
	}

	public AwesomeOscillator(Indicator<? extends Number> average) {
		this.sma5 = new SMA(average, 5);
		this.sma34 = new SMA(average, 34);
	}

	@Override
	protected Double calculate(int index) {
		return sma5.getValue(index) - sma34.getValue(index);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
