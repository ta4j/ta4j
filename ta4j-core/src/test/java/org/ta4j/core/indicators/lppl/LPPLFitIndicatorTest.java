/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.IndicatorSerialization;

class LPPLFitIndicatorTest {

    @Test
    void evaluatesCurrentBarWithoutRefittingIt() {
        BarSeries aboveSeries = LPPLTestFixtures.syntheticSeries(-0.03, 0.08);
        BarSeries belowSeries = LPPLTestFixtures.syntheticSeries(-0.03, -0.08);
        LPPLFit above = fit(aboveSeries);
        LPPLFit below = fit(belowSeries);

        assertThat(above.isConverged()).isTrue();
        assertThat(below.isConverged()).isTrue();
        assertThat(above.a()).isEqualTo(below.a());
        assertThat(above.b()).isEqualTo(below.b());
        assertThat(above.criticalTime()).isEqualTo(below.criticalTime());
        assertThat(above.residual()).isPositive();
        assertThat(below.residual()).isNegative();
        assertThat(above.normalizedResidual()).isBetween(0.0, 1.0);
        assertThat(below.normalizedResidual()).isBetween(-1.0, 0.0);
    }

    @Test
    void reportsWarmupAndInvalidInputWithoutReadingPrehistory() {
        double[] prices = LPPLTestFixtures.syntheticPrices(-0.03, 0.0);
        prices[10] = 0.0;
        BarSeries series = new MockBarSeriesBuilder().withData(prices).build();
        LPPLFitIndicator indicator = new LPPLFitIndicator(series, LPPLTestFixtures.compactProfile());

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(LPPLTestFixtures.WINDOW);
        assertThat(indicator.getValue(LPPLTestFixtures.WINDOW - 1).status()).isEqualTo(LPPLFitStatus.INSUFFICIENT_DATA);
        assertThat(indicator.getValue(LPPLTestFixtures.WINDOW).status()).isEqualTo(LPPLFitStatus.INVALID_INPUT);
    }

    @Test
    void laterPricesCannotChangeAnEarlierFit() {
        double[] first = LPPLTestFixtures.syntheticPrices(-0.03, 0.04);
        double[] second = first.clone();
        second[LPPLTestFixtures.WINDOW + 1] *= 20.0;
        LPPLFit firstFit = fit(new MockBarSeriesBuilder().withData(first).build());
        LPPLFit secondFit = fit(new MockBarSeriesBuilder().withData(second).build());

        assertThat(secondFit).isEqualTo(firstFit);
    }

    @Test
    void descriptorAndJsonRoundTripsPreserveProfileAndOutput() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(-0.03, 0.04);
        LPPLFitIndicator original = new LPPLFitIndicator(new ClosePriceIndicator(series),
                LPPLTestFixtures.compactProfile());
        ComponentDescriptor descriptor = original.toDescriptor();

        LPPLFitIndicator fromDescriptor = (LPPLFitIndicator) IndicatorSerialization.fromDescriptor(series, descriptor);
        Indicator<?> fromJson = Indicator.fromJson(series, original.toJson());

        assertThat(ComponentSerialization.toJson(fromDescriptor.toDescriptor()))
                .isEqualTo(ComponentSerialization.toJson(descriptor));
        assertThat(ComponentSerialization.toJson(fromJson.toDescriptor()))
                .isEqualTo(ComponentSerialization.toJson(descriptor));
        assertThat(fromDescriptor.getProfile()).isEqualTo(original.getProfile());
        assertThat(fromDescriptor.getValue(LPPLTestFixtures.WINDOW))
                .isEqualTo(original.getValue(LPPLTestFixtures.WINDOW));
    }

    private static LPPLFit fit(BarSeries series) {
        return new LPPLFitIndicator(series, LPPLTestFixtures.compactProfile()).getValue(LPPLTestFixtures.WINDOW);
    }
}
