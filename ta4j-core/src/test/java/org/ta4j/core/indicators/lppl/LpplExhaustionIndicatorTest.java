/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

class LpplExhaustionIndicatorTest {

    private static final int WINDOW = 80;

    @Test
    void detectsBubbleExhaustionFromSyntheticLpplSeries() {
        BarSeries series = syntheticSeries(-0.03);
        LpplExhaustionIndicator indicator = new LpplExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());

        LpplExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isValid()).isTrue();
        assertThat(exhaustion.side()).isEqualTo(LpplExhaustionSide.BUBBLE_EXHAUSTION);
        assertThat(exhaustion.score().doubleValue()).isLessThan(0.0).isGreaterThanOrEqualTo(-1.0);
        assertThat(exhaustion.dominantFit().b()).isLessThan(0.0);
        assertThat(exhaustion.dominantFit().criticalOffset()).isBetween(10, 30);
    }

    @Test
    void detectsCrashExhaustionFromSyntheticLpplSeries() {
        BarSeries series = syntheticSeries(0.03);
        LpplExhaustionIndicator indicator = new LpplExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());

        LpplExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isValid()).isTrue();
        assertThat(exhaustion.side()).isEqualTo(LpplExhaustionSide.CRASH_EXHAUSTION);
        assertThat(exhaustion.score().doubleValue()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat(exhaustion.dominantFit().b()).isGreaterThan(0.0);
        assertThat(exhaustion.dominantFit().criticalOffset()).isBetween(10, 30);
    }

    @Test
    void edgeOfActionableHorizonStillProducesNonZeroScore() {
        BarSeries series = syntheticSeries(0.03, 10);
        LpplExhaustionScoreIndicator indicator = new LpplExhaustionScoreIndicator(series, compactProfile());

        Num score = indicator.getValue(series.getEndIndex());

        assertThat(score.doubleValue()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void returnsInvalidResultForNonPositiveInput() {
        double[] prices = syntheticPrices(0.03);
        prices[prices.length - 10] = 0.0;
        BarSeries series = new MockBarSeriesBuilder().withData(prices).build();
        LpplExhaustionIndicator indicator = new LpplExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());

        LpplExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isValid()).isFalse();
        assertThat(exhaustion.status()).isEqualTo(LpplExhaustionStatus.NO_VALID_FIT);
        assertThat(exhaustion.score().isZero()).isTrue();
        assertThat(exhaustion.fits()).extracting(LpplFit::status).contains(LpplExhaustionStatus.INVALID_INPUT);
    }

    @Test
    void returnsWarmupStatusBeforeEnoughHistoryAndNaNScoreProjection() {
        BarSeries series = syntheticSeries(0.03);
        LpplExhaustionIndicator indicator = new LpplExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());
        LpplExhaustionScoreIndicator scoreIndicator = new LpplExhaustionScoreIndicator(indicator);
        int unstableBars = indicator.getCountOfUnstableBars();
        int warmupIndex = unstableBars - 1;

        LpplExhaustion exhaustion = indicator.getValue(warmupIndex);
        Num score = scoreIndicator.getValue(warmupIndex);
        LpplExhaustion firstStableExhaustion = indicator.getValue(unstableBars);
        Num firstStableScore = scoreIndicator.getValue(unstableBars);

        assertThat(unstableBars).isEqualTo(WINDOW - 1);
        assertThat(exhaustion.status()).isEqualTo(LpplExhaustionStatus.INSUFFICIENT_DATA);
        assertThat(score.isNaN()).isTrue();
        assertThat(firstStableExhaustion.status()).isNotEqualTo(LpplExhaustionStatus.INSUFFICIENT_DATA);
        assertThat(firstStableScore.isNaN()).isFalse();
    }

    @Test
    void rejectsValidFitWithNonFiniteDiagnostics() {
        assertThatThrownBy(() -> new LpplFit(WINDOW, LpplExhaustionStatus.VALID, 1.0, 0.03, 0.01, 0.02, WINDOW + 20.0,
                0.5, 8.0, Double.NaN, 0.1, 0.9, 20, 5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid fits");
    }

    @Test
    void failedOptimizerReturnsNoValidFitWithoutThrowing() {
        BarSeries series = syntheticSeries(0.03);
        LpplCalibrationProfile profile = new LpplCalibrationProfile(new int[] { WINDOW }, 0.1, 0.9, 3, 7.5, 8.5, 3, 10,
                30, 5, 10, 30, 1, 0.6);
        LpplExhaustionIndicator indicator = new LpplExhaustionIndicator(new ClosePriceIndicator(series), profile);

        LpplExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isValid()).isFalse();
        assertThat(exhaustion.status()).isEqualTo(LpplExhaustionStatus.NO_VALID_FIT);
        assertThat(exhaustion.fits()).extracting(LpplFit::status).contains(LpplExhaustionStatus.OPTIMIZER_FAILED);
    }

    @Test
    void serializesAndRestoresConfiguredIndicator() {
        BarSeries series = syntheticSeries(0.03);
        LpplExhaustionIndicator original = new LpplExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());

        String json = original.toJson();
        Indicator<?> restored = Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(LpplExhaustionIndicator.class);
        LpplExhaustionIndicator restoredIndicator = (LpplExhaustionIndicator) restored;
        assertThat(restoredIndicator.getProfile().windows()).containsExactly(WINDOW);
        assertThat(restoredIndicator.getValue(series.getEndIndex()).side())
                .isEqualTo(LpplExhaustionSide.CRASH_EXHAUSTION);
    }

    @Test
    void profileUsesValueSemanticsAndDefensiveWindows() {
        int[] windows = { WINDOW, 40, WINDOW };
        LpplCalibrationProfile profile = new LpplCalibrationProfile(windows, 0.1, 0.9, 5, 7.5, 8.5, 3, 10, 30, 5, 10,
                30, 80, 0.6);
        windows[0] = 5;

        assertThat(profile.windows()).containsExactly(40, WINDOW);
        assertThat(profile).isEqualTo(new LpplCalibrationProfile(new int[] { 40, WINDOW }, 0.1, 0.9, 5, 7.5, 8.5, 3, 10,
                30, 5, 10, 30, 80, 0.6));
        assertThat(profile.toString()).contains("windows=[40, 80]");

        int[] copy = profile.windows();
        copy[0] = 5;
        assertThat(profile.windows()).containsExactly(40, WINDOW);
    }

    @Test
    void rejectsInvalidProfileSettings() {
        assertThatThrownBy(
                () -> new LpplCalibrationProfile(new int[] { 4 }, 0.1, 0.9, 5, 7.5, 8.5, 3, 10, 30, 5, 10, 30, 80, 0.6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LpplCalibrationProfile(new int[] { WINDOW }, 0.9, 0.1, 5, 7.5, 8.5, 3, 10, 30, 5,
                10, 30, 80, 0.6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LpplCalibrationProfile(new int[] { WINDOW }, 0.1, 0.9, 5, 7.5, 8.5, 3, 10, 30, 0,
                10, 30, 80, 0.6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LpplCalibrationProfile(new int[] { WINDOW }, 0.1, 0.9, 5, 7.5, 8.5, 3, 10, 30, 5,
                9, 30, 80, 0.6)).isInstanceOf(IllegalArgumentException.class);
    }

    private static LpplCalibrationProfile compactProfile() {
        return new LpplCalibrationProfile(new int[] { WINDOW }, 0.1, 0.9, 5, 7.5, 8.5, 3, 10, 30, 5, 10, 30, 80, 0.6);
    }

    private static BarSeries syntheticSeries(double b) {
        return syntheticSeries(b, 20);
    }

    private static BarSeries syntheticSeries(double b, int criticalOffset) {
        return new MockBarSeriesBuilder().withData(syntheticPrices(b, criticalOffset)).build();
    }

    private static double[] syntheticPrices(double b) {
        return syntheticPrices(b, 20);
    }

    private static double[] syntheticPrices(double b, int criticalOffset) {
        double[] prices = new double[WINDOW];
        double criticalTime = WINDOW - 1 + criticalOffset;
        double a = 4.6;
        double c1 = 0.01;
        double c2 = -0.006;
        double m = 0.5;
        double omega = 8.0;
        for (int i = 0; i < prices.length; i++) {
            double dt = criticalTime - i;
            double power = Math.pow(dt, m);
            double logDt = Math.log(dt);
            double logPrice = a + b * power + c1 * power * Math.cos(omega * logDt)
                    + c2 * power * Math.sin(omega * logDt);
            prices[i] = Math.exp(logPrice);
        }
        return Arrays.copyOf(prices, prices.length);
    }
}
