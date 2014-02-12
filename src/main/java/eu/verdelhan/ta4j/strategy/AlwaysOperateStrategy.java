package eu.verdelhan.ta4j.strategy;

/**
 * Strategy which always enters and exits.
 * Enter: always
 * Exit: always
 */
public class AlwaysOperateStrategy extends AbstractStrategy {

    @Override
    public boolean shouldEnter(int index) {
        return true;
    }

    @Override
    public boolean shouldExit(int index) {
        return true;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
