package ta4jexamples.indicators.math;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

public abstract class BinaryIndicatorOperation extends AbstractNumericIndicator {

	private final Indicator<Num> leftOperand;
	private final Indicator<Num> rightOperand;
		
	public BinaryIndicatorOperation(Indicator<Num> leftOperand, Indicator<Num> rightOperand) {
		this.leftOperand = leftOperand;
		this.rightOperand = rightOperand;
	}

	// `arbitrarily choose first to respond; unlikely to be called for this object
	@Override
	public BarSeries getBarSeries() {
		return leftOperand.getBarSeries();
	}

	public Indicator<Num> getLeftOperand() {
		return leftOperand;
	}

	public Indicator<Num> getRightOperand() {
		return rightOperand;
	}	
}
