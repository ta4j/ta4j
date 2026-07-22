/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.List;
import java.util.Objects;

/**
 * Ordered immutable result from an explicit indicator batch request.
 *
 * @param values      values ordered by absolute index
 * @param diagnostics execution diagnostics
 * @param <T>         indicator value type
 * @since 0.23.1
 */
public record IndicatorBatchResult<T>(List<IndexedIndicatorValue<T>> values, AccelerationDiagnostics diagnostics) {

    public IndicatorBatchResult {
        values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    }

    /**
     * @return values without index wrappers, in request order
     * @since 0.23.1
     */
    public List<T> orderedValues() {
        return values.stream().map(IndexedIndicatorValue::value).toList();
    }
}
