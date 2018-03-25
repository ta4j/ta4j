/**
 * 
 */
package org.ta4j.core.indicators;

import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;

/**
 * The "CHOP" index is used to indicate side-ways markets 
		 see {@link https://www.tradingview.com/wiki/Choppiness_Index_(CHOP)}
		 100++ * LOG10( SUM(ATR(1), n) / ( MaxHi(n) - MinLo(n) ) ) / LOG10(n)
				n = User defined period length.
				LOG10(n) = base-10 LOG of n
				ATR(1) = Average True Range (Period of 1)
				SUM(ATR(1), n) = Sum of the Average True Range over past n bars 
				MaxHi(n) = The highest high over past n bars
				
				++ usually this index is between 0 and 100, but could be scalled differently by the 'scaleTo' arg of the constructor
 * @implNote precision may be lost because of the double calcuations using log10
 */
public class ChopIndicator extends CachedIndicator<Num> {
	public static final double DEFAULT_UPPER_THRESHOLD = 61.8;
	public static final double DEFAULT_LOWER_THRESHOLD = 38.2;

	private ATRIndicator atrIndicator;
	TimeSeries timeseries;
	private int timeFrame;
	public final double LOG10n;
	double MaxHi = 0-Float.MAX_VALUE, MinLo = Float.MAX_VALUE;
	private HighestValueIndicator hvi;
	private LowestValueIndicator lvi;
	private final double scaleUpTo;

	/**
	 * ctor
	 * @param timeseries the time series or @param timeseries the {@link TimeSeries}
	 * @param ciTimeFrame time-frame often something like '14'
	 * @param scaleTo maximum value to scale this oscillator, usually '1' or '100'
	 */
	public ChopIndicator( TimeSeries timeseries, int ciTimeFrame, int scaleTo ) {
		super( timeseries );
        this.atrIndicator = new ATRIndicator( timeseries, ciTimeFrame );
        hvi = new HighestValueIndicator( new MaxPriceIndicator(timeseries), ciTimeFrame );
        lvi = new LowestValueIndicator( new MinPriceIndicator(timeseries), ciTimeFrame );
        this.timeFrame = ciTimeFrame;
        this.timeseries = timeseries;
        this.LOG10n = Math.log10( ciTimeFrame );
        this.scaleUpTo = scaleTo;
	}

	@Override
	public Num calculate( int index ) {
		double sumAtr = 0;
        Num summ = atrIndicator.getValue( index );
        for( int i = 1; i<timeFrame; ++i ) {
        	summ = summ.plus( atrIndicator.getValue( index - i ) );
        }
		Num a = summ.dividedBy( (hvi.getValue(index).minus(lvi.getValue(index))) );
		double chop = scaleUpTo * Math.log10( a.doubleValue() ) / LOG10n;
		return DoubleNum.valueOf( chop );
	}
}
