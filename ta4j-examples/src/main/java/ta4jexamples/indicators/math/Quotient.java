package ta4jexamples.indicators.math;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

public class Quotient extends BinaryIndicatorOperation {

	public Quotient(Indicator<Num> leftOperand, Indicator<Num> rightOperand) {
		super(leftOperand, rightOperand);
	}

	// ConstantIndicator is not cached; it never uses the forced BarSeries constructor arg
	// In the unlikely event a Quotient object is asked for its bar series, it will return 
	// that of the left operand anyway
	public Quotient(Indicator<Num> leftOperand, Num divisor) {
		this(leftOperand, new ConstantIndicator<Num>(null, divisor));  
	}

	@Override
	public Num getValue(int index) { 
		return getLeftOperand().getValue(index).dividedBy(getRightOperand().getValue(index));
	}

}
