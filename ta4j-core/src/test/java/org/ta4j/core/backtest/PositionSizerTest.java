/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

class PositionSizerTest {

    static Stream<NumFactory> factories() {
        return Stream.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void leverageScalesBalanceAndCustomSizing(NumFactory factory) {
        PositionSizer.Context context = context(factory);
        assertNumEquals(100, PositionSizer.balance(10_000).amount(context));
        assertNumEquals(300, PositionSizer.balance(10_000).leveraged(3).amount(context));
        assertNumEquals(75, PositionSizer
                .balance(10_000, (entry, balance) -> balance.dividedBy(entry.entryPrice()).dividedBy(factory.numOf(4)))
                .leveraged(3)
                .amount(context));
        assertNumEquals(30, PositionSizer.fixed(10).leveraged(3).amount(context));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void roundingFloorsTheFinalQuantityAndAllowsZero(NumFactory factory) {
        PositionSizer.Context context = context(factory);
        assertNumEquals(33.333, PositionSizer.fixed(33.3337).roundedDownTo(0.001).amount(context));
        assertNumEquals(0, PositionSizer.fixed(0.0004).roundedDownTo(0.001).amount(context));
        assertNumEquals(100, PositionSizer.fixed(33.3337).leveraged(3).roundedDownTo(1).amount(context));
        assertNumEquals(2, PositionSizer.fixed(2).roundedDownTo(1).amount(context));
    }

    @Test
    void decoratorsRejectInvalidConfigurationEagerly() {
        PositionSizer sizer = PositionSizer.fixed();
        assertThrows(NullPointerException.class, () -> sizer.leveraged(null));
        assertThrows(NullPointerException.class, () -> sizer.roundedDownTo(null));
        for (Number invalid : new Number[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> sizer.leveraged(invalid));
            assertThrows(IllegalArgumentException.class, () -> sizer.roundedDownTo(invalid));
        }
    }

    private static PositionSizer.Context context(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100).build();
        ZeroCostModel zeroCost = new ZeroCostModel();
        return new PositionSizer.Context(0, 0, factory.numOf(100), new BaseStrategy(new FixedRule(0), new FixedRule()),
                series, TradeType.BUY, new BaseTradingRecord(), zeroCost, zeroCost);
    }
}
