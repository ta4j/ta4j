package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;

/**
 * Indicator-Pearson-Correlation
 * <p/>
 * see
 * http://www.statisticshowto.com/probability-and-statistics/correlation-coefficient-formula/
 */
public class PearsonCorrelationIndicator extends RecursiveCachedIndicator<Decimal> {

	private static final long serialVersionUID = 6317147143504055664L;
	
	private final Indicator<Decimal> indicator1;
	private final Indicator<Decimal> indicator2;
	private final int timeFrame;

	/**
	 * Constructor.
	 * 
	 * @param indicator1 the first indicator
	 * @param indicator2 the second indicator
	 * @param timeFrame the time frame
	 */
	public PearsonCorrelationIndicator(Indicator<Decimal> indicator1, Indicator<Decimal> indicator2, int timeFrame) {
		super(indicator1);
		this.indicator1 = indicator1;
		this.indicator2 = indicator2;
		this.timeFrame = timeFrame;
	}
	

	@Override
	protected Decimal calculate(int index) {

		Decimal n = Decimal.valueOf(timeFrame);

		Decimal Sx = Decimal.ZERO;
		Decimal Sy = Decimal.ZERO;
		Decimal Sxx = Decimal.ZERO;
		Decimal Syy = Decimal.ZERO;
		Decimal Sxy = Decimal.ZERO;
		
		for (int i = Math.max(getTimeSeries().getBeginIndex(), index - timeFrame + 1); i <= index; i++) {

			Decimal x = indicator1.getValue(i);
			Decimal y = indicator2.getValue(i);

			Sx = Sx.plus(x);
			Sy = Sy.plus(y);
			Sxy = Sxy.plus(x.multipliedBy(y));
			Sxx = Sxx.plus(x.multipliedBy(x));
			Syy = Syy.plus(y.multipliedBy(y));
		}

		// (n * Sxx - Sx * Sx) * (n * Syy - Sy * Sy)
		Decimal toSqrt = (n.multipliedBy(Sxx).minus(Sx.multipliedBy(Sx)))
				.multipliedBy(n.multipliedBy(Syy).minus(Sy.multipliedBy(Sy)));
		
		if (toSqrt.isGreaterThan(Decimal.ZERO)) {
			// pearson = (n * Sxy - Sx * Sy) / sqrt((n * Sxx - Sx * Sx) * (n * Syy - Sy * Sy))
			return (n.multipliedBy(Sxy).minus(Sx.multipliedBy(Sy))).dividedBy(Decimal.valueOf(Math.sqrt(toSqrt.doubleValue())));
		}

		return Decimal.NaN;
	}
}
