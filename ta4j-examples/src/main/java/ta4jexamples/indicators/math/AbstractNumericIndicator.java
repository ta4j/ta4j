package ta4jexamples.indicators.math;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

public abstract class AbstractNumericIndicator implements NumericIndicator {

	@Override
	public Num numOf(Number number) {
		return getBarSeries().numOf(number);
	}
	
	public CrossedUpIndicatorRule crossedUp(Indicator<Num> other) {
		return new CrossedUpIndicatorRule(this, other);
	}
	
	public CrossedDownIndicatorRule crossedDown(Indicator<Num> other) {
		return new CrossedDownIndicatorRule(this, other);
	}
	
	public OverIndicatorRule isOver(Indicator<Num> other) {
		return new OverIndicatorRule(this, other);
	}
	
	public UnderIndicatorRule isUnder(Indicator<Num> other) {
		return new UnderIndicatorRule(this, other);
	}

	@Override
	public NumericIndicator minus(Indicator<Num> other) {
		return new Difference(this, other);
	}

	@Override
	public NumericIndicator plus(Indicator<Num> other) {
		return new Sum(this, other);
	}

	@Override
	public NumericIndicator dividedBy(Indicator<Num> other) {
		return new Quotient(this, other);
	}

	@Override
	public NumericIndicator dividedBy(Num divisor) {
		return new Quotient(this, divisor);
	}

	@Override
	public NumericIndicator multipliedBy(Num multiplier) {
		return new Product(this, multiplier);
	}

	


}
