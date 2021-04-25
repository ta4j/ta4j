package ta4jexamples.indicators.math;

import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

// Unlike the binary indicator operators, I have implemented these simple unary oparations as static methods
// I don't want to create class named Log, Sqrt, and so on
public class UnaryIndicatorOperation extends AbstractNumericIndicator {
	
	public static UnaryIndicatorOperation sqrt(Indicator<Num> operand) {
		return new UnaryIndicatorOperation(operand, Num::sqrt);
	}
	
	public static UnaryIndicatorOperation log(Indicator<Num> operand) {
		return new UnaryIndicatorOperation(operand, Num::log);
	}
	
	public static UnaryIndicatorOperation abs(Indicator<Num> operand) {
		return new UnaryIndicatorOperation(operand, Num::abs);
	}
	
	public static UnaryIndicatorOperation ceil(Indicator<Num> operand) {
		return new UnaryIndicatorOperation(operand, Num::ceil);
	}
	
	public static UnaryIndicatorOperation floor(Indicator<Num> operand) {
		return new UnaryIndicatorOperation(operand, Num::floor);
	}
	
	public static UnaryIndicatorOperation negate(Indicator<Num> operand) {
		return new UnaryIndicatorOperation(operand, Num::negate);
	}
	
	private final Indicator<Num> operand;
	private final Function<Num, Num> pointwiseOperator;

	public UnaryIndicatorOperation(Indicator<Num> operand, Function<Num, Num> pointwiseOperator) {
		super();
		this.operand = operand;
		this.pointwiseOperator = pointwiseOperator;
	}

	@Override
	public Num getValue(int index) {
		return pointwiseOperator.apply(operand.getValue(index));
	}

	@Override
	public BarSeries getBarSeries() {
		return operand.getBarSeries();
	}

}
