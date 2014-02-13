package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Indicator;
import java.math.BigDecimal;

/**
 * Enter: when the value of the first {@link indicator} is strictly less than the value of the second one
 * Exit: when the value of the first {@link indicator} is strictly greater than the value of the second one
 */
public class IndicatorOverIndicatorStrategy extends AbstractStrategy {

    private Indicator<BigDecimal> first;

    private Indicator<BigDecimal> second;

	/**
	 * @param first the first indicator
	 * @param second the second indicator
	 */
    public IndicatorOverIndicatorStrategy(Indicator<BigDecimal> first, Indicator<BigDecimal> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean shouldEnter(int index) {
        return (first.getValue(index).compareTo(second.getValue(index)) < 0);
    }

    @Override
    public boolean shouldExit(int index) {
        return (first.getValue(index).compareTo(second.getValue(index)) > 0);
    }

    @Override
    public String toString() {
        return String.format("%s upper: %s lower: %s", this.getClass().getSimpleName(), first, second);
    }
}
