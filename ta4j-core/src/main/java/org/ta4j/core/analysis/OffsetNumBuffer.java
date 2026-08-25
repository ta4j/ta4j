/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * Compact, offset-aware storage for {@link Num} values addressed by absolute
 * series indexes.
 *
 * <p>
 * Analysis results are indexed by absolute series index, but the retained
 * window of a {@link BarSeries} may start far from zero (a large
 * {@code beginIndex} after leading-bar removal). Allocating one slot per
 * absolute index ({@code endIndex + 1}) overflows {@code int} arithmetic near
 * {@link Integer#MAX_VALUE} and wastes memory for offset windows. This buffer
 * stores exactly one slot per logical bar of the window
 * {@code [startIndex, endIndex]} and maps absolute indexes to positions with
 * {@code index - startIndex}.
 *
 * <p>
 * Reads outside the window return the configured {@code neutral} value instead
 * of throwing, so absolute-indexed lookups on offset series never fail.
 * Mutating operations outside the window are ignored, and index ranges are
 * clamped to the window.
 *
 * @since 0.24.2
 */
final class OffsetNumBuffer {

    private final int startIndex;
    private final int endIndex;
    private final List<Num> values;
    private final Num neutral;

    /**
     * Constructor.
     *
     * @param startIndex   the first absolute index of the window
     * @param endIndex     the last absolute index of the window
     * @param initialValue the value pre-filling the window
     * @param neutral      the value returned for reads outside the window
     */
    OffsetNumBuffer(int startIndex, int endIndex, Num initialValue, Num neutral) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.neutral = Objects.requireNonNull(neutral);
        this.values = new ArrayList<>(Collections.nCopies(sizeOf(startIndex, endIndex), initialValue));
    }

    /**
     * Creates a buffer covering the full logical window of the given series.
     *
     * @param series       the bar series
     * @param initialValue the value pre-filling the window
     * @param neutral      the value returned for reads outside the window
     * @return a buffer covering {@code [series.getBeginIndex(), series.getEndIndex()]}
     */
    static OffsetNumBuffer of(BarSeries series, Num initialValue, Num neutral) {
        return new OffsetNumBuffer(series.getBeginIndex(), series.getEndIndex(), initialValue, neutral);
    }

    private static int sizeOf(int startIndex, int endIndex) {
        if (startIndex < 0 || endIndex < startIndex) {
            return 0;
        }
        long span = (long) endIndex - startIndex + 1;
        return span > Integer.MAX_VALUE ? 0 : (int) span;
    }

    /**
     * Returns the number of logical bars covered by the window.
     *
     * @return the window size
     */
    int size() {
        return values.size();
    }

    /**
     * Checks whether the given absolute index lies within the window.
     *
     * @param index the absolute index
     * @return {@code true} when the index is within {@code [startIndex, endIndex]}
     */
    boolean contains(int index) {
        return index >= startIndex && index <= endIndex;
    }

    /**
     * Returns the value at the given absolute index, or the neutral value when
     * the index lies outside the window.
     *
     * @param index the absolute index
     * @return the stored or neutral value
     */
    Num get(int index) {
        int position = index - startIndex;
        if (position < 0 || position >= values.size()) {
            return neutral;
        }
        return values.get(position);
    }

    /**
     * Adds the given delta at the index. Indexes outside the window are
     * ignored.
     *
     * @param index the absolute index
     * @param delta the delta to add
     */
    void add(int index, Num delta) {
        int position = index - startIndex;
        if (position < 0 || position >= values.size()) {
            return;
        }
        values.set(position, values.get(position).plus(delta));
    }

    /**
     * Adds the given delta over the absolute index range, clamped to the
     * window. Ranges entirely outside the window are ignored.
     *
     * @param fromIndex the first absolute index of the range
     * @param toIndex   the last absolute index of the range
     * @param delta     the delta to add
     */
    void addRange(int fromIndex, int toIndex, Num delta) {
        int firstPosition = Math.max(fromIndex, startIndex) - startIndex;
        int lastPosition = Math.min(toIndex, endIndex) - startIndex;
        for (int position = Math.max(0, firstPosition); position <= lastPosition
                && position < values.size(); position++) {
            values.set(position, values.get(position).plus(delta));
        }
    }

    /**
     * Multiplies the value at the index by the factor. Indexes outside the
     * window are ignored.
     *
     * @param index  the absolute index
     * @param factor the factor to multiply by
     */
    void multiply(int index, Num factor) {
        int position = index - startIndex;
        if (position < 0 || position >= values.size()) {
            return;
        }
        values.set(position, values.get(position).multipliedBy(factor));
    }

    /**
     * Multiplies the values over the absolute index range by the factor,
     * clamped to the window. Ranges entirely outside the window are ignored.
     *
     * @param fromIndex the first absolute index of the range
     * @param toIndex   the last absolute index of the range
     * @param factor    the factor to multiply by
     */
    void multiplyRange(int fromIndex, int toIndex, Num factor) {
        int firstPosition = Math.max(fromIndex, startIndex) - startIndex;
        int lastPosition = Math.min(toIndex, endIndex) - startIndex;
        for (int position = Math.max(0, firstPosition); position <= lastPosition
                && position < values.size(); position++) {
            values.set(position, values.get(position).multipliedBy(factor));
        }
    }

    /**
     * Returns the value at the given position within the window.
     *
     * @param position the zero-based position within the window
     * @return the value at that position
     */
    Num at(int position) {
        return values.get(position);
    }
}
