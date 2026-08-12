/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.event.BinningStrategy;
import org.ta4j.core.analysis.event.EventMutualInformationConfig;
import org.ta4j.core.analysis.event.EventMutualInformationEvaluator;
import org.ta4j.core.analysis.event.EventMutualInformationResult;
import org.ta4j.core.indicators.NetMomentumIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.DynamicTimeWarpingDistanceIndicator;
import org.ta4j.core.indicators.statistics.LeadLagCorrelationIndicator;
import org.ta4j.core.indicators.statistics.LeadLagCorrelationIndicator.LagSelectionPolicy;
import org.ta4j.core.indicators.statistics.LeadLagCorrelationIndicator.Profile;
import org.ta4j.core.indicators.zigzag.ZigZagPivotHighIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagPivotLowIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Demonstrates the three advanced relationship-analysis capabilities on one
 * deterministic dataset: the lead/lag correlation profile (TLCC), bounded
 * dynamic time warping (DTW), and event-aware mutual information.
 *
 * <p>
 * The fixture is a committed, ossified Coinbase BTC daily dataset loaded from
 * the examples classpath, so the demo is fully reproducible and needs no
 * network access. Net Momentum is the predictor; close price is the comparison
 * series; causal ZigZag swing confirmations are the Boolean event targets.
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
 * @see LeadLagCorrelationIndicator
 * @see DynamicTimeWarpingDistanceIndicator
 * @see EventMutualInformationEvaluator
 */
public final class LeadLagDtwEventAnalysisExample {

    private static final Logger LOG = LogManager.getLogger(LeadLagDtwEventAnalysisExample.class);

    /** Committed daily BTC dataset from the examples classpath. */
    private static final String DATASET_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";

    private LeadLagDtwEventAnalysisExample() {
    }

    private static BarSeries loadDataset() {
        BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(DATASET_RESOURCE);
        if (series == null || series.isEmpty()) {
            throw new IllegalStateException("Required dataset '" + DATASET_RESOURCE + "' is missing or empty");
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
    record DemoResult(Profile profile, Num dtwDistance, EventMutualInformationResult swingHighMi,
            EventMutualInformationResult swingLowMi) {
    }

    /**
     * Runs the demo and returns the three workflow results.
     *
     * @return the TLCC profile, DTW distance, and swing-high/swing-low MI
     *         evaluations
     */
    static DemoResult run() {
        BarSeries series = loadDataset();
        int lastIndex = series.getEndIndex();

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        NetMomentumIndicator momentum = new NetMomentumIndicator(
                NumericIndicator.of(close).minus(new PreviousValueIndicator(close, 1)), 5, 0);
        ZigZagStateIndicator zigZagState = new ZigZagStateIndicator(close, 60);
        ZigZagPivotHighIndicator swingHighConfirmation = new ZigZagPivotHighIndicator(zigZagState);
        ZigZagPivotLowIndicator swingLowConfirmation = new ZigZagPivotLowIndicator(zigZagState);

        // Capability A: lead/lag correlation profile over a bounded lag range.
        // The symmetric convenience constructor searches [-20, 20]; positive
        // lags mean the first indicator leads the second.
        LeadLagCorrelationIndicator leadLag = new LeadLagCorrelationIndicator(momentum, close, 32, 20,
                LagSelectionPolicy.MAXIMUM_ABSOLUTE_CORRELATION);
        Profile profile = leadLag.getProfile(lastIndex);
        LOG.info("TLCC: {} defined lags of {}, best lags {}, selected lag {} with correlation {}",
                profile.points().stream().filter(point -> point.isDefined()).count(), profile.points().size(),
                profile.bestLags(), profile.selectedLag(), profile.selectedCorrelation());

        // Capability B: bounded DTW shape distance between the waves, using
        // the short recommended configuration (z-score normalization, squared
        // local cost, Sakoe-Chiba radius 5, path-length normalization).
        DynamicTimeWarpingDistanceIndicator dtw = new DynamicTimeWarpingDistanceIndicator(momentum, close, 32,
                DynamicTimeWarpingDistanceIndicator.Config.shapeComparison(5));
        Num dtwDistance = dtw.getValue(lastIndex);
        LOG.info("DTW: z-score shape distance (window 32, Sakoe-Chiba radius 5) = {}", dtwDistance);

        // Capability C: event-aware MI of momentum state vs current-or-future
        // confirmations. The convenience config clamps to the available range.
        EventMutualInformationConfig miConfig = new EventMutualInformationConfig(0, 3, 8,
                BinningStrategy.EQUAL_FREQUENCY);
        EventMutualInformationEvaluator miEvaluator = new EventMutualInformationEvaluator();
        EventMutualInformationResult swingHighMi = miEvaluator.evaluate(momentum, swingHighConfirmation, 0, lastIndex,
                miConfig);
        LOG.info("Swing-high MI: samples={} positives={} rate={} MI={} nats, H(Y)={} nats, normalized={}",
                swingHighMi.sampleCount(), swingHighMi.positiveTargetCount(), swingHighMi.positiveTargetRate(),
                swingHighMi.mutualInformationNats(), swingHighMi.targetEntropyNats(),
                swingHighMi.normalizedMutualInformation());
        EventMutualInformationResult swingLowMi = miEvaluator.evaluate(momentum, swingLowConfirmation, 0, lastIndex,
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
