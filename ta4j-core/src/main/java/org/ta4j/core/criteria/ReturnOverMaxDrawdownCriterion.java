/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.utils.DeprecationNotifier;

/**
 * @deprecated This class was moved to
 *             {@link org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion}
 *             and is scheduled for removal in 0.24.0.
 */
@Deprecated(since = "0.19", forRemoval = true)
public class ReturnOverMaxDrawdownCriterion extends org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion {

    public ReturnOverMaxDrawdownCriterion() {
        DeprecationNotifier.warnOnce(ReturnOverMaxDrawdownCriterion.class,
                "org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion", "0.24.0");
    }
}
