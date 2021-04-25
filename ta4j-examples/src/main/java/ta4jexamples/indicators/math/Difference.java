package ta4jexamples.indicators.math;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * This and the the other indicator operation classes could be implemented as static objects or enums.
 * Each uses a simple "pointwise" operator to calculate the "sum" of two Num instances at index i.
 * This could easily be made a field of a single "binary arithmetoc operator" class.
 * I have chosen to use simple, explicitly named classes instead.
 * 
 */
public class Difference extends BinaryIndicatorOperation {
	
	public Difference(Indicator<Num> leftOperand, Indicator<Num> rightOperand) {
		super(leftOperand, rightOperand);
	}

	@Override
	public Num getValue(int index) {
		return getLeftOperand().getValue(index).minus(getRightOperand().getValue(index));
	}
}
