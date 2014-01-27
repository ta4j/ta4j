package eu.verdelhan.tailtest.strategy;

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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + (operated ? 1231 : 1237);
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
        final JustBuyOnceStrategy other = (JustBuyOnceStrategy) obj;
        if (operated != other.operated) {
            return false;
        }
        return true;
    }

}
