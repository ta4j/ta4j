/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

class WalkForwardEngineTest {

    @Test
    void engineBuildsSnapshotsObservationsMetricsAndAuditDeterministically() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(220)).build();
        NumFactory numFactory = series.numFactory();
        WalkForwardConfig config = new WalkForwardConfig(80, 30, 30, 2, 2, 0, 5, List.of(3), 2, List.of(1), 7L);

        List<WalkForwardRunResult.LeakageAudit> auditRecords = new ArrayList<>();
        PredictionProvider<String, String> provider = (fullSeries, decisionIndex, context) -> List.of(
                new RankedPrediction<>(context + "-bull", 1, numFactory.numOf(0.7), numFactory.numOf(0.8), "bull"),
                new RankedPrediction<>(context + "-bear", 2, numFactory.numOf(0.3), numFactory.numOf(0.4), "bear"));

        OutcomeLabeler<String, Boolean> labeler = (fullSeries, decisionIndex, horizonBars, prediction) -> {
            double start = fullSeries.getBar(decisionIndex).getClosePrice().doubleValue();
            double end = fullSeries.getBar(decisionIndex + horizonBars).getClosePrice().doubleValue();
            boolean movedUp = end > start;
            return "bull".equals(prediction.payload()) ? movedUp : !movedUp;
        };

        List<WalkForwardMetric<String, Boolean>> metrics = List.of(
                WalkForwardMetric.agreement("eventAgreement", 1, (prediction, outcome) -> outcome),
                WalkForwardMetric.topKHitRate("top2Hit", 2, (prediction, outcome) -> outcome),
                WalkForwardMetric.brierScore("brier", 1, outcome -> outcome ? numFactory.one() : numFactory.zero()));

        WalkForwardEngine<String, String, Boolean> engine = new WalkForwardEngine<>(
                new AnchoredExpandingWalkForwardSplitter(), provider, labeler, metrics, ignored -> {
                    // no-op
                }, auditRecords::add);

        WalkForwardRunResult<String, Boolean> first = engine.run(series, "ctx", config);
        WalkForwardRunResult<String, Boolean> second = engine.run(series, "ctx", config);

        assertThat(first.splits()).isNotEmpty();
        assertThat(first.snapshots()).isNotEmpty();
        assertThat(first.observationsByHorizon().get(5)).isNotEmpty();
        assertThat(first.globalMetricsForHorizon(5)).containsKeys("eventAgreement", "top2Hit", "brier");
        assertThat(first.foldMetricsForHorizon(5)).isNotEmpty();
        assertThat(first.runtimeReport().overallRuntime()).isNotNull();
        assertThat(first.manifest().configHash()).isEqualTo(config.configHash());

        assertThat(auditRecords).isNotEmpty();
        for (WalkForwardRunResult.LeakageAudit audit : auditRecords) {
            assertThat(audit.visibleEndIndex()).isEqualTo(audit.decisionIndex());
            assertThat(audit.labelStartIndex()).isEqualTo(audit.decisionIndex() + 1);
        }

        assertThat(second.snapshots().size()).isEqualTo(first.snapshots().size());
        assertThat(second.globalMetricsByHorizon()).isEqualTo(first.globalMetricsByHorizon());
        assertThat(second.foldMetricsByHorizon()).isEqualTo(first.foldMetricsByHorizon());
    }

    @Test
    void engineExposesFoldBoundedLabelWindowSkips() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(120)).build();
        WalkForwardConfig config = new WalkForwardConfig(60, 20, 20, 0, 0, 0, 15, List.of(), 1, List.of(), 1L);

        WalkForwardEngine<String, String, Boolean> engine = new WalkForwardEngine<>(
                new AnchoredExpandingWalkForwardSplitter(),
                (fullSeries, decisionIndex,
                        context) -> List.of(new RankedPrediction<>("p", 1, fullSeries.numFactory().numOf(0.5),
                                fullSeries.numFactory().numOf(0.5), "p")),
                (fullSeries, decisionIndex, horizonBars, prediction) -> true,
                List.of(WalkForwardMetric.agreement("agreement", 1, (prediction, outcome) -> outcome)));

        WalkForwardRunResult<String, Boolean> result = engine.run(series, "ctx", config);

        long skipped = result.leakageAudit().stream().filter(audit -> !audit.withinFoldBounds()).count();
        assertThat(skipped).isGreaterThan(0);
        assertThat(result.observationsByHorizon().get(15)).isNotEmpty();
    }

    private static double[] prices(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + (i * 0.5);
        }
        return prices;
    }
}
