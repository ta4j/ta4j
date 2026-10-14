/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * An indicator whose every value is the decoded output of one {@link Kernel}
 * row, so an acceleration provider, such as a GPU, can compute its values in
 * batches inside an {@link AccelerationRuntime} scope.
 *
 * <p>
 * The indicator owns the domain side of the calculation: it
 * {@linkplain #snapshot(int, double[]) snapshots} each decision index into
 * primitive row inputs, supplies the shared {@linkplain #windowValue(int)
 * window values}, and {@linkplain #decode(int, double[]) decodes} raw kernel
 * outputs back into values. The kernel owns the arithmetic. Outside an
 * accelerated batch, {@link #calculate(int)} runs the same kernel on the CPU
 * over a one-row request, so both lanes evaluate one definition and agree bit
 * for bit.
 *
 * <p>
 * A decision index is unavailable, and {@link #unavailable(int)} supplies its
 * value, when its snapshot is declined, its window reaches before index
 * {@code 0}, or any of its window values is {@code NaN}. The runtime batches
 * only consecutive available indexes and leaves every other index to the CPU.
 *
 * <p>
 * Subclasses must read inputs only from this indicator's {@link #getBarSeries()
 * series}, the only series whose revisions the runtime watches while a batch is
 * computed.
 *
 * @param <T> indicator value type
 * @since 0.26.1
 */
public abstract class KernelIndicator<T> extends CachedIndicator<T> {

    private final Kernel kernel;

    /**
     * Creates a kernel indicator reading bars of {@code series} directly.
     *
     * @param kernel kernel computing every value
     * @param series bar series
     * @since 0.26.1
     */
    protected KernelIndicator(Kernel kernel, BarSeries series) {
        super(series);
        this.kernel = Objects.requireNonNull(kernel, "kernel must not be null");
    }

    /**
     * Creates a kernel indicator over source indicators of one series.
     *
     * @param kernel            kernel computing every value
     * @param firstSource       first source indicator
     * @param additionalSources further source indicators
     * @since 0.26.1
     */
    protected KernelIndicator(Kernel kernel, Indicator<?> firstSource, Indicator<?>... additionalSources) {
        super(firstSource, additionalSources);
        this.kernel = Objects.requireNonNull(kernel, "kernel must not be null");
    }

    /**
     * Returns the kernel computing every value of this indicator.
     *
     * @return kernel
     * @since 0.26.1
     */
    public final Kernel kernel() {
        return kernel;
    }

    /**
     * Captures the row inputs of one decision index.
     *
     * @param index     decision index
     * @param rowInputs destination of length {@link Kernel#inputsPerRow()}
     * @return {@code false} when the index is unavailable
     * @since 0.26.1
     */
    protected abstract boolean snapshot(int index, double[] rowInputs);

    /**
     * Returns the window value of one bar.
     *
     * @param barIndex bar index, never negative
     * @return window value, {@code NaN} when unusable
     * @since 0.26.1
     */
    protected abstract double windowValue(int barIndex);

    /**
     * Reconstructs the value of one decision index from its raw kernel outputs. The
     * outputs array may be reused after this call returns.
     *
     * @param index   decision index
     * @param outputs raw outputs of length {@link Kernel#outputsPerRow()}
     * @return decoded value, never {@code null}
     * @since 0.26.1
     */
    protected abstract T decode(int index, double[] outputs);

    /**
     * Returns the value of an unavailable decision index.
     *
     * @param index decision index
     * @return value reported without running the kernel
     * @since 0.26.1
     */
    protected abstract T unavailable(int index);

    /**
     * Returns the accelerated value of the current scope's batch, or runs the
     * kernel on the CPU for this index.
     *
     * @since 0.26.1
     */
    @Override
    protected final T calculate(int index) {
        T accelerated = AccelerationRuntime.batchValue(this, index);
        if (accelerated != null) {
            return accelerated;
        }
        int windowLength = kernel.windowLength();
        double[] rowInputs = new double[kernel.inputsPerRow()];
        if ((long) index - windowLength + 1L < 0L || !snapshot(index, rowInputs)) {
            return unavailable(index);
        }
        double[] window = new double[windowLength];
        for (int offset = 0; offset < windowLength; offset++) {
            double value = windowValue(index - windowLength + 1 + offset);
            if (Double.isNaN(value)) {
                return unavailable(index);
            }
            window[offset] = value;
        }
        double[][] rows = new double[rowInputs.length][];
        for (int buffer = 0; buffer < rowInputs.length; buffer++) {
            rows[buffer] = new double[] { rowInputs[buffer] };
        }
        KernelRequest request = AccelerationRuntime.request(kernel, index, 1, rows, window);
        double[] outputs = new double[kernel.outputsPerRow()];
        kernel.run(request, 0, outputs);
        return decode(index, outputs);
    }
}
