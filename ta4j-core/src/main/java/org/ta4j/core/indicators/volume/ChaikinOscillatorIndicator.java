package org.ta4j.core.indicators.volume;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Chaikin Oscillator.
 * <p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_oscillator">http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_oscillator</a>
 */
public class ChaikinOscillatorIndicator extends CachedIndicator<Num> {

	private static final long serialVersionUID = 2235402541638515096L;
	private final EMAIndicator ema3;
	private final EMAIndicator ema10;

	/**
	 * Constructor.
	 * 
	 * @param series the {@link TimeSeries}
	 * @param shortBarCount (usually 3)
	 * @param longBarCount (usually 10)
	 */
	public ChaikinOscillatorIndicator(TimeSeries series, int shortBarCount, int longBarCount) {
		super(series);
		ema3 = new EMAIndicator(new AccumulationDistributionIndicator(series), shortBarCount);
		ema10 = new EMAIndicator(new AccumulationDistributionIndicator(series), longBarCount);
	}

	/**
	 * Constructor.
	 * 
	 * @param series the {@link TimeSeries}
	 */
	public ChaikinOscillatorIndicator(TimeSeries series) {
		this(series, 3, 10);
	}

	@Override
	protected Num calculate(int index) {
		return ema3.getValue(index).minus(ema10.getValue(index));
	}
}
