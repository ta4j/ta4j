/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.forecast.KinematicKalmanPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.num.Num;

class KinematicKalmanForecastExampleTest {

    private static final String FIXTURE_SHA256 = "76a9b43ace82ed6fddc8dc16da377e6ae13d89ca7c1866534eaf75f6409fe155";
    private static final BarSeries SERIES = KinematicKalmanForecastExample.loadSeries();
    private static final KinematicKalmanForecastExample.ForecastModel MODEL = KinematicKalmanForecastExample
            .createModel(SERIES);

    @Test
    void ossifiedSp500FixtureMatchesTheCapturedYahooSnapshot() throws Exception {
        assertEquals("^GSPC", SERIES.getName());
        assertEquals(3997, SERIES.getBarCount());
        assertEquals(Instant.parse("1950-01-02T05:00:00Z"), SERIES.getFirstBar().getEndTime());
        assertEquals(Instant.parse("2026-07-27T04:00:00Z"), SERIES.getLastBar().getEndTime());
        assertEquals(16.65999984741211, SERIES.getFirstBar().getOpenPrice().doubleValue(), 1e-12);
        assertEquals(7316.14990234375, SERIES.getLastBar().getClosePrice().doubleValue(), 1e-12);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = KinematicKalmanForecastExampleTest.class.getClassLoader()
                .getResourceAsStream(KinematicKalmanForecastExample.SP500_RESOURCE)) {
            assertNotNull(input, "S&P 500 resource should be available");
            assertEquals(FIXTURE_SHA256, HexFormat.of().formatHex(digest.digest(input.readAllBytes())));
        }
    }

    @Test
    void customNoiseCompositionPreservesVarianceUnitsAndInvertsChop() {
        int index = SERIES.getEndIndex();
        double atr = MODEL.atr().getValue(index).doubleValue();
        double chop = MODEL.chop().getValue(index).doubleValue();
        double expectedProcessNoise = Math.max(KinematicKalmanForecastExample.MINIMUM_VARIANCE,
                atr * atr * KinematicKalmanForecastExample.PROCESS_ATR_VARIANCE_SCALE);
        double trendStrength = Math.max(0, Math.min(1, 1 - chop));
        double expectedMeasurementNoise = Math.max(KinematicKalmanForecastExample.MINIMUM_VARIANCE,
                atr * atr
                        * (KinematicKalmanForecastExample.MEASUREMENT_VARIANCE_FLOOR
                                + KinematicKalmanForecastExample.MEASUREMENT_TREND_VARIANCE_SCALE * trendStrength
                                        * trendStrength));

        Num processNoise = MODEL.processNoise().getValue(index);
        Num measurementNoise = MODEL.measurementNoise().getValue(index);
        assertTrue(Num.isFinite(processNoise) && processNoise.isPositive());
        assertTrue(Num.isFinite(measurementNoise) && measurementNoise.isPositive());
        assertEquals(expectedProcessNoise, processNoise.doubleValue(), expectedProcessNoise * 1e-12);
        assertEquals(expectedMeasurementNoise, measurementNoise.doubleValue(), expectedMeasurementNoise * 1e-12);
    }

    @Test
    void forecastsShareStateAcrossHorizonsAndComposeWithConformalCalibration() {
        int index = SERIES.getEndIndex();
        assertEquals(List.of(1, 4, 13),
                MODEL.forecasts().stream().map(KinematicKalmanPriceForecastIndicator::getHorizon).toList());
        for (KinematicKalmanPriceForecastIndicator forecastIndicator : MODEL.forecasts()) {
            Forecast forecast = forecastIndicator.getValue(index);
            assertTrue(forecast.isStable());
            assertEquals(index, forecast.decisionIndex());
            assertTrue(forecast.quantile(0.05).isLessThan(forecast.mean()));
            assertTrue(forecast.quantile(0.95).isGreaterThan(forecast.mean()));
        }

        Forecast baseFourWeek = MODEL.forecasts().get(1).getValue(index);
        Forecast conformalFourWeek = MODEL.conformalFourWeek().getValue(index);
        assertTrue(conformalFourWeek.isStable());
        assertTrue(conformalFourWeek.quantile(0.05).isLessThanOrEqual(baseFourWeek.quantile(0.05)));
        assertTrue(conformalFourWeek.quantile(0.95).isGreaterThanOrEqual(baseFourWeek.quantile(0.95)));
    }

    @Test
    void walkForwardEvaluationProducesFiniteComparableMetrics() {
        List<KinematicKalmanForecastExample.ForecastEvaluation> evaluations = KinematicKalmanForecastExample
                .evaluate(MODEL, KinematicKalmanForecastExample.WALK_FORWARD_DECISIONS);

        assertEquals(List.of(1, 4, 13),
                evaluations.stream().map(KinematicKalmanForecastExample.ForecastEvaluation::horizon).toList());
        for (KinematicKalmanForecastExample.ForecastEvaluation evaluation : evaluations) {
            assertEquals(KinematicKalmanForecastExample.WALK_FORWARD_DECISIONS, evaluation.sampleCount());
            assertTrue(Double.isFinite(evaluation.kalmanMeanAbsoluteError()));
            assertTrue(Double.isFinite(evaluation.lastCloseMeanAbsoluteError()));
            assertTrue(evaluation.analyticCoverage() >= 0 && evaluation.analyticCoverage() <= 1);
            assertTrue(evaluation.analyticNormalizedWidth() > 0);
        }
        KinematicKalmanForecastExample.ForecastEvaluation fourWeek = evaluations.get(1);
        assertTrue(fourWeek.conformalCoverage() >= 0 && fourWeek.conformalCoverage() <= 1);
        assertTrue(fourWeek.conformalNormalizedWidth() >= fourWeek.analyticNormalizedWidth());
    }
}
