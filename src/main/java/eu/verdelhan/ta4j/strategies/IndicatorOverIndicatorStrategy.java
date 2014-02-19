package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Indicator;

/**
 * Enter: when the value of the first {@link indicator} is strictly less than the value of the second one
 * Exit: when the value of the first {@link indicator} is strictly greater than the value of the second one
 */
public class IndicatorOverIndicatorStrategy extends AbstractStrategy {

    private Indicator<Double> first;

    private Indicator<Double> second;

	/**
	 * @param first the first indicator
	 * @param second the second indicator
	 */
    public IndicatorOverIndicatorStrategy(Indicator<Double> first, Indicator<Double> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean shouldEnter(int index) {
        return (first.getValue(index) < second.getValue(index));
    }

    @Override
    public boolean shouldExit(int index) {
        return (first.getValue(index) > second.getValue(index));
    }

    @Override
    public String toString() {
        return String.format("%s upper: %s lower: %s", this.getClass().getSimpleName(), first, second);
    }
}
