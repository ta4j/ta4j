/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;

/**
 * The primitive calculation behind a {@link KernelIndicator}: the one
 * definition of its values, run on the CPU by the indicator itself and offered
 * to acceleration providers as a {@link KernelRequest} of the same shape.
 *
 * <p>
 * A request batches the consecutive decision indexes
 * {@code request.fromInclusive() .. request.toInclusive()}. Its inputs are
 * {@link #inputsPerRow()} row buffers holding one value per decision row,
 * followed, when {@link #windowLength()} is positive, by one shared window
 * buffer of length {@code request.size() + windowLength() - 1}: decision row
 * {@code r} reads the window {@code window[r .. r + windowLength() - 1]}, whose
 * last element belongs to the decision index itself.
 *
 * <p>
 * {@link #run(KernelRequest, int, double[])} reads nothing but the request, so
 * running it over a provider's request reproduces exactly what the CPU lane
 * computes. A provider's raw output is accepted only when every value is
 * finite; a kernel that reports a non-finite value for a row is still evaluated
 * on the CPU, where the indicator decodes it.
 *
 * @since 0.26.1
 */
public interface Kernel {

    /**
     * Returns the versioned operation providers implement.
     *
     * @return operation
     * @since 0.26.1
     */
    Operation operation();

    /**
     * Returns the base seed copied into every request.
     *
     * @return base seed, {@code 0} for deterministic kernels without randomness
     * @since 0.26.1
     */
    long seed();

    /**
     * Returns the operation parameters copied into every request.
     *
     * @return a new array of parameters defined by the operation contract
     * @since 0.26.1
     */
    double[] params();

    /**
     * Returns the number of row buffers, each holding one value per decision row.
     *
     * @return non-negative row buffer count
     * @since 0.26.1
     */
    int inputsPerRow();

    /**
     * Returns the number of consecutive window values each decision row reads,
     * ending at the decision index.
     *
     * @return non-negative window length; {@code 0} when the request carries no
     *         window buffer
     * @since 0.26.1
     */
    int windowLength();

    /**
     * Returns the number of raw output values per decision row.
     *
     * @return positive output count
     * @since 0.26.1
     */
    int outputsPerRow();

    /**
     * Returns the device bytes one decision row needs, including its inputs,
     * outputs and workspace; the shared window prefix of {@code windowLength() - 1}
     * values is accounted for separately.
     *
     * @return non-negative byte count, {@link Long#MAX_VALUE} when it overflows
     * @since 0.26.1
     */
    long deviceBytesPerRow();

    /**
     * Returns an order-of-magnitude estimate of the CPU time one decision row
     * takes, the baseline accelerators must beat.
     *
     * @return estimated nanoseconds, non-positive when unknown
     * @since 0.26.1
     */
    long scalarNanosPerRow();

    /**
     * Computes the raw outputs of one decision row.
     *
     * @param request request whose seed, parameters and inputs are the only data
     *                read
     * @param row     decision row, {@code 0 <= row < request.size()}
     * @param outputs destination of length {@link #outputsPerRow()}
     * @since 0.26.1
     */
    void run(KernelRequest request, int row, double[] outputs);
}
