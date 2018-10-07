package org.ta4j.core.trading.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Indicator-in-slope rule.
 * </p>
 * Satisfied when the difference of the value of the {@link Indicator indicator}
 * and the previous (n-th) value of the {@link Indicator indicator} is between the values of
 * maxSlope or/and minSlope. It can test both, positive and negative slope.
 */
public class InSlopeRule extends AbstractRule {

    /** The actual indicator */
    private Indicator<Num> ref;
    /** The previous n-th value of ref */
    private PreviousValueIndicator prev;
    /** The minimum slope between ref and prev */
    private Num minSlope;
    /** The maximum slope between ref and prev */
    private Num maxSlope;

    /**
     * Constructor.
     * @param ref the reference indicator
     * @param minSlope minumum slope between reference and previous indicator
     */
    public InSlopeRule(Indicator<Num> ref, Num minSlope) {
        this(ref, 1, minSlope, NaN);
    }
    
    /**
     * Constructor.
     * @param ref the reference indicator
     * @param minSlope minumum slope between value of reference and previous indicator
     * @param maxSlope maximum slope between value of reference and previous indicator
     */
    public InSlopeRule(Indicator<Num> ref, Num minSlope, Num maxSlope) {
        this(ref, 1, minSlope, maxSlope);
    }
    
     /**
     * Constructor.
     * @param ref the reference indicator
     * @param nthPrevious defines the previous n-th indicator
     * @param maxSlope maximum slope between value of reference and previous indicator
     */
    public InSlopeRule(Indicator<Num> ref, int nthPrevious, Num maxSlope) {
    	this(ref, nthPrevious, NaN, maxSlope);
    }

    /**
     * Constructor.
     * @param ref the reference indicator
     * @param nthPrevious defines the previous n-th indicator
     * @param minSlope minumum slope between value of reference and previous indicator
     * @param maxSlope maximum slope between value of reference and previous indicator
     */
    public InSlopeRule(Indicator<Num> ref, int nthPrevious, Num minSlope, Num maxSlope) {
        this.ref = ref;
        this.prev = new PreviousValueIndicator(ref, nthPrevious);
        this.minSlope = minSlope;
        this.maxSlope = maxSlope;
    }

   @Override
   public boolean isSatisfied(int index, TradingRecord tradingRecord) {
	DifferenceIndicator diff = new DifferenceIndicator(ref, prev);
	Num val = diff.getValue(index);
	boolean minSlopeSatisfied = minSlope.isNaN() || val.isGreaterThanOrEqual(minSlope);
	boolean maxSlopeSatisfied = maxSlope.isNaN() || val.isLessThanOrEqual(maxSlope);
	boolean isNaN = minSlope.isNaN() && maxSlope.isNaN();

	final boolean satisfied = minSlopeSatisfied && maxSlopeSatisfied && !isNaN;
	traceIsSatisfied(index, satisfied);
	return satisfied;
   }
}
