/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import org.ta4j.core.num.Num;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.IndicatorSerialization;
import org.ta4j.core.serialization.IndicatorSerializationException;

import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Indicator over a {@link BarSeries bar series}.
 *
 * <p>
 * Returns a value of type <b>T</b> for each index of the bar series.
 *
 * @param <T> the type of the returned value (Double, Boolean, etc.)
 */
public interface Indicator<T> {

    /**
     * @param index the bar index
     * @return the value of the indicator
     */
    T getValue(int index);

    /**
     * Returns {@code true} once {@code this} indicator has enough bars to
     * accurately calculate its value. Otherwise, {@code false} will be returned,
     * which means the indicator will give incorrect values due to insufficient
     * data. This method determines stability using the formula:
     *
     * <pre>
     * isStable = {@link BarSeries#getBarCount()} >= {@link #getCountOfUnstableBars()}
     * </pre>
     *
     * @return true if the calculated indicator value is correct
     */
    default boolean isStable() {
        return getBarSeries().getBarCount() >= getCountOfUnstableBars();
    }

    /**
     * Returns the number of bars up to which {@code this} Indicator calculates
     * wrong values.
     *
     * @return unstable bars
     */
    int getCountOfUnstableBars();

    /**
     * @return the related bar series
     */
    BarSeries getBarSeries();

    /**
     * @return all values from {@code this} Indicator over {@link #getBarSeries()}
     *         as a Stream
     */
    default Stream<T> stream() {
        return IntStream.range(getBarSeries().getBeginIndex(), getBarSeries().getEndIndex() + 1)
                .mapToObj(this::getValue);
    }

    /**
     * Returns all values of an {@link Indicator} within the given {@code index} and
     * {@code barCount} as an array of Doubles. The returned doubles could have a
     * minor loss of precision, if {@link Indicator} was based on {@link Num Num}.
     *
     * @param ref      the indicator
     * @param index    the index
     * @param barCount the barCount
     * @return array of Doubles within {@code index} and {@code barCount}
     */
    static Double[] toDouble(Indicator<Num> ref, int index, int barCount) {
        int startIndex = Math.max(0, index - barCount + 1);
        return IntStream.range(startIndex, startIndex + barCount)
                .mapToObj(ref::getValue)
                .map(Num::doubleValue)
                .toArray(Double[]::new);
    }

    /**
     * Serializes {@code this} indicator into a JSON payload that captures its type,
     * numeric parameters, and child indicators.
     *
     * <p>
     * The serialization process uses reflection to introspect the indicator's
     * structure, extracting numeric constructor parameters and recursively
     * serializing child indicators. The resulting JSON can be used to reconstruct
     * an equivalent indicator instance using {@link #fromJson(BarSeries, String)}.
     *
     * <p>
     * The JSON format includes:
     * <ul>
     * <li>The indicator's simple class name (type)</li>
     * <li>Numeric parameters extracted from constructor arguments</li>
     * <li>Child indicators as nested component descriptors</li>
     * </ul>
     *
     * @return JSON description of the indicator
     * @throws IndicatorSerializationException if serialization fails due to
     *                                         reflection errors, class loading
     *                                         issues, or JSON generation problems.
     *                                         This exception wraps all underlying
     *                                         serialization failures, providing a
     *                                         consistent exception type for error
     *                                         handling.
     * @since 0.19
     */
    default String toJson() {
        return IndicatorSerialization.toJson(this);
    }

    /**
     * Converts {@code this} indicator into a structured descriptor that can be
     * embedded inside other component metadata.
     *
     * <p>
     * This method creates a {@link ComponentDescriptor} that represents the
     * indicator's structure, including its type, numeric parameters, and child
     * indicators. The descriptor can be used for serialization, comparison, or
     * embedding within larger component hierarchies (such as rules or strategies).
     *
     * <p>
     * The descriptor extraction process:
     * <ul>
     * <li>Uses reflection to introspect the indicator's fields</li>
     * <li>Extracts numeric constructor parameters</li>
     * <li>Recursively processes child indicators, handling circular references</li>
     * <li>Builds a tree structure representing the indicator's composition</li>
     * </ul>
     *
     * @return component descriptor for the indicator
     * @throws IndicatorSerializationException if descriptor creation fails due to
     *                                         reflection errors, class loading
     *                                         issues, or problems processing
     *                                         circular references. This exception
     *                                         wraps all underlying failures,
     *                                         providing a consistent exception type
     *                                         for error handling.
     * @since 0.19
     */
    default ComponentDescriptor toDescriptor() {
        return IndicatorSerialization.describe(this);
    }

    /**
     * Reconstructs an indicator instance from its serialized representation.
     *
     * <p>
     * This method parses a JSON payload (typically generated by {@link #toJson()})
     * and reconstructs an equivalent indicator instance. The deserialization
     * process:
     * <ul>
     * <li>Parses the JSON into a component descriptor structure</li>
     * <li>Resolves indicator types by simple name from the classpath</li>
     * <li>Matches constructor parameters to descriptor values</li>
     * <li>Recursively instantiates child indicators</li>
     * <li>Constructs the indicator using reflection-based constructor matching</li>
     * </ul>
     *
     * <p>
     * <strong>Important:</strong> The indicator type must be resolvable from the
     * classpath. Custom indicator classes must be in the
     * {@code org.ta4j.core.indicators} package or registered appropriately for
     * successful deserialization.
     *
     * @param series backing series to attach to the reconstructed indicator
     * @param json   serialized indicator payload generated by {@link #toJson()}
     * @return indicator instance
     * @throws IndicatorSerializationException if deserialization fails due to:
     *                                         <ul>
     *                                         <li>Invalid or malformed JSON
     *                                         syntax</li>
     *                                         <li>Unknown indicator type (class not
     *                                         found or not in expected
     *                                         package)</li>
     *                                         <li>Missing or incompatible
     *                                         constructor parameters</li>
     *                                         <li>Constructor instantiation
     *                                         failures</li>
     *                                         <li>Class loading or reflection
     *                                         errors</li>
     *                                         </ul>
     *                                         This exception wraps all underlying
     *                                         deserialization failures, providing a
     *                                         consistent exception type for error
     *                                         handling. The original cause is
     *                                         preserved and can be accessed via
     *                                         {@link Throwable#getCause()}.
     * @since 0.19
     */
    static Indicator<?> fromJson(BarSeries series, String json) {
        return IndicatorSerialization.fromJson(series, json);
    }
}
