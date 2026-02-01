/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import org.ta4j.core.num.Num;

public interface ExternalCriterionTest {

    /**
     * Gets the BarSeries used by an external criterion calculator.
     *
     * @return BarSeries from the external criterion calculator
     * @throws Exception if the external calculator throws an Exception
     */
    BarSeries getSeries() throws Exception;

    /**
     * Sends criterion parameters to an external criterion calculator and returns
     * the final value of the externally calculated criterion.
     *
     * @param params criterion parameters
     * @return Num final criterion value
     * @throws Exception if the external calculator throws an Exception
     */
    Num getFinalCriterionValue(Object... params) throws Exception;

    /**
     * Gets the trading record used by an external criterion calculator.
     *
     * @return TradingRecord from the external criterion calculator
     * @throws Exception if the external calculator throws an Exception
     */
    TradingRecord getTradingRecord() throws Exception;

}
