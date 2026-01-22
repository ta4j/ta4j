/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

/**
 * Unchecked exception thrown when indicator serialization or deserialization
 * fails.
 *
 * <p>
 * This exception wraps underlying errors that occur during the serialization
 * process, such as reflection failures, class loading issues, instantiation
 * problems, or JSON parsing errors. It provides a consistent exception type for
 * callers to handle serialization failures.
 *
 * @since 0.19
 */
public class IndicatorSerializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public IndicatorSerializationException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method)
     */
    public IndicatorSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
