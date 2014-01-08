package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.cache.CachedIndicator;
import eu.verdelhan.tailtest.indicator.oscillator.AwesomeOscillator;
import eu.verdelhan.tailtest.indicator.simple.AverageHighLow;

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
