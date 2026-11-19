/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class IsFallingRuleTest {

    private IsFallingRule rule;
    private BarSeries series;

    @BeforeEach
    public void setUp() {
        series = new BaseBarSeriesBuilder().build();
        Indicator<Num> indicator = new FixedNumIndicator(series, 6, 5, 4, 3, 2, 1, 0, -1, 2, 3);
        rule = new IsFallingRule(indicator, 3);
    }

    @Test
    public void isSatisfied() {
        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertTrue(rule.isSatisfied(3));
        assertTrue(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertFalse(rule.isSatisfied(8));
        assertFalse(rule.isSatisfied(9));
    }

    @Test
    public void doesNotAnchorLookbackBeforeSeriesBeginIndex() {
        BarSeries trimmedSeries = new MockBarSeriesBuilder().withData(0, 0, 0, 0, 6, 5, 4, 3, 2, 1).build();
        trimmedSeries.setMaximumBarCount(5);
        var indicator = new FixedNumIndicator(trimmedSeries, 0, 0, 0, 0, 6, 5, 4, 3, 2, 1);

        assertFalse(new IsFallingRule(indicator, 5).isSatisfied(9));
        assertTrue(new IsFallingRule(indicator, 3).isSatisfied(9));
    }

    @Test
    public void countsStrictFallExactlyWithLowPrecision() {
        for (RoundingMode rounding : List.of(RoundingMode.HALF_UP, RoundingMode.UNNECESSARY)) {
            NumFactory factory = DecimalNumFactory.getInstance(new MathContext(1, rounding));
            BarSeries prices = new MockBarSeriesBuilder().withNumFactory(factory)
                    .withData(0, -1, -2, -3, -4, -5, -6, -7, -8, -9, -20, -30, -40, -40, -50, -60, -70, -80, -90, -200,
                            -300, -400, -500, -600, -700, -800)
                    .build();
            ClosePriceIndicator close = new ClosePriceIndicator(prices);
            IsFallingRule strict = new IsFallingRule(close, 12);
            IsFallingRule elevenOfTwelve = new IsFallingRule(close, 12, 11.0 / 12);

            assertFalse(strict.isSatisfied(11));
            assertTrue(strict.isSatisfied(12));
            assertFalse(strict.isSatisfied(13));
            assertTrue(elevenOfTwelve.isSatisfied(13));
            assertFalse(strict.isSatisfied(24));
            assertTrue(strict.isSatisfied(25));
        }
    }

    @Test
    public void serializeAndDeserialize() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
