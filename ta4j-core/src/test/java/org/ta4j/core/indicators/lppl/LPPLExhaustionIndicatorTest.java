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
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.IndicatorSerialization;

class LPPLExhaustionIndicatorTest {

    @Test
    void detectsBubbleAndCrashExhaustion() {
        LPPLExhaustion bubble = calculate(-0.03);
        LPPLExhaustion crash = calculate(0.03);

        assertThat(bubble.isActionable()).isTrue();
        assertThat(bubble.side()).isEqualTo(LPPLExhaustionSide.BUBBLE_EXHAUSTION);
        assertThat(bubble.score().doubleValue()).isBetween(-1.0, 0.0);
        assertThat(crash.isActionable()).isTrue();
        assertThat(crash.side()).isEqualTo(LPPLExhaustionSide.CRASH_EXHAUSTION);
        assertThat(crash.score().doubleValue()).isBetween(0.0, 1.0);
    }

    @Test
    void returnsNoActionableFitForInvalidInput() {
        double[] prices = LPPLTestFixtures.syntheticPrices(0.03);
        prices[prices.length - 10] = Double.NaN;
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(prices)
                .build();
        LPPLExhaustionIndicator indicator = new LPPLExhaustionIndicator(new ClosePriceIndicator(series),
                LPPLTestFixtures.compactProfile());

        LPPLExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isActionable()).isFalse();
        assertThat(exhaustion.status()).isEqualTo(LPPLExhaustionStatus.NO_VALID_FIT);
        assertThat(exhaustion.fits()).extracting(LPPLFit::status).contains(LPPLExhaustionStatus.INVALID_INPUT);
    }

    @Test
    void reportsWarmupBoundaryWithoutReadingPrehistory() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(0.03);
        LPPLExhaustionIndicator indicator = new LPPLExhaustionIndicator(new ClosePriceIndicator(series),
                LPPLTestFixtures.compactProfile());
        int unstableBars = indicator.getCountOfUnstableBars();

        assertThat(unstableBars).isEqualTo(LPPLTestFixtures.WINDOW - 1);
        assertThat(indicator.getValue(unstableBars - 1).status()).isEqualTo(LPPLExhaustionStatus.INSUFFICIENT_DATA);
        assertThat(indicator.getValue(unstableBars).status()).isNotEqualTo(LPPLExhaustionStatus.INSUFFICIENT_DATA);
    }

    @Test
    void descriptorAndJsonRoundTripsPreserveProfileAndOutput() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(0.03);
        LPPLExhaustionIndicator original = new LPPLExhaustionIndicator(new ClosePriceIndicator(series),
                LPPLTestFixtures.compactProfile());
        ComponentDescriptor descriptor = original.toDescriptor();

        LPPLExhaustionIndicator fromDescriptor = (LPPLExhaustionIndicator) IndicatorSerialization.fromDescriptor(series,
                descriptor);
        Indicator<?> fromJson = Indicator.fromJson(series, original.toJson());

        assertThat(ComponentSerialization.toJson(fromDescriptor.toDescriptor()))
                .isEqualTo(ComponentSerialization.toJson(descriptor));
        assertThat(ComponentSerialization.toJson(fromJson.toDescriptor()))
                .isEqualTo(ComponentSerialization.toJson(descriptor));
        assertThat(fromDescriptor.getProfile()).isEqualTo(original.getProfile());
        assertThat(fromDescriptor.getValue(series.getEndIndex())).isEqualTo(original.getValue(series.getEndIndex()));
        assertThat(fromJson.getValue(series.getEndIndex())).isEqualTo(original.getValue(series.getEndIndex()));
    }

    @Test
    void failedOptimizerReturnsNoActionableFitWithoutThrowing() {
        BarSeries series = LPPLTestFixtures.syntheticSeries(0.03);
        LPPLCalibrationProfile profile = LPPLTestFixtures.compactProfile().withOptimizerSettings(1, 0.6);
        LPPLExhaustionIndicator indicator = new LPPLExhaustionIndicator(new ClosePriceIndicator(series), profile);

        LPPLExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isActionable()).isFalse();
        assertThat(exhaustion.fits()).extracting(LPPLFit::status).contains(LPPLExhaustionStatus.OPTIMIZER_FAILED);
    }

    private static LPPLExhaustion calculate(double b) {
        BarSeries series = LPPLTestFixtures.syntheticSeries(b);
        LPPLExhaustionIndicator indicator = new LPPLExhaustionIndicator(new ClosePriceIndicator(series),
                LPPLTestFixtures.compactProfile());
        return indicator.getValue(series.getEndIndex());
    }
}
