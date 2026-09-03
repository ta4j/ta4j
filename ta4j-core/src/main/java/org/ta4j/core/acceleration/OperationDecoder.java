/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import org.ta4j.core.num.NumFactory;

/**
 * Core-owned reconstruction of domain values from raw kernel output.
 *
 * <p>
 * Decoders run inside the acceleration scope after the runtime validates raw
 * shapes and series snapshots. They map primitives through the owning
 * {@link NumFactory} and must return {@code null} only via an exception: a
 * {@code null} return is treated as malformed provider output.
 *
 * @since 0.24.2
 */
@FunctionalInterface
public interface OperationDecoder {

    /**
     * Decodes one decision index slice into its domain value.
     *
     * @param slice   raw outputs for the index, length {@code outputsPerIndex}
     * @param index   decision index the slice belongs to
     * @param factory owning factory for numeric reconstruction
     * @return decoded domain value, never {@code null}
     * @since 0.24.2
     */
    Object decode(double[] slice, int index, NumFactory factory);
}
