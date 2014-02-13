package eu.verdelhan.ta4j.indicators;

import eu.verdelhan.ta4j.Indicator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cache the constructor of the indicator. Avoid to calculate the same index of the indicator twice.
 */
public abstract class CachedIndicator<T> implements Indicator<T> {

    private List<T> results = new ArrayList<T>();

    @Override
    public T getValue(int index) {
        increaseLength(index);
        if (results.get(index) == null) {
            int i = index;
            while ((i > 0) && (results.get(i--) == null)) {
                ;
            }
            for (; i <= index; i++) {
                if (results.get(i) == null) {
                    results.set(i, calculate(i));
                }
            }
        }
        return results.get(index);
    }

    protected abstract T calculate(int index);

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private void increaseLength(int index) {
        if (results.size() <= index) {
            results.addAll(Collections.<T> nCopies((index - results.size()) + 1, null));
        }
    }
}
