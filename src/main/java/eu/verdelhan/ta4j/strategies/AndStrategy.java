package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Strategy;

/**
 * AND combination of two strategies.
 * Enter: according to the provided {@link strategy strategies}
 * Exit: according to the provided {@link strategy strategies}
 */
public class AndStrategy extends AbstractStrategy {

    private Strategy strategy;
    private Strategy strategy2;

	/**
	 * @param strategy the first strategy
	 * @param strategy2 the second strategy
	 */
    public AndStrategy(Strategy strategy, Strategy strategy2) {
        this.strategy = strategy;
        this.strategy2 = strategy2;
    }

    @Override
    public boolean shouldEnter(int index) {
        return strategy.shouldEnter(index) && strategy2.shouldEnter(index);
    }

    @Override
    public boolean shouldExit(int index) {
        return strategy.shouldExit(index) && strategy2.shouldExit(index);
    }

    @Override
    public String toString() {
        return String.format("%s and %s", strategy, strategy2);
    }
}
