/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

class LPPLExhaustionIndicatorTest {

    private static final int WINDOW = 80;

    @Test
    void detectsBubbleExhaustionFromSyntheticLPPLSeries() {
        BarSeries series = syntheticSeries(-0.03);
        LPPLExhaustionIndicator indicator = new LPPLExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());

        LPPLExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isValid()).isTrue();
        assertThat(exhaustion.side()).isEqualTo(LPPLExhaustionSide.BUBBLE_EXHAUSTION);
        assertThat(exhaustion.score().doubleValue()).isLessThan(0.0).isGreaterThanOrEqualTo(-1.0);
        assertThat(exhaustion.dominantFit().b()).isLessThan(0.0);
        assertThat(exhaustion.dominantFit().criticalOffset()).isBetween(10, 30);
    }

    @Test
    void detectsCrashExhaustionFromSyntheticLPPLSeries() {
        BarSeries series = syntheticSeries(0.03);
        LPPLExhaustionIndicator indicator = new LPPLExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());

        LPPLExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isValid()).isTrue();
        assertThat(exhaustion.side()).isEqualTo(LPPLExhaustionSide.CRASH_EXHAUSTION);
        assertThat(exhaustion.score().doubleValue()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat(exhaustion.dominantFit().b()).isGreaterThan(0.0);
        assertThat(exhaustion.dominantFit().criticalOffset()).isBetween(10, 30);
    }

    @Test
    void edgeOfActionableHorizonStillProducesNonZeroScore() {
        BarSeries series = syntheticSeries(0.03, 10);
        LPPLExhaustionScoreIndicator indicator = new LPPLExhaustionScoreIndicator(series, compactProfile());

        Num score = indicator.getValue(series.getEndIndex());

        assertThat(score.doubleValue()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void returnsInvalidResultForNonPositiveInput() {
        double[] prices = syntheticPrices(0.03);
        prices[prices.length - 10] = 0.0;
        BarSeries series = new MockBarSeriesBuilder().withData(prices).build();
        LPPLExhaustionIndicator indicator = new LPPLExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());

        LPPLExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isValid()).isFalse();
        assertThat(exhaustion.status()).isEqualTo(LPPLExhaustionStatus.NO_VALID_FIT);
        assertThat(exhaustion.score().isZero()).isTrue();
        assertThat(exhaustion.fits()).extracting(LPPLFit::status).contains(LPPLExhaustionStatus.INVALID_INPUT);
    }

    @Test
    void returnsInvalidResultForNaNInput() {
        double[] prices = syntheticPrices(0.03);
        prices[prices.length - 10] = Double.NaN;
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(prices)
                .build();
        LPPLExhaustionIndicator indicator = new LPPLExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());

        LPPLExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isValid()).isFalse();
        assertThat(exhaustion.status()).isEqualTo(LPPLExhaustionStatus.NO_VALID_FIT);
        assertThat(exhaustion.score().isZero()).isTrue();
        assertThat(exhaustion.fits()).extracting(LPPLFit::status).contains(LPPLExhaustionStatus.INVALID_INPUT);
    }

    @Test
    void returnsWarmupStatusBeforeEnoughHistoryAndNaNScoreProjection() {
        BarSeries series = syntheticSeries(0.03);
        LPPLExhaustionIndicator indicator = new LPPLExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());
        LPPLExhaustionScoreIndicator scoreIndicator = new LPPLExhaustionScoreIndicator(indicator);
        int unstableBars = indicator.getCountOfUnstableBars();
        int warmupIndex = unstableBars - 1;

        LPPLExhaustion exhaustion = indicator.getValue(warmupIndex);
        Num score = scoreIndicator.getValue(warmupIndex);
        LPPLExhaustion firstStableExhaustion = indicator.getValue(unstableBars);
        Num firstStableScore = scoreIndicator.getValue(unstableBars);

        assertThat(unstableBars).isEqualTo(WINDOW - 1);
        assertThat(exhaustion.status()).isEqualTo(LPPLExhaustionStatus.INSUFFICIENT_DATA);
        assertThat(score.isNaN()).isTrue();
        assertThat(firstStableExhaustion.status()).isNotEqualTo(LPPLExhaustionStatus.INSUFFICIENT_DATA);
        assertThat(firstStableScore.isNaN()).isFalse();
    }

    @Test
    void rejectsValidFitWithNonFiniteDiagnostics() {
        assertThatThrownBy(() -> new LPPLFit(WINDOW, LPPLExhaustionStatus.VALID, 1.0, 0.03, 0.01, 0.02, WINDOW + 20.0,
                0.5, 8.0, Double.NaN, 0.1, 0.9, 20, 5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid fits");
    }

    @Test
    void rejectsInconsistentExhaustionFitCounts() {
        BarSeries series = syntheticSeries(0.03);
        Num score = series.numFactory().one();
        LPPLFit fit = new LPPLFit(WINDOW, LPPLExhaustionStatus.VALID, 1.0, 0.03, 0.01, 0.02, WINDOW + 20.0, 0.5, 8.0,
                0.1, 0.1, 0.9, 20, 5);
        List<LPPLFit> fits = List.of(fit);

        assertThatThrownBy(() -> new LPPLExhaustion(LPPLExhaustionStatus.VALID, LPPLExhaustionSide.CRASH_EXHAUSTION,
                score, score, fit, fits, 0, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validFits")
                .hasMessageContaining("attemptedFits");
        assertThatThrownBy(() -> new LPPLExhaustion(LPPLExhaustionStatus.VALID, LPPLExhaustionSide.CRASH_EXHAUSTION,
                score, score, fit, fits, 1, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("crashFits + bubbleFits");
    }

    @Test
    void failedOptimizerReturnsNoValidFitWithoutThrowing() {
        BarSeries series = syntheticSeries(0.03);
        LPPLCalibrationProfile profile = new LPPLCalibrationProfile(new int[] { WINDOW }, 0.1, 0.9, 3, 7.5, 8.5, 3, 10,
                30, 5, 10, 30, 1, 0.6);
        LPPLExhaustionIndicator indicator = new LPPLExhaustionIndicator(new ClosePriceIndicator(series), profile);

        LPPLExhaustion exhaustion = indicator.getValue(series.getEndIndex());

        assertThat(exhaustion.isValid()).isFalse();
        assertThat(exhaustion.status()).isEqualTo(LPPLExhaustionStatus.NO_VALID_FIT);
        assertThat(exhaustion.fits()).extracting(LPPLFit::status).contains(LPPLExhaustionStatus.OPTIMIZER_FAILED);
    }

    @Test
    void serializesAndRestoresConfiguredIndicator() {
        BarSeries series = syntheticSeries(0.03);
        LPPLExhaustionIndicator original = new LPPLExhaustionIndicator(new ClosePriceIndicator(series),
                compactProfile());

        String json = original.toJson();
        Indicator<?> restored = Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(LPPLExhaustionIndicator.class);
        LPPLExhaustionIndicator restoredIndicator = (LPPLExhaustionIndicator) restored;
        assertThat(restoredIndicator.getProfile().windows()).containsExactly(WINDOW);
        assertThat(restoredIndicator.getValue(series.getEndIndex()).side())
                .isEqualTo(LPPLExhaustionSide.CRASH_EXHAUSTION);
    }

    @Test
    void profileUsesValueSemanticsAndDefensiveWindows() {
        int[] windows = { WINDOW, 40, WINDOW };
        LPPLCalibrationProfile profile = new LPPLCalibrationProfile(windows, 0.1, 0.9, 5, 7.5, 8.5, 3, 10, 30, 5, 10,
                30, 80, 0.6);
        windows[0] = 5;

        assertThat(profile.windows()).containsExactly(40, WINDOW);
        assertThat(profile).isEqualTo(new LPPLCalibrationProfile(new int[] { 40, WINDOW }, 0.1, 0.9, 5, 7.5, 8.5, 3, 10,
                30, 5, 10, 30, 80, 0.6));
        assertThat(profile.toString()).contains("windows=[40, 80]");

        int[] copy = profile.windows();
        copy[0] = 5;
        assertThat(profile.windows()).containsExactly(40, WINDOW);
    }

    @Test
    void rejectsInvalidProfileSettings() {
        assertThatThrownBy(
                () -> new LPPLCalibrationProfile(new int[] { 4 }, 0.1, 0.9, 5, 7.5, 8.5, 3, 10, 30, 5, 10, 30, 80, 0.6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LPPLCalibrationProfile(new int[] { WINDOW }, 0.9, 0.1, 5, 7.5, 8.5, 3, 10, 30, 5,
                10, 30, 80, 0.6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LPPLCalibrationProfile(new int[] { WINDOW }, 0.1, 0.9, 5, 7.5, 8.5, 3, 10, 30, 0,
                10, 30, 80, 0.6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LPPLCalibrationProfile(new int[] { WINDOW }, 0.1, 0.9, 5, 7.5, 8.5, 3, 10, 30, 5,
                9, 30, 80, 0.6)).isInstanceOf(IllegalArgumentException.class);
    }

    private static LPPLCalibrationProfile compactProfile() {
        return new LPPLCalibrationProfile(new int[] { WINDOW }, 0.1, 0.9, 5, 7.5, 8.5, 3, 10, 30, 5, 10, 30, 80, 0.6);
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
