package org.ta4j.core.indicators.caching;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Function;

public interface IndicatorValueCache<T> {

    T get(ZonedDateTime endTime, Function<ZonedDateTime, T> mappingFunction);

    void put(ZonedDateTime endTime, T result);

    Map<ZonedDateTime, T> getValues();

    void clear();
}
