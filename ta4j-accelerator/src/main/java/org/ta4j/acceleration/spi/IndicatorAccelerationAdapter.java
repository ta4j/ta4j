/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.spi;

import org.ta4j.core.Indicator;

/**
 * Explicit opt-in adapter for one indicator graph family.
 *
 * <p>
 * Implementations must fail closed. A graph is supported only when concrete
 * indicator types, parameters, numeric representation, snapshot rules, and
 * provider operation semantics are all understood.
 *
 * @param <T> indicator value type
 * @since 0.23.1
 */
public interface IndicatorAccelerationAdapter<T> {

    /**
     * @return stable operation identifier
     * @since 0.23.1
     */
    String operationId();

    /**
     * Checks whether this adapter owns {@code indicator}.
     *
     * @param indicator candidate indicator
     * @return match result
     * @since 0.23.1
     */
    AdapterMatch<T> match(Indicator<?> indicator);
}
