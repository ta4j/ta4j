/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;

/**
 * Semantic contract for indicators whose values are returns.
 * <p>
 * Implementations promise that their numeric output is a return stream in the
 * declared {@link ReturnRepresentation}. Consumers can use this contract when
 * log, decimal, percentage, or multiplicative return semantics are not
 * interchangeable.
 *
 * @since 0.22.9
 */
public interface ReturnIndicator extends Indicator<Num> {

    /**
     * Returns the representation used by this return stream.
     *
     * @return return representation
     * @since 0.22.9
     */
    ReturnRepresentation getReturnRepresentation();
}
