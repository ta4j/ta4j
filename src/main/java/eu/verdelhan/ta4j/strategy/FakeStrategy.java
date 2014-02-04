package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Operation;
import java.util.Arrays;

public class FakeStrategy extends AbstractStrategy {

    private Operation[] enter;

    private Operation[] exit;

    public FakeStrategy(Operation[] enter, Operation[] exit) {
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
        return "Fake Strategy:0";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + Arrays.hashCode(enter);
        result = (prime * result) + Arrays.hashCode(exit);
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
        final FakeStrategy other = (FakeStrategy) obj;
        if (!Arrays.equals(enter, other.enter)) {
            return false;
        }
        if (!Arrays.equals(exit, other.exit)) {
            return false;
        }
        return true;
    }
}
