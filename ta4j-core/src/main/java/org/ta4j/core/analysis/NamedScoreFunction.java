/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.function.Function;

/**
 * Shared contract for named scoring functions.
 *
 * <p>
 * Implementations expose a stable display name and compute a score-like output
 * for an input payload.
 * </p>
 *
 * @param <I> input payload type
 * @param <S> score/output type
 * @since 0.22.4
 */
public interface NamedScoreFunction<I, S> extends Function<I, S> {

    /**
     * @return human-readable function name
     * @since 0.22.4
     */
    String name();

    /**
     * Computes output for the provided input payload.
     *
     * @param input input payload
     * @return computed output
     * @since 0.22.4
     */
    S score(I input);

    /**
     * Function-style alias for {@link #score(Object)}.
     *
     * @param input input payload
     * @return computed output
     * @since 0.22.4
     */
    @Override
    default S apply(I input) {
        return score(input);
    }
}
