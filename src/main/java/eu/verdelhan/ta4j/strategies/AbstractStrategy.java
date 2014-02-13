package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;

public abstract class AbstractStrategy implements Strategy {

    @Override
    public boolean shouldOperate(Trade trade, int index) {
        if (trade.isNew()) {
            return shouldEnter(index);
        } else if (trade.isOpened()) {
            return shouldExit(index);
        }
        return false;
    }

    @Override
    public Strategy and(Strategy strategy) {
        return new AndStrategy(this, strategy);
    }

    @Override
    public Strategy or(Strategy strategy) {
        return new OrStrategy(this, strategy);
    }
}