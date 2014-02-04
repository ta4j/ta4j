package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicator.helper.CrossIndicator;

/**
 * 
 * Strategy that buy when upper is above lower and cross and sell when lower is
 * above upper and cross
 * 
 */
public class IndicatorCrossedIndicatorStrategy extends AbstractStrategy {

    private final Indicator<Boolean> crossUp;

    private final Indicator<Boolean> crossDown;

    private Indicator<? extends Number> upper;

    private Indicator<? extends Number> lower;

	/**
	 * @param upper the upper indicator
	 * @param lower the lower indicator
	 */
    public IndicatorCrossedIndicatorStrategy(Indicator<? extends Number> upper, Indicator<? extends Number> lower) {
        this.upper = upper;
        this.lower = lower;
        crossUp = new CrossIndicator(upper, lower);
        crossDown = new CrossIndicator(lower, upper);
    }

    @Override
    public boolean shouldEnter(int index) {
        return crossUp.getValue(index);
    }

    @Override
    public boolean shouldExit(int index) {
        return crossDown.getValue(index);
    }

    @Override
    public String toString() {
        return String.format("Cross %s over %s", upper, lower);
    }
}
