/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.rules.BooleanRule;

class StrategySnapshotsTest {

    @Test
    void customStrategyKeepsItsOverriddenBehavior() {
        Strategy custom = new BaseStrategy("custom", BooleanRule.FALSE, BooleanRule.FALSE) {
            @Override
            public boolean shouldEnter(int index, TradingRecord tradingRecord) {
                return true;
            }
        };

        Strategy snapshot = StrategySnapshots.copy(custom);

        assertThat(snapshot.shouldEnter(0, null)).isTrue();
        assertThat(snapshot).isSameAs(custom);
    }

    @Test
    void plainBaseStrategyIsCopied() {
        Strategy plain = new BaseStrategy("plain", BooleanRule.TRUE, BooleanRule.FALSE, 3);

        Strategy snapshot = StrategySnapshots.copy(plain);

        assertThat(snapshot).isNotSameAs(plain);
        assertThat(snapshot.getName()).isEqualTo("plain");
        assertThat(snapshot.getUnstableBars()).isEqualTo(3);
        assertThat(snapshot.shouldEnter(5, null)).isTrue();
    }
}
