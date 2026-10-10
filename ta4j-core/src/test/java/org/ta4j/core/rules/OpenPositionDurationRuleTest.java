/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class OpenPositionDurationRuleTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
    }

    @Test
    public void isSatisfiedAfterMinimumBars() {
        ConstantIndicator<Num> minBarsIndicator = new ConstantIndicator<>(series, series.numFactory().three());
        OpenPositionDurationRule rule = new OpenPositionDurationRule(minBarsIndicator);
        TradingRecord record = new BaseTradingRecord();
        record.enter(0);

        assertFalse(rule.isSatisfied(2, record));
        assertTrue(rule.isSatisfied(3, record));
    }

    @Test
    public void isNotSatisfiedWithoutOpenPosition() {
        ConstantIndicator<Num> minBarsIndicator = new ConstantIndicator<>(series, series.numFactory().three());
        OpenPositionDurationRule rule = new OpenPositionDurationRule(minBarsIndicator);
        TradingRecord record = new BaseTradingRecord();

        assertFalse(rule.isSatisfied(3, record));
    }

    @Test
    public void serializeAndDeserialize() {
        ConstantIndicator<Num> minBarsIndicator = new ConstantIndicator<>(series, series.numFactory().three());
        OpenPositionDurationRule rule = new OpenPositionDurationRule(minBarsIndicator);

        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
