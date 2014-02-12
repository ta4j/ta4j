package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Strategy;

/**
 * Combination of a BUY strategy and a SELL strategy.
 * Enter: according to the provided BUY {@link strategy}
 * Exit: according to the provided SELL {@link strategy}
 */
public class CombinedBuyAndSellStrategy extends AbstractStrategy {

    private Strategy buyStrategy;

    private Strategy sellStrategy;

	/**
	 * @param buyStrategy the buy strategy
	 * @param sellStrategy the sell strategy
	 */
    public CombinedBuyAndSellStrategy(Strategy buyStrategy, Strategy sellStrategy) {
        this.buyStrategy = buyStrategy;
        this.sellStrategy = sellStrategy;
    }

    @Override
    public boolean shouldEnter(int index) {
        return buyStrategy.shouldEnter(index);
    }

    @Override
    public boolean shouldExit(int index) {
        return sellStrategy.shouldExit(index);
    }

    @Override
    public String toString() {
        return String.format("Combined strategy using buy strategy %s and sell strategy %s", buyStrategy, sellStrategy);
    }
}
