/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.IndicatorSerialization;

class LPPLResidualIndicatorTest {

    private static final Logger LOG = LogManager.getLogger(LPPLResidualIndicatorTest.class);

    @Test
    void supportsAllConstructorPaths() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(-0.03, 0.08);
        ClosePriceIndicator prices = new ClosePriceIndicator(series);
        LPPLCalibrationProfile profile = LPPLTestFixtures.compactProfile();

        LPPLResidualIndicator seriesDefaults = new LPPLResidualIndicator(series);
        LPPLResidualIndicator seriesProfile = new LPPLResidualIndicator(series, profile);
        LPPLResidualIndicator indicatorDefaults = new LPPLResidualIndicator(prices);
        LPPLResidualIndicator indicatorProfile = new LPPLResidualIndicator(prices, profile);

        assertThat(seriesDefaults.getProfile()).isEqualTo(LPPLCalibrationProfile.defaults());
        assertThat(seriesProfile.getProfile()).isEqualTo(profile);
        assertThat(indicatorDefaults.getPriceIndicator()).isSameAs(prices);
        assertThat(indicatorProfile.getPriceIndicator()).isSameAs(prices);
        assertThat(indicatorProfile.getProfile()).isEqualTo(profile);
    }

    @Test
    void returnsNormalizedOneStepResidual() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(-0.03, 0.08);
        LPPLResidualIndicator indicator = new LPPLResidualIndicator(series, LPPLTestFixtures.compactProfile());

        Num value = indicator.getValue(LPPLTestFixtures.WINDOW);
        Indicator<LPPLFit> fitIndicator = indicator.getFitIndicator();
        LPPLFit fit = fitIndicator.getValue(LPPLTestFixtures.WINDOW);

        assertThat(indicator.getFitIndicator().getValue(LPPLTestFixtures.WINDOW)).isSameAs(fit);
        assertThat(value.isNaN()).isFalse();
        assertThat(value.doubleValue()).isEqualTo(fit.normalizedResidual());
        assertThat(fit.residual()).isPositive();
    }

    @Test
    void returnsNaNForWarmupAndUnqualifiedFits() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(-0.03, 0.08);
        LPPLCalibrationProfile impossible = LPPLTestFixtures.compactProfile().withOptimizerSettings(1, 0.99);
        LPPLResidualIndicator indicator = new LPPLResidualIndicator(series, impossible);

        assertThat(indicator.getValue(LPPLTestFixtures.WINDOW - 1).isNaN()).isTrue();
        assertThat(indicator.getValue(LPPLTestFixtures.WINDOW).isNaN()).isTrue();
    }

    @Test
    void descriptorAndJsonRoundTripsPreserveCustomPriceSourceAndOutput() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(-0.03, -0.08);
        LPPLResidualIndicator original = new LPPLResidualIndicator(new ClosePriceIndicator(series),
                LPPLTestFixtures.compactProfile());
        ComponentDescriptor descriptor = original.toDescriptor();

        Indicator<?> fromDescriptor = IndicatorSerialization.fromDescriptor(series, descriptor);
        Indicator<?> fromJson = Indicator.fromJson(series, original.toJson());

        assertThat(descriptor.getType()).isEqualTo("LPPLResidualIndicator");
        assertThat(ComponentSerialization.toJson(fromDescriptor.toDescriptor()))
                .isEqualTo(ComponentSerialization.toJson(descriptor));
        assertThat(ComponentSerialization.toJson(fromJson.toDescriptor()))
                .isEqualTo(ComponentSerialization.toJson(descriptor));
        assertThat(fromDescriptor.getValue(LPPLTestFixtures.WINDOW))
                .isEqualTo(original.getValue(LPPLTestFixtures.WINDOW));
        assertThat(fromJson.getValue(LPPLTestFixtures.WINDOW)).isEqualTo(original.getValue(LPPLTestFixtures.WINDOW));
    }

    @Test
    void sharedFitViewPreservesIndicatorSerializationContract() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(-0.03, -0.08);
        LPPLResidualIndicator residual = new LPPLResidualIndicator(new ClosePriceIndicator(series),
                LPPLTestFixtures.compactProfile());
        Indicator<LPPLFit> fitIndicator = residual.getFitIndicator();

        ComponentDescriptor descriptor = fitIndicator.toDescriptor();
        Indicator<?> restored = Indicator.fromJson(series, fitIndicator.toJson());

        assertThat(descriptor.getType()).isEqualTo("LPPLFitIndicator");
        assertThat(restored).isInstanceOf(LPPLFitIndicator.class);
        assertThat(restored.getValue(LPPLTestFixtures.WINDOW))
                .isEqualTo(fitIndicator.getValue(LPPLTestFixtures.WINDOW));
    }

    @Test
    @Tag("benchmark")
    @EnabledIfSystemProperty(named = "ta4j.runBenchmarks", matches = "true")
    void measuresSharedAndSeparateDiagnosticCalibration() {
        double[] prices = new double[510];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100.0 * Math.exp(0.001 * i + 0.01 * Math.sin(i / 12.0));
        }
        BarSeries series = new MockBarSeriesBuilder().withData(prices).build();

        LPPLResidualIndicator shared = new LPPLResidualIndicator(series);
        long sharedStart = System.nanoTime();
        for (int index = 500; index < prices.length; index++) {
            shared.getValue(index);
            shared.getFitIndicator().getValue(index);
        }
        long sharedNanos = System.nanoTime() - sharedStart;

        LPPLResidualIndicator separateResidual = new LPPLResidualIndicator(series);
        LPPLFitIndicator separateFits = new LPPLFitIndicator(series);
        long separateStart = System.nanoTime();
        for (int index = 500; index < prices.length; index++) {
            separateResidual.getValue(index);
            separateFits.getValue(index);
        }
        long separateNanos = System.nanoTime() - separateStart;

        LOG.info("LPPL default-profile benchmark: shared={} ms, separate={} ms", sharedNanos / 1_000_000.0,
                separateNanos / 1_000_000.0);
        assertThat(sharedNanos).isPositive();
        assertThat(separateNanos).isPositive();
    }
}
