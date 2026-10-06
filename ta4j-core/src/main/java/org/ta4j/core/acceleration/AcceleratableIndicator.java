/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import org.ta4j.core.Indicator;
import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.NumericEncoding;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;

/**
 * An indicator whose values an acceleration provider, such as a GPU, can
 * compute in batches inside an {@link AccelerationRuntime} scope.
 *
 * <p>
 * Implementing this interface makes an indicator a candidate, not a guarantee:
 * {@link #planAcceleration(int, int, long)} decides for this instance, its
 * series and the requested range whether the work can be lowered. Indicators
 * that do not implement it never reach provider code. An implementation asks
 * {@link AccelerationRuntime#value(AcceleratableIndicator, int)} for the value
 * at the start of its calculation and computes it on the CPU when the answer is
 * empty.
 *
 * <h2>Contract</h2>
 * <p>
 * Return a plan only when every condition below holds; otherwise decline with a
 * reason, which {@link AccelerationRuntime#lastDiagnostic()} reports.
 * <ul>
 * <li><b>Primitive snapshot.</b> Every input the kernel reads is captured as
 * primitive arrays in a {@link KernelRequest}; providers never receive
 * indicators, series or {@code Num} values. All inputs come from this
 * indicator's {@link #getBarSeries() series}, the only series whose revisions
 * the runtime watches for mid-run changes.</li>
 * <li><b>Versioned kernel contract.</b> The request names an {@link Operation}
 * that providers implement; changing kernel semantics requires a new
 * constant.</li>
 * <li><b>Independent, batchable work.</b> Given the snapshot, each index (and
 * each unit of work inside it, such as a Monte Carlo path) is computed without
 * reading another index's output, so ranges can be batched and chunked.</li>
 * <li><b>Exact reproducibility.</b> A {@link NumericEncoding#FLOAT64} kernel on
 * {@code DoubleNum} whose output is {@link Determinism#BITWISE_IDENTICAL} to
 * this indicator's own CPU calculation, independent of thread or device
 * scheduling; randomness comes from counter-based per-unit streams, never a
 * shared sequential generator.</li>
 * <li><b>Bounded cost.</b> Per-index work heavy enough that a device can beat
 * the CPU after transfer costs, stated as
 * {@link KernelRequest#estimatedScalarNanos()}, with device memory that is
 * estimable per row so the request fits the given memory limit.</li>
 * </ul>
 * Path-dependent recursions (EMA-style state carried across indexes),
 * {@code DecimalNum} arithmetic and user-supplied callbacks cannot meet this
 * contract and stay on the CPU.
 *
 * <p>
 * Conditions fixed when the indicator is built, such as its numeric type or a
 * user-supplied component, are declined with
 * {@link AccelerationPlan#unsupported(String)}. Conditions that depend on the
 * range, such as a warm-up prefix, history the series no longer retains or a
 * non-finite input, are declined with
 * {@link AccelerationPlan#retryFrom(int, String)}, or the plan is shortened to
 * the eligible prefix.
 *
 * @param <T> indicator value type
 * @since 0.26.1
 */
public interface AcceleratableIndicator<T> extends Indicator<T> {

    /**
     * Plans accelerated evaluation of {@code [fromInclusive, toInclusive]}.
     *
     * <p>
     * Called by the runtime inside a scope. A plan's request may cover a shorter
     * contiguous range that starts at or after {@code fromInclusive} and ends at or
     * before {@code toInclusive}; the runtime plans the next range when a later
     * index is read.
     *
     * @param fromInclusive    first requested decision index
     * @param toInclusive      last requested decision index
     * @param memoryLimitBytes device memory ceiling for the request's
     *                         {@link KernelRequest#peakDeviceBytesEstimate()}
     * @return executable plan or decline, never {@code null}
     * @since 0.26.1
     */
    AccelerationPlan<T> planAcceleration(int fromInclusive, int toInclusive, long memoryLimitBytes);
}
