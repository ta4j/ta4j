/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class EdgeHealthyRuleTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(10).highPrice(10).lowPrice(10).closePrice(10).volume(1).add();
    }

    @Test
    public void shouldRequireEdgeToMeetTheMinimumThreshold() {
        EdgeHealthyRule subject = new EdgeHealthyRule(new ConstantIndicator<>(series, series.numFactory().numOf(15)),
                10);

        assertTrue(subject.isSatisfied(0, null));
    }

    @Test
    public void shouldRejectWhenSlopeFallsBelowTheMinimum() {
        ConstantIndicator<Num> edge = new ConstantIndicator<>(series, series.numFactory().numOf(15));
        ConstantIndicator<Num> minimum = new ConstantIndicator<>(series, series.numFactory().numOf(10));
        ConstantIndicator<Num> slope = new ConstantIndicator<>(series, series.numFactory().numOf(-2));
        ConstantIndicator<Num> minimumSlope = new ConstantIndicator<>(series, series.numFactory().zero());
        EdgeHealthyRule subject = new EdgeHealthyRule(edge, minimum, slope, minimumSlope);

        assertFalse(subject.isSatisfied(0, null));
    }

    @Test
    public void shouldUseZeroSlopeThresholdWhenMinimumSlopeIsOmitted() {
        ConstantIndicator<Num> edge = new ConstantIndicator<>(series, series.numFactory().numOf(15));
        ConstantIndicator<Num> minimum = new ConstantIndicator<>(series, series.numFactory().numOf(10));
        ConstantIndicator<Num> improvingSlope = new ConstantIndicator<>(series, series.numFactory().one());
        EdgeHealthyRule subject = new EdgeHealthyRule(edge, minimum, improvingSlope, null);

        assertTrue(subject.isSatisfied(0, null));
    }
}
