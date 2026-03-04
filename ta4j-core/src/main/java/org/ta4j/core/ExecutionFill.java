/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serializable;
import java.time.Instant;
import org.ta4j.core.num.Num;

/**
 * Generic execution fill contract for live trading.
 *
 * <p>
 * Implementations must provide execution {@link #price()} and
 * {@link #amount()}. Metadata fields ({@link #time()}, {@link #fee()},
 * {@link #side()}, ids) are optional and default to {@code null}.
 *
 * <p>
 * The optional correlation id can be used to associate a fill with an
 * {@link ExecutionIntent} or external decision id when available.
 * </p>
 *
 * @since 0.22.2
 */
public interface ExecutionFill extends Serializable {

    /**
     * @return the execution timestamp (UTC)
     * @since 0.22.2
     */
    default Instant time() {
        return null;
    }

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
    default Num fee() {
        return null;
    }

    /**
     * @return the execution side
     * @since 0.22.2
     */
    default ExecutionSide side() {
        return null;
    }

    /**
     * @return the exchange order id if available
     * @since 0.22.2
     */
    default String orderId() {
        return null;
    }

    /**
     * @return the correlation id if available
     * @since 0.22.2
     */
    default String correlationId() {
        return null;
    }

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
