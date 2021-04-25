package ta4jexamples.indicators.math;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

public class Sum extends BinaryIndicatorOperation {

	public Sum(Indicator<Num> leftOperand, Indicator<Num> rightOperand) {
		super(leftOperand, rightOperand);
	}

	@Override
	public Num getValue(int index) {
		return getLeftOperand().getValue(index).plus(getRightOperand().getValue(index));
	}

}
