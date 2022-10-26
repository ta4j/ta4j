package org.ta4j.core.cache;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

public interface Cache<T> {

    T getValue(Bar bar);

    void put(Bar bar, T value);
}
