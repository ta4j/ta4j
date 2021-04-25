package ta4jexamples.indicators.math;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

public class Product extends BinaryIndicatorOperation {

	// I haven't had occasion to multiply 2 series (indicators) together;
	// so there is only a constructor to create a multiple
	// Price X Volume would likely be a useful formulation, though
	public Product(Indicator<Num> leftOperand, Num muliplier) {
		super(leftOperand, new ConstantIndicator<Num>(null, muliplier));
	}

	@Override
	public Num getValue(int index) {
		return getLeftOperand().getValue(index).multipliedBy(getRightOperand().getValue(index));
	}

}
