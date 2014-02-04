package eu.verdelhan.ta4j.indicator.tracker;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.cache.CachedIndicator;
import eu.verdelhan.ta4j.indicator.oscillator.AwesomeOscillator;
import eu.verdelhan.ta4j.indicator.simple.AverageHighLow;

public class AccelerationDeceleration extends CachedIndicator<Double> {
	
	private AwesomeOscillator awesome;
	
	private SMA sma5;

	
	public AccelerationDeceleration(TimeSeries series, int timeFrameSma1, int timeFrameSma2) {
		this.awesome = new AwesomeOscillator(new AverageHighLow(series), timeFrameSma1, timeFrameSma2);
		this.sma5 = new SMA(awesome, timeFrameSma1);
	}
	
	public AccelerationDeceleration(TimeSeries series) {
		this(series, 5, 34);
	}
	
	@Override
	protected Double calculate(int index) {
		return awesome.getValue(index) - sma5.getValue(index);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

}
