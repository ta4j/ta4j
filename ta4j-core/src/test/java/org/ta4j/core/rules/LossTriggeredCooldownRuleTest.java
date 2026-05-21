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

public class LossTriggeredCooldownRuleTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        for (int i = 0; i < 8; i++) {
            double close = 100 - i;
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(1).add();
        }
    }

    @Test
    public void shouldBlockAfterALosingPositionUntilCooldownExpires() {
        TradingRecord record = new BaseTradingRecord();
        record.enter(0, series.numFactory().numOf(100), series.numFactory().one());
        record.exit(2, series.numFactory().numOf(90), series.numFactory().one());

        LossTriggeredCooldownRule subject = new LossTriggeredCooldownRule(series, 3);

        assertFalse(subject.isSatisfied(4, record));
        assertTrue(subject.isSatisfied(5, record));
    }

    @Test
    public void shouldAllowResetRuleToClearCooldownEarly() {
        TradingRecord record = new BaseTradingRecord();
        record.enter(0, series.numFactory().numOf(100), series.numFactory().one());
        record.exit(2, series.numFactory().numOf(90), series.numFactory().one());

        LossTriggeredCooldownRule subject = new LossTriggeredCooldownRule(
                new ConstantIndicator<Num>(series, series.numFactory().three()), new FixedRule(3), null);

        assertTrue(subject.isSatisfied(3, record));
    }

    @Test
    public void shouldAllowEntryWhenLaterPositionWasProfitable() {
        TradingRecord record = new BaseTradingRecord();
        record.enter(0, series.numFactory().numOf(100), series.numFactory().one());
        record.exit(2, series.numFactory().numOf(90), series.numFactory().one());
        record.enter(3, series.numFactory().numOf(90), series.numFactory().one());
        record.exit(4, series.numFactory().numOf(110), series.numFactory().one());

        LossTriggeredCooldownRule subject = new LossTriggeredCooldownRule(series, 10);

        assertTrue(subject.isSatisfied(5, record));
    }
}
