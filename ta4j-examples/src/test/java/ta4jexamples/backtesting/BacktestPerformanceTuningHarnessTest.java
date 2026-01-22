/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

class BacktestPerformanceTuningHarnessTest {

    @Test
    void sliceToLastBarsReturnsSameInstanceWhenNoTruncationRequested() {
        BarSeries series = buildSeries(50);

        assertSame(series, BacktestPerformanceTuningHarness.sliceToLastBars(series, 0),
                "barCount=0 should keep full series");
        assertSame(series, BacktestPerformanceTuningHarness.sliceToLastBars(series, 50),
                "barCount==available bars should keep full series");
        assertSame(series, BacktestPerformanceTuningHarness.sliceToLastBars(series, 500),
                "barCount>available bars should keep full series");
    }

    @Test
    void sliceToLastBarsTruncatesToRequestedSizeAndPreservesLastBar() {
        BarSeries series = buildSeries(100);
        Num expectedLastClose = series.getLastBar().getClosePrice();

        BarSeries sliced = BacktestPerformanceTuningHarness.sliceToLastBars(series, 10);

        assertNotSame(series, sliced, "Sliced series should be a new instance");
        assertEquals(10, sliced.getBarCount(), "Sliced series should expose requested number of bars");
        assertEquals(expectedLastClose, sliced.getLastBar().getClosePrice(), "Sliced series should preserve last bar");
    }

    @Test
    void applyMaximumBarCountHintOverridesMaximumBarCountWithoutMutatingDelegate() {
        BarSeries series = buildSeries(20);

        assertSame(series, BacktestPerformanceTuningHarness.applyMaximumBarCountHint(series, 0),
                "hint=0 should be a no-op");

        BarSeries wrapped = BacktestPerformanceTuningHarness.applyMaximumBarCountHint(series, 123);
        assertEquals(123, wrapped.getMaximumBarCount(), "Wrapper should override BarSeries.getMaximumBarCount");
        assertEquals(series.getBarCount(), wrapped.getBarCount(), "Wrapper should preserve bar count");
        assertEquals(series.getLastBar().getClosePrice(), wrapped.getLastBar().getClosePrice(),
                "Wrapper should delegate bar access");
        assertThrows(UnsupportedOperationException.class, () -> wrapped.setMaximumBarCount(1),
                "Hint wrapper should not allow mutating maximum bar count");
    }

    @Test
    void createStrategiesRespectsRequestedCount() {
        BarSeries series = buildSeries(500);

        List<Strategy> strategies = BacktestPerformanceTuningHarness.createStrategies(series, 5);

        assertEquals(5, strategies.size(), "Should only create the requested number of strategies");
        assertTrue(strategies.stream().allMatch(strategy -> strategy != null),
                "Strategies should not contain null entries");
        assertThrows(IllegalArgumentException.class, () -> BacktestPerformanceTuningHarness.createStrategies(series, 0),
                "Zero strategies is not a meaningful request");
    }

    @Test
    void isNonLinearReturnsFalseForRoughlyLinearScaling() {
        Thresholds thresholds = new Thresholds(0.25d, 1.25d);

        RunResult previous = sampleRun(1_000L, Duration.ofSeconds(10), Duration.ofSeconds(1));
        RunResult current = sampleRun(1_300L, Duration.ofSeconds(13), Duration.ofSeconds(1));

        assertFalse(BacktestPerformanceTuningHarness.isNonLinear(previous, current, thresholds),
                "Near-linear scaling should not be flagged as non-linear");
    }

    @Test
    void isNonLinearFlagsGcDominatedRuns() {
        Thresholds thresholds = new Thresholds(0.25d, 1.25d);

        RunResult previous = sampleRun(1_000L, Duration.ofSeconds(10), Duration.ofSeconds(1));
        RunResult current = sampleRun(1_300L, Duration.ofSeconds(13), Duration.ofSeconds(4));

        assertTrue(BacktestPerformanceTuningHarness.isNonLinear(previous, current, thresholds),
                "Runs dominated by GC time should be flagged as non-linear");
    }

    @Test
    void isNonLinearFlagsSlowdownBeyondThreshold() {
        Thresholds thresholds = new Thresholds(0.25d, 1.25d);

        RunResult previous = sampleRun(1_000L, Duration.ofSeconds(10), Duration.ofSeconds(1));
        RunResult current = sampleRun(1_300L, Duration.ofSeconds(25), Duration.ofSeconds(1));

        assertTrue(BacktestPerformanceTuningHarness.isNonLinear(previous, current, thresholds),
                "Runs that slow down beyond the threshold should be flagged as non-linear");
    }

    @Test
    void selectBestRecommendationPrefersHighestWorkUnits() {
        RunResult smaller = sampleRun(1_000L, Duration.ofSeconds(10), Duration.ofSeconds(1));
        RunResult larger = sampleRun(5_000L, Duration.ofSeconds(20), Duration.ofSeconds(2));

        List<VariantTuningResult> results = List.of(new VariantTuningResult(new SeriesVariant(500, 0), smaller, null),
                new VariantTuningResult(new SeriesVariant(2000, 0), larger, null));

        assertEquals(larger, BacktestPerformanceTuningHarness.selectBestRecommendation(results),
                "Best recommendation should be the last linear run with highest work units");
    }

    private RunResult sampleRun(long workUnits, Duration runtime, Duration gcTime) {
        BacktestRuntimeStats runtimeStats = new BacktestRuntimeStats(runtime, Duration.ZERO, Duration.ZERO,
                Duration.ZERO, Duration.ZERO, "{}");
        HeapSnapshot heap = new HeapSnapshot(1024L * 1024L, 1024L * 1024L, 512L * 1024L);
        GcSnapshot gcDelta = new GcSnapshot(0L, gcTime);
        return new RunResult(ExecutionMode.FULL_RESULT, 1, 1, 0, Integer.MAX_VALUE, 0, Duration.ZERO, runtimeStats,
                workUnits, gcDelta, heap, heap, "MockNumFactory");
    }

    private BarSeries buildSeries(int barCount) {
        double[] data = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            data[i] = i + 1d;
        }
        return new MockBarSeriesBuilder().withData(data).build();
    }
}
