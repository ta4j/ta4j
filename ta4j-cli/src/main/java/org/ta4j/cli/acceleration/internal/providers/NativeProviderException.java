/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

/**
 * Reports a native lane failure: library load, device probe, chunk evaluation,
 * or a malformed sample vector. The core maps these to provider quarantine and
 * scalar fallback; the message always names the backend first so quarantines
 * stay attributable.
 *
 * @since 0.24.2
 */
final class NativeProviderException extends RuntimeException {

    NativeProviderException(String backend, Throwable cause) {
        super(backend + " native execution failed: " + cause.getClass().getSimpleName()
                + (cause.getMessage() == null || cause.getMessage().isBlank() ? "" : ": " + cause.getMessage()), cause);
    }

    NativeProviderException(String backend, String detail) {
        super(backend + " native execution failed: " + detail);
    }

    NativeProviderException(String detail) {
        super(detail);
    }
}
