/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.IndicatorSerialization;

class LPPLExhaustionScoreIndicatorTest {

    @Test
    void providesOneStepNumericIndicatorPath() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(0.03, 10);
        LPPLExhaustionScoreIndicator indicator = new LPPLExhaustionScoreIndicator(series,
                LPPLTestFixtures.compactProfile());

        Num score = indicator.getValue(series.getEndIndex());

        assertThat(score.doubleValue()).isBetween(0.0, 1.0);
    }

    @Test
    void returnsNaNDuringWrappedIndicatorWarmup() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(0.03);
        LPPLExhaustionScoreIndicator indicator = new LPPLExhaustionScoreIndicator(series,
                LPPLTestFixtures.compactProfile());
        int unstableBars = indicator.getCountOfUnstableBars();

        assertThat(indicator.getValue(unstableBars - 1).isNaN()).isTrue();
        assertThat(indicator.getValue(unstableBars).isNaN()).isFalse();
    }

    @Test
    void descriptorAndJsonRoundTripsPreserveOutput() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(0.03);
        LPPLExhaustionScoreIndicator original = new LPPLExhaustionScoreIndicator(series,
                LPPLTestFixtures.compactProfile());
        ComponentDescriptor descriptor = original.toDescriptor();

        Indicator<?> fromDescriptor = IndicatorSerialization.fromDescriptor(series, descriptor);
        Indicator<?> fromJson = Indicator.fromJson(series, original.toJson());

        assertThat(ComponentSerialization.toJson(fromDescriptor.toDescriptor()))
                .isEqualTo(ComponentSerialization.toJson(descriptor));
        assertThat(ComponentSerialization.toJson(fromJson.toDescriptor()))
                .isEqualTo(ComponentSerialization.toJson(descriptor));
        assertThat(fromDescriptor.getValue(series.getEndIndex())).isEqualTo(original.getValue(series.getEndIndex()));
        assertThat(fromJson.getValue(series.getEndIndex())).isEqualTo(original.getValue(series.getEndIndex()));
        assertThat(original.getExhaustionIndicator()).isNotSameAs(original.getExhaustionIndicator());
    }
}
