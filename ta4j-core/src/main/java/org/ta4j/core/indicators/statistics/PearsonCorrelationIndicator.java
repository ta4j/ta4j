package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Indicator-Pearson-Correlation
 * <p/>
 * see
 * http://www.statisticshowto.com/probability-and-statistics/correlation-coefficient-formula/
 */
public class PearsonCorrelationIndicator extends RecursiveCachedIndicator<Num> {

	private static final long serialVersionUID = 6317147143504055664L;
	
	private final Indicator<Num> indicator1;
	private final Indicator<Num> indicator2;
	private final int barCount;

	/**
	 * Constructor.
	 * 
	 * @param indicator1 the first indicator
	 * @param indicator2 the second indicator
	 * @param barCount the time frame
	 */
	public PearsonCorrelationIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
		super(indicator1);
		this.indicator1 = indicator1;
		this.indicator2 = indicator2;
		this.barCount = barCount;
	}
	

	@Override
	protected Num calculate(int index) {

		Num n = numOf(barCount);

		Num Sx = numOf(0);
		Num Sy = numOf(0);
		Num Sxx = numOf(0);
		Num Syy = numOf(0);
		Num Sxy = numOf(0);
		
		for (int i = Math.max(getTimeSeries().getBeginIndex(), index - barCount + 1); i <= index; i++) {

			Num x = indicator1.getValue(i);
			Num y = indicator2.getValue(i);

			Sx = Sx.plus(x);
			Sy = Sy.plus(y);
			Sxy = Sxy.plus(x.multipliedBy(y));
			Sxx = Sxx.plus(x.multipliedBy(x));
			Syy = Syy.plus(y.multipliedBy(y));
		}

		// (n * Sxx - Sx * Sx) * (n * Syy - Sy * Sy)
		Num toSqrt = (n.multipliedBy(Sxx).minus(Sx.multipliedBy(Sx)))
				.multipliedBy(n.multipliedBy(Syy).minus(Sy.multipliedBy(Sy)));
		
		if (toSqrt.isGreaterThan(numOf(0))) {
			// pearson = (n * Sxy - Sx * Sy) / sqrt((n * Sxx - Sx * Sx) * (n * Syy - Sy * Sy))
			return (n.multipliedBy(Sxy).minus(Sx.multipliedBy(Sy))).dividedBy(numOf(Math.sqrt(toSqrt.doubleValue())));
		}

		return NaN;
	}
}
