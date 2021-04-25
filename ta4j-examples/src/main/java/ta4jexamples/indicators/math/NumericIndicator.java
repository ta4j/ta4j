package ta4jexamples.indicators.math;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

public interface NumericIndicator extends Indicator<Num> {
	
	// convenient ways to create rules
	
	CrossedUpIndicatorRule crossedUp(Indicator<Num> other);
	CrossedDownIndicatorRule crossedDown(Indicator<Num> other);
	OverIndicatorRule isOver(Indicator<Num> other);
	UnderIndicatorRule isUnder(Indicator<Num> other);

	// convenient ways to create "pseudo" indicators that do simple math 
	// these are the ones I have found a use for, others can easily be added
	
	NumericIndicator minus(Indicator<Num> other);
	NumericIndicator plus(Indicator<Num> other);
	NumericIndicator dividedBy(Indicator<Num> other);
	
	public NumericIndicator multipliedBy(Num multiplier);
	public NumericIndicator dividedBy(Num divisor);

}
