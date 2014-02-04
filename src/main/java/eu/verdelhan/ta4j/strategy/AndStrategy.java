package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Strategy;

/**
 * AND combination of two strategies
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((strategy == null) ? 0 : strategy.hashCode());
        result = (prime * result) + ((strategy2 == null) ? 0 : strategy2.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AndStrategy other = (AndStrategy) obj;
        if (strategy == null) {
            if (other.strategy != null) {
                return false;
            }
        } else if (!strategy.equals(other.strategy)) {
            return false;
        }
        if (strategy2 == null) {
            if (other.strategy2 != null) {
                return false;
            }
        } else if (!strategy2.equals(other.strategy2)) {
            return false;
        }
        return true;
    }
}
