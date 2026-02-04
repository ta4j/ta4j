/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.rules.FixedRule;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategyStartingTypeTest {

    @Test
    void defaultsToLong() {
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(1));
        assertEquals(TradeType.BUY, strategy.getStartingType());
    }

    @Test
    void canOverrideStartingType() {
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(1)) {
            @Override
            public TradeType getStartingType() {
                return TradeType.SELL;
            }
        };
        assertEquals(TradeType.SELL, strategy.getStartingType());
    }
}
