/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

/**
 * Unchecked exception thrown when rule serialization or deserialization fails
 * or is not supported.
 *
 * <p>
 * This exception is thrown when attempting to serialize or deserialize a rule
 * that does not support serialization. It provides a consistent exception type
 * for callers to handle serialization failures and includes guidance on which
 * rules need serialization support to be implemented.
 *
 * <p>
 * To check if a rule supports serialization before attempting to serialize it,
 * use {@link RuleSerialization#isSerializationSupported(org.ta4j.core.Rule)}.
 *
 * @since 0.19
 */
public class RuleSerializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public RuleSerializationException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method)
     */
    public RuleSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
