package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.cache.CachedIndicator;
import eu.verdelhan.tailtest.indicator.oscilator.AwesomeOscillatorIndicator;
import eu.verdelhan.tailtest.indicator.simple.AverageHighLowIndicator;

public class AccelerationDecelerationIndicator extends CachedIndicator<Double> {
	
	private AwesomeOscillatorIndicator awesome;
	
	private SMAIndicator sma5;

	
	public AccelerationDecelerationIndicator(TimeSeries series, int timeFrameSma1, int timeFrameSma2) {
		this.awesome = new AwesomeOscillatorIndicator(new AverageHighLowIndicator(series), timeFrameSma1, timeFrameSma2);
		this.sma5 = new SMAIndicator(awesome, timeFrameSma1);
	}
	
	public AccelerationDecelerationIndicator(TimeSeries series) {
		this(series, 5, 34);
	}
	
	@Override
	protected Double calculate(int index) {
		return awesome.getValue(index) - sma5.getValue(index);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

}
