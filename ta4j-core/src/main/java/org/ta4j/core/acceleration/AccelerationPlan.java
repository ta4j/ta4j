/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.Objects;

import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;

/**
 * An {@link AcceleratableIndicator}'s answer for one requested range: a kernel
 * request plus the decoder that turns its raw output back into indicator
 * values, or the reason the range stays on the CPU.
 *
 * <p>
 * A declined range is either {@linkplain #unsupported(String) unsupported},
 * meaning the indicator stays on the CPU for the rest of the scope (for example
 * a custom Monte Carlo method or a non-{@code DoubleNum} series), or
 * {@linkplain #retryFrom(int, String) deferred} to a later index (for example a
 * warm-up prefix), so an early read cannot disable acceleration for the whole
 * scope. Plans are opaque outside the runtime, and providers never see them:
 * their boundary remains the primitive-only {@link KernelRequest}.
 *
 * @param <T> indicator value type
 * @since 0.26.1
 */
public final class AccelerationPlan<T> {

    private static final int NO_RETRY = -1;

    private final KernelRequest request;
    private final Decoder<T> decoder;
    private final int retryFromIndex;
    private final String reason;

    private AccelerationPlan(KernelRequest request, Decoder<T> decoder, int retryFromIndex, String reason) {
        this.request = request;
        this.decoder = decoder;
        this.retryFromIndex = retryFromIndex;
        this.reason = reason;
    }

    /**
     * Creates a plan that runs {@code request} on a provider and decodes each
     * index's raw outputs with {@code decoder}.
     *
     * @param request kernel request built from primitives only
     * @param decoder reconstruction of one index's value from its raw outputs
     * @param <T>     indicator value type
     * @return executable plan
     * @since 0.26.1
     */
    public static <T> AccelerationPlan<T> of(KernelRequest request, Decoder<T> decoder) {
        return new AccelerationPlan<>(Objects.requireNonNull(request, "request must not be null"),
                Objects.requireNonNull(decoder, "decoder must not be null"), NO_RETRY, null);
    }

    /**
     * Declines acceleration for the rest of the scope.
     *
     * @param reason concise operator-facing reason, reported by
     *               {@link AccelerationRuntime#lastDiagnostic()}
     * @param <T>    indicator value type
     * @return permanent decline
     * @since 0.26.1
     */
    public static <T> AccelerationPlan<T> unsupported(String reason) {
        return new AccelerationPlan<>(null, null, NO_RETRY, Objects.requireNonNull(reason, "reason must not be null"));
    }

    /**
     * Declines the requested range and defers planning until an index at or after
     * {@code index} is read.
     *
     * @param index  first decision index that may be eligible
     * @param reason concise operator-facing reason, reported by
     *               {@link AccelerationRuntime#lastDiagnostic()}
     * @param <T>    indicator value type
     * @return transient decline
     * @throws IllegalArgumentException if {@code index} is negative
     * @since 0.26.1
     */
    public static <T> AccelerationPlan<T> retryFrom(int index, String reason) {
        if (index < 0) {
            throw new IllegalArgumentException("retry index must not be negative: " + index);
        }
        return new AccelerationPlan<>(null, null, index, Objects.requireNonNull(reason, "reason must not be null"));
    }

    boolean isPlanned() {
        return request != null;
    }

    boolean isPermanentDecline() {
        return request == null && retryFromIndex == NO_RETRY;
    }

    KernelRequest request() {
        return request;
    }

    Decoder<T> decoder() {
        return decoder;
    }

    int retryFromIndex() {
        return retryFromIndex;
    }

    String reason() {
        return reason;
    }

    @Override
    public String toString() {
        if (isPlanned()) {
            return "AccelerationPlan[" + request.operation() + " over [" + request.fromInclusive() + ", "
                    + request.toInclusive() + "]]";
        }
        return isPermanentDecline() ? "AccelerationPlan[unsupported: " + reason + "]"
                : "AccelerationPlan[retry from " + retryFromIndex + ": " + reason + "]";
    }

    /**
     * Reconstructs one decision index's value from its raw kernel outputs.
     *
     * <p>
     * The runtime calls the decoder only after validating the output shape,
     * rejecting non-finite outputs and confirming the series did not change.
     *
     * @param <T> indicator value type
     * @since 0.26.1
     */
    @FunctionalInterface
    public interface Decoder<T> {

        /**
         * Decodes one decision index.
         *
         * @param slice raw outputs for the index, of length
         *              {@link KernelRequest#outputsPerIndex()}; the runtime reuses the
         *              array for the next index, so it must not be retained
         * @param index decision index the slice belongs to
         * @return decoded value, never {@code null}
         * @since 0.26.1
         */
        T decode(double[] slice, int index);
    }
}
