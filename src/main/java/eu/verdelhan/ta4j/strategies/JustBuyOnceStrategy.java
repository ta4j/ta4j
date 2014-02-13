package eu.verdelhan.ta4j.strategies;

/**
 * Strategy which buys just once and never moves later.
 * Enter: the first time it's called
 * Exit: never
 */
public class JustBuyOnceStrategy extends AbstractStrategy {

    private boolean operated = false;

    @Override
    public boolean shouldEnter(int index) {
        if (!operated) {
            operated = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldExit(int index) {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
