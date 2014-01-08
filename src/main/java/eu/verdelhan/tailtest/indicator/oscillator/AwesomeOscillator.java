package eu.verdelhan.tailtest.indicator.oscillator;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.indicator.cache.CachedIndicator;
import eu.verdelhan.tailtest.indicator.tracker.SMA;

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
