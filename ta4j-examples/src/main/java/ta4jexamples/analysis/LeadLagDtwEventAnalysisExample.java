/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import java.time.Duration;
import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.event.BinningStrategy;
import org.ta4j.core.analysis.event.EventMutualInformationConfig;
import org.ta4j.core.analysis.event.EventMutualInformationEvaluator;
import org.ta4j.core.analysis.event.EventMutualInformationConfig;
import org.ta4j.core.analysis.event.EventMutualInformationResult;
import org.ta4j.core.analysis.event.EventSignal;
import org.ta4j.core.analysis.event.EventSignals;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.HistoryPolicy;
import org.ta4j.core.indicators.NetMomentumIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.DynamicTimeWarpingConfig;
import org.ta4j.core.indicators.statistics.DynamicTimeWarpingDistanceIndicator;
import org.ta4j.core.indicators.statistics.LagCorrelationProfile;
import org.ta4j.core.indicators.statistics.LagSelectionPolicy;
import org.ta4j.core.indicators.statistics.LeadLagCorrelationAnalyzer;
import org.ta4j.core.indicators.statistics.LocalDistance;
import org.ta4j.core.indicators.statistics.PathCostNormalization;
import org.ta4j.core.indicators.statistics.SequenceNormalization;
import org.ta4j.core.indicators.statistics.WarpingWindow;
import org.ta4j.core.indicators.zigzag.ZigZagPivotHighIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagPivotLowIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Demonstrates the three advanced relationship-analysis capabilities on one
 * deterministic synthetic series: the lead/lag correlation profile (TLCC),
 * bounded dynamic time warping (DTW), and event-aware mutual information.
 *
 * <p>
 * The fixture is the same deterministic sine series used by
 * {@link EventSynchronizationExample}, so the demo is fully reproducible and
 * needs no network access. Net Momentum is the predictor; close price is the
 * comparison series; causal ZigZag swing confirmations are the Boolean event
 * targets.
 * </p>
 *
 * <p>
 * <strong>Confirmation-time semantics:</strong> the ZigZag Boolean indicators
 * are {@code true} at the bar where a prior pivot becomes confirmed. The MI
 * evaluation targets those confirmation indexes in a current-or-future bar
 * window (offset zero labels the sample's own bar); it never reads a target
 * index outside the supplied analysis partition.
 * </p>
 *
 * <p>
 * The demo reports findings without claiming predictive causality: correlation,
 * shape distance, and mutual information describe association, not causation.
 * </p>
 *
 * @see LeadLagCorrelationAnalyzer
 * @see DynamicTimeWarpingDistanceIndicator
 * @see EventMutualInformationEvaluator
 */
public final class LeadLagDtwEventAnalysisExample {

    private static final Logger LOG = LogManager.getLogger(LeadLagDtwEventAnalysisExample.class);

    private static final int BARS = 200;

    private LeadLagDtwEventAnalysisExample() {
    }

    private static BarSeries sineSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        Duration timePeriod = Duration.ofDays(1);
        Instant endTime = Instant.EPOCH;
        for (int i = 0; i < BARS; i++) {
            endTime = endTime.plus(timePeriod);
            double close = 1000.0 + 100.0 * Math.sin(2.0 * Math.PI * i / 20.0);
            series.barBuilder()
                    .timePeriod(timePeriod)
                    .endTime(endTime)
                    .openPrice(close)
                    .closePrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .volume(1d)
                    .add();
        }
        return series;
    }

    /**
     * Findings of the three demo workflows.
     *
     * @param profile     TLCC profile of Net Momentum against close price
     * @param dtwDistance DTW distance between the Net Momentum and price waves
     * @param swingHighMi event MI of Net Momentum against current-or-future
     *                    swing-high confirmations
     * @param swingLowMi  event MI of Net Momentum against current-or-future
     *                    swing-low confirmations
     */
    record DemoResult(LagCorrelationProfile profile, Num dtwDistance, EventMutualInformationResult swingHighMi,
            EventMutualInformationResult swingLowMi) {
    }

    /**
     * Runs the demo and returns the three workflow results.
     *
     * @return the TLCC profile, DTW distance, and swing-high/swing-low MI
     *         evaluations
     */
    static DemoResult run() {
        BarSeries series = sineSeries();

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        NetMomentumIndicator momentum = new NetMomentumIndicator(
                NumericIndicator.of(close).minus(new PreviousValueIndicator(close, 1)), 5, 0);
        ZigZagStateIndicator zigZagState = new ZigZagStateIndicator(close, 60);
        ZigZagPivotHighIndicator swingHighConfirmation = new ZigZagPivotHighIndicator(zigZagState);
        ZigZagPivotLowIndicator swingLowConfirmation = new ZigZagPivotLowIndicator(zigZagState);

        // Capability A: lead/lag correlation profile over a bounded lag range.
        LeadLagCorrelationAnalyzer analyzer = new LeadLagCorrelationAnalyzer();
        LagCorrelationProfile profile = analyzer.analyze(momentum, close, BARS - 1, 32, -20, 20,
                LagSelectionPolicy.MAXIMUM_ABSOLUTE_CORRELATION);
        LOG.info("TLCC: {} defined lags of {}, best lags {}, selected lag {} with correlation {}",
                profile.points().stream().filter(point -> point.isDefined()).count(), profile.points().size(),
                profile.bestLags(), profile.selectedLag(), profile.selectedCorrelation());

        // Capability B: bounded DTW shape distance between the waves.
        DynamicTimeWarpingConfig dtwConfig = new DynamicTimeWarpingConfig(SequenceNormalization.Z_SCORE,
                LocalDistance.SQUARED, WarpingWindow.sakoeChiba(5), PathCostNormalization.BY_PATH_LENGTH);
        DynamicTimeWarpingDistanceIndicator dtw = new DynamicTimeWarpingDistanceIndicator(momentum, close, 32,
                dtwConfig);
        Num dtwDistance = dtw.getValue(BARS - 1);
        LOG.info("DTW: z-score shape distance (window 32, Sakoe-Chiba radius 5) = {}", dtwDistance);

        // Capability C: event-aware MI of momentum state vs current-or-future
        // confirmations.
        EventMutualInformationConfig miConfig = new EventMutualInformationConfig(0, 3, 8,
                BinningStrategy.EQUAL_FREQUENCY, HistoryPolicy.CLAMP);
        EventMutualInformationEvaluator miEvaluator = new EventMutualInformationEvaluator();
        EventSignal swingHighEvents = EventSignals.fromIndicator(swingHighConfirmation);
        EventMutualInformationResult swingHighMi = miEvaluator.evaluate(momentum, swingHighEvents, 0, BARS - 1,
                miConfig);
        LOG.info("Swing-high MI: samples={} positives={} rate={} MI={} nats, H(Y)={} nats, normalized={}",
                swingHighMi.sampleCount(), swingHighMi.positiveTargetCount(), swingHighMi.positiveTargetRate(),
                swingHighMi.mutualInformationNats(), swingHighMi.targetEntropyNats(),
                swingHighMi.normalizedMutualInformation());
        EventMutualInformationResult swingLowMi = miEvaluator.evaluate(momentum, swingLowConfirmation, 0, BARS - 1,
                miConfig);
        LOG.info("Swing-low MI: samples={} positives={} rate={} MI={} nats, H(Y)={} nats, normalized={}",
                swingLowMi.sampleCount(), swingLowMi.positiveTargetCount(), swingLowMi.positiveTargetRate(),
                swingLowMi.mutualInformationNats(), swingLowMi.targetEntropyNats(),
                swingLowMi.normalizedMutualInformation());

        return new DemoResult(profile, dtwDistance, swingHighMi, swingLowMi);
    }

    /**
     * Runs the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        run();
    }
}
