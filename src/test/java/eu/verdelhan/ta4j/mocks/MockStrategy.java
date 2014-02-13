package eu.verdelhan.ta4j.mocks;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.strategies.AbstractStrategy;

public class MockStrategy extends AbstractStrategy {

    private Operation[] enter;

    private Operation[] exit;

    public MockStrategy(Operation[] enter, Operation[] exit) {
        this.enter = enter;
        this.exit = exit;
    }

    @Override
    public boolean shouldEnter(int index) {
        return (enter[index] != null);
    }

    @Override
    public boolean shouldExit(int index) {
        return (exit[index] != null);
    }

    @Override
    public String toString() {
        return "Mock Strategy";
    }
}
