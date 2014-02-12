package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Strategy;

/**
 * OR combination of two strategies.
 * Enter: according to the provided {@link strategy strategies}
 * Exit: according to the provided {@link strategy strategies}
 */
public class OrStrategy extends AbstractStrategy {

    private Strategy strategy;
    private Strategy strategy2;

	/**
	 * @param strategy the first strategy
	 * @param strategy2  the second strategy
	 */
    public OrStrategy(Strategy strategy, Strategy strategy2) {
        this.strategy = strategy;
        this.strategy2 = strategy2;
    }

    @Override
    public boolean shouldEnter(int index) {
        return strategy.shouldEnter(index) || strategy2.shouldEnter(index);
    }

    @Override
    public boolean shouldExit(int index) {
        return strategy.shouldExit(index) || strategy2.shouldExit(index);
    }

    @Override
    public String toString() {
        return String.format("%s or %s", strategy, strategy2);
    }
}
