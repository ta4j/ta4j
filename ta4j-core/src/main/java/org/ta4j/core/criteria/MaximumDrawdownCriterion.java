/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.utils.DeprecationNotifier;

/**
 * @deprecated This class was moved to
 *             {@link org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion}.
 */
@Deprecated(since = "0.19", forRemoval = true)
public class MaximumDrawdownCriterion extends org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion {

    public MaximumDrawdownCriterion() {
        DeprecationNotifier.warnOnce(MaximumDrawdownCriterion.class,
                "org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion");
    }
}
