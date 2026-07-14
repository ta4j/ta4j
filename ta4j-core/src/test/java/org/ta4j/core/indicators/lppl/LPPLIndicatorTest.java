/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.IndicatorSerialization;

class LPPLIndicatorTest {

    @Test
    void returnsNormalizedOneStepResidual() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(-0.03, 0.08);
        LPPLIndicator indicator = new LPPLIndicator(series, LPPLTestFixtures.compactProfile());

        Num value = indicator.getValue(LPPLTestFixtures.WINDOW);
        LPPLFit fit = new LPPLFitIndicator(series, LPPLTestFixtures.compactProfile()).getValue(LPPLTestFixtures.WINDOW);

        assertThat(value.isNaN()).isFalse();
        assertThat(value.doubleValue()).isBetween(0.0, 1.0);
        assertThat(fit.residual()).isPositive();
    }

    @Test
    void returnsNaNForWarmupAndUnqualifiedFits() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(-0.03, 0.08);
        LPPLCalibrationProfile impossible = LPPLTestFixtures.compactProfile().withOptimizerSettings(1, 0.99);
        LPPLIndicator indicator = new LPPLIndicator(series, impossible);

        assertThat(indicator.getValue(LPPLTestFixtures.WINDOW - 1).isNaN()).isTrue();
        assertThat(indicator.getValue(LPPLTestFixtures.WINDOW).isNaN()).isTrue();
    }

    @Test
    void descriptorAndJsonRoundTripsPreserveCustomPriceSourceAndOutput() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(-0.03, -0.08);
        LPPLIndicator original = new LPPLIndicator(new ClosePriceIndicator(series), LPPLTestFixtures.compactProfile());
        ComponentDescriptor descriptor = original.toDescriptor();

        Indicator<?> fromDescriptor = IndicatorSerialization.fromDescriptor(series, descriptor);
        Indicator<?> fromJson = Indicator.fromJson(series, original.toJson());

        assertThat(ComponentSerialization.toJson(fromDescriptor.toDescriptor()))
                .isEqualTo(ComponentSerialization.toJson(descriptor));
        assertThat(ComponentSerialization.toJson(fromJson.toDescriptor()))
                .isEqualTo(ComponentSerialization.toJson(descriptor));
        assertThat(fromDescriptor.getValue(LPPLTestFixtures.WINDOW))
                .isEqualTo(original.getValue(LPPLTestFixtures.WINDOW));
        assertThat(fromJson.getValue(LPPLTestFixtures.WINDOW)).isEqualTo(original.getValue(LPPLTestFixtures.WINDOW));
    }
}
