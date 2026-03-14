/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serializable;
import java.time.Instant;
import org.ta4j.core.num.Num;

/**
 * Deprecated live-fill compatibility contract.
 *
 * <p>
 * Use {@link TradeFill} for new code. This interface remains available in the
 * 0.22.x line so existing live adapters can migrate without a hard compile
 * break.
 * </p>
 *
 * @since 0.22.2
 */
@Deprecated(since = "0.22.4")
public interface ExecutionFill extends Serializable {

    /**
     * @return the execution timestamp (UTC)
     * @since 0.22.2
     */
    Instant time();

    /**
     * @return the execution price per asset
     * @since 0.22.2
     */
    Num price();

    /**
     * @return the execution amount
     * @since 0.22.2
     */
    Num amount();

    /**
     * @return the execution fee (nullable, zero when unknown)
     * @since 0.22.2
     */
    Num fee();

    /**
     * @return the execution side
     * @since 0.22.2
     */
    ExecutionSide side();

    /**
     * @return the exchange order id if available
     * @since 0.22.2
     */
    String orderId();

    /**
     * @return the correlation id if available
     * @since 0.22.2
     */
    String correlationId();

    /**
     * @return the associated intent id, defaulting to {@link #correlationId()}
     * @since 0.22.2
     */
    default String intentId() {
        return correlationId();
    }

    /**
     * @return the bar index for the fill, or {@code -1} when not specified
     * @since 0.22.2
     */
    default int index() {
        return -1;
    }

    /**
     * @return true when the fill has a non-zero fee
     * @since 0.22.2
     */
    default boolean hasFee() {
        Num fee = fee();
        return fee != null && !fee.isZero();
    }
}
