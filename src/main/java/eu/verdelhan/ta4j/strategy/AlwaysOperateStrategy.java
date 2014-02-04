package eu.verdelhan.ta4j.strategy;

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
