/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.macd.MACDVMomentumState;
import org.ta4j.core.indicators.macd.MACDVMomentumStateIndicator;
import org.ta4j.core.indicators.macd.VolatilityNormalizedMACDIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class MomentumStateRuleTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withData(100, 101, 102, 101, 103, 104, 103, 105, 106, 105).build();
    }

    @Test
    public void isSatisfiedWhenExpectedStateMatches() {
        FixedIndicator<MACDVMomentumState> states = new FixedIndicator<>(series, MACDVMomentumState.RANGING,
                MACDVMomentumState.HIGH_RISK, MACDVMomentumState.RANGING, MACDVMomentumState.LOW_RISK);
        MomentumStateRule rule = new MomentumStateRule(states, MACDVMomentumState.RANGING);

        assertTrue(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertTrue(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
    }

    @Test
    public void validatesConstructorArguments() {
        FixedIndicator<MACDVMomentumState> states = new FixedIndicator<>(series, MACDVMomentumState.RANGING);

        assertThrows(NullPointerException.class, () -> new MomentumStateRule(null, MACDVMomentumState.RANGING));
        assertThrows(NullPointerException.class, () -> new MomentumStateRule(states, null));
    }

    @Test
    public void serializeAndDeserialize() {
        VolatilityNormalizedMACDIndicator macdV = new VolatilityNormalizedMACDIndicator(series, 2, 5, 2);
        MACDVMomentumStateIndicator stateIndicator = new MACDVMomentumStateIndicator(macdV, 20, 40, -20, -40);
        MomentumStateRule rule = new MomentumStateRule(stateIndicator, MACDVMomentumState.RANGING);

        Rule restored = RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertEquals("Mismatch at index " + i, rule.isSatisfied(i), restored.isSatisfied(i));
        }
    }
}
