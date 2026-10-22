/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockBarBuilderFactory;

class ProminenceSwingDetectorTest {

    @Test
    void factoryBuildsConfiguredProminenceDetector() {
        ProminenceSwingConfig config = new ProminenceSwingConfig(3, 1, 0, 1, 0.0);
        SwingDetector detector = SwingDetectors.prominence(config);

        assertThat(detector).isInstanceOf(ProminenceSwingDetector.class);
        assertThat(((ProminenceSwingDetector) detector).getConfig()).isEqualTo(config);
    }

    @Test
    void detectsAlternatingProminentHighsAndLows() {
        BarSeries series = series(10, 20, 10, 20, 10);
        SwingDetector detector = SwingDetectors.prominence(new ProminenceSwingConfig(3, 1, 0, 1, 0.0));

        SwingDetectorResult result = detector.detect(series, series.getEndIndex(), ElliottDegree.MINOR);

        assertThat(result.pivots()).extracting(SwingPivot::index).containsExactly(1, 2, 3);
        assertThat(result.pivots()).extracting(SwingPivot::type)
                .containsExactly(SwingPivotType.HIGH, SwingPivotType.LOW, SwingPivotType.HIGH);
        assertThat(detector.detectPivots(series, series.getEndIndex())).isEqualTo(result.pivots());
    }

    @Test
    void rejectsLocalExtremaBelowTheProminenceThreshold() {
        BarSeries series = series(10, 12, 10);
        SwingDetector detector = SwingDetectors.prominence(new ProminenceSwingConfig(2, 1, 0, 1, 2.0));

        SwingDetectorResult result = detector.detect(series, series.getEndIndex(), ElliottDegree.MINOR);

        assertThat(result.pivots()).isEmpty();
    }

    @Test
    void supportedRevisionDoesNotScanOrCopyRetainedBarsOnRepeatedDetection() {
        double[] closes = new double[80];
        for (int index = 0; index < closes.length; index++) {
            closes[index] = index % 2 == 0 ? 10 : 20;
        }
        CountingSeries series = new CountingSeries(series(closes).getBarData());
        ProminenceSwingConfig config = new ProminenceSwingConfig(3, 1, 0, 1, 0.0);
        ProminenceSwingDetector detector = new ProminenceSwingDetector(config);
        SwingDetectorResult initial = detector.detect(series, series.getEndIndex(), ElliottDegree.MINOR);
        series.barReads = 0;
        series.copiedBars = 0;

        assertThat(detector.detect(series, series.getEndIndex(), ElliottDegree.MINOR)).isEqualTo(initial);

        assertThat(series.barReads).isLessThan(20);
        assertThat(series.copiedBars).isZero();
    }

    @Test
    void rebuildsAfterRetainedValueMutationWithoutRevisionOrIdentityChange() {
        assertRetainedMutationIsRebuilt(false);
    }

    @Test
    void rebuildsAfterRetainedValueMutationFollowedByAppend() {
        assertRetainedMutationIsRebuilt(true);
    }

    @Test
    void revalidatesRetainedValuesAfterUnlockedEvaluation() {
        RevisionlessSeries series = new RevisionlessSeries(series(10, 20, 10, 20, 10, 20, 10).getBarData());
        ProminenceSwingConfig config = new ProminenceSwingConfig(3, 1, 0, 1, 0.0);
        ProminenceSwingDetector warmed = new ProminenceSwingDetector(config);
        warmed.detect(series, 0, ElliottDegree.MINOR);
        SwingDetectorResult before = new ProminenceSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.MINOR);
        Bar interior = series.getBar(2);
        series.mutateOnUnlockedRead(4, () -> interior.addPrice(series.numFactory().numOf(50)));

        SwingDetectorResult result = warmed.detect(series, series.getEndIndex(), ElliottDegree.MINOR);
        SwingDetectorResult fresh = new ProminenceSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.MINOR);

        assertThat(series.mutationTriggered).isTrue();
        assertThat(fresh).isNotEqualTo(before);
        assertThat(result).isEqualTo(fresh);
    }

    private void assertRetainedMutationIsRebuilt(final boolean append) {
        BarSeries series = new RevisionlessSeries(series(10, 20, 10, 20, 10, 20, 10).getBarData());
        ProminenceSwingConfig config = new ProminenceSwingConfig(3, 1, 0, 1, 0.0);
        ProminenceSwingDetector warmed = new ProminenceSwingDetector(config);
        SwingDetectorResult before = warmed.detect(series, series.getEndIndex(), ElliottDegree.MINOR);
        Bar interior = series.getBar(3);
        Bar terminal = series.getLastBar();
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();

        interior.addPrice(series.numFactory().numOf(40));

        assertThat(series.getBar(3)).isSameAs(interior);
        assertThat(series.getLastBar()).isSameAs(terminal);
        assertThat(series.getBeginIndex()).isEqualTo(begin);
        assertThat(series.getEndIndex()).isEqualTo(end);
        assertThat(series.getBarHistoryRevision()).isEqualTo(-1L);
        if (append) {
            series.barBuilder().openPrice(20).highPrice(20).lowPrice(20).closePrice(20).volume(1).add();
        }
        SwingDetectorResult fresh = new ProminenceSwingDetector(config).detect(series, series.getEndIndex(),
                ElliottDegree.MINOR);
        assertThat(fresh).isNotEqualTo(before);
        assertThat(warmed.detect(series, series.getEndIndex(), ElliottDegree.MINOR)).isEqualTo(fresh);
    }

    private static final class CountingSeries extends BaseBarSeries {

        private int barReads;
        private int copiedBars;

        private CountingSeries(final List<Bar> bars) {
            super("counted-prominence", new ArrayList<>(bars));
        }

        @Override
        public Bar getBar(final int index) {
            barReads++;
            return super.getBar(index);
        }

        @Override
        public List<Bar> getBarData() {
            copiedBars += getBarCount();
            return super.getBarData();
        }
    }

    private static final class RevisionlessSeries extends BaseBarSeries {

        private int readScopeDepth;
        private int triggerIndex = -1;
        private Runnable mutation;
        private boolean mutationTriggered;

        private void mutateOnUnlockedRead(final int index, final Runnable action) {
            triggerIndex = index;
            mutation = action;
        }

        @Override
        public Bar getBar(final int index) {
            if (index == triggerIndex && mutation != null && readScopeDepth == 0) {
                Runnable action = mutation;
                mutation = null;
                mutationTriggered = true;
                action.run();
            }
            return super.getBar(index);
        }

        @Override
        public void withReadLock(final Runnable action) {
            readScopeDepth++;
            try {
                action.run();
            } finally {
                readScopeDepth--;
            }
        }

        @Override
        public <T> T withReadLock(final Supplier<T> action) {
            readScopeDepth++;
            try {
                return action.get();
            } finally {
                readScopeDepth--;
            }
        }

        private RevisionlessSeries(final List<Bar> bars) {
            super("revisionless-prominence", new ArrayList<>(bars));
        }

        @Override
        public BarBuilder barBuilder() {
            return new MockBarBuilderFactory().createBarBuilder(this);
        }

        @Override
        public synchronized long getBarHistoryRevision() {
            return -1L;
        }

        @Override
        public synchronized BarSeriesChangeSnapshot getBarSeriesChangeSnapshot(final long sinceRevision) {
            return new BarSeriesChangeSnapshot(-1L, -1, getRemovedBarsCount() - 1, getMaximumBarCount(), getEndIndex());
        }

    }

    private BarSeries series(final double... closes) {
        BarSeries series = new MockBarSeriesBuilder().build();
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(1).add();
        }
        return series;
    }
}
