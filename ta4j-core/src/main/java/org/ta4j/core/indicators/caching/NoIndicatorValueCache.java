package org.ta4j.core.indicators.caching;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public class NoIndicatorValueCache<T> implements IndicatorValueCache<T> {

    @Override
    public T get(ZonedDateTime endTime, Function<ZonedDateTime, T> mappingFunction) {
        return mappingFunction.apply(endTime);
    }

    @Override
    public void put(ZonedDateTime endTime, T result) {
        // do nothing
    }

    @Override
    public Map<ZonedDateTime, T> getValues() {
        return Collections.emptyMap();
    }

    @Override
    public void clear() {
        // do nothing
    }
}
