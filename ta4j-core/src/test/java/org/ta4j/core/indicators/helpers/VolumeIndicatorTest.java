/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VolumeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public VolumeIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void indicatorShouldRetrieveBarVolume() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        var volumeIndicator = new VolumeIndicator(series);
        for (int i = 0; i < 10; i++) {
            assertEquals(volumeIndicator.getValue(i), series.getBar(i).getVolume());
        }
    }

    @Test
    public void sumOfVolume() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(0).volume(10).add();
        series.barBuilder().closePrice(0).volume(11).add();
        series.barBuilder().closePrice(0).volume(12).add();
        series.barBuilder().closePrice(0).volume(13).add();
        series.barBuilder().closePrice(0).volume(150).add();
        series.barBuilder().closePrice(0).volume(155).add();
        series.barBuilder().closePrice(0).volume(160).add();

        var volumeIndicator = new VolumeIndicator(series, 3);

        assertNumEquals(10, volumeIndicator.getValue(0));
        assertNumEquals(21, volumeIndicator.getValue(1));
        assertNumEquals(33, volumeIndicator.getValue(2));
        assertNumEquals(36, volumeIndicator.getValue(3));
        assertNumEquals(175, volumeIndicator.getValue(4));
        assertNumEquals(318, volumeIndicator.getValue(5));
        assertNumEquals(465, volumeIndicator.getValue(6));
    }

    @Test
    public void partialSumsProduceCorrectOutputWithRandomAccessOrder() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(0).volume(10).add();
        series.barBuilder().closePrice(0).volume(11).add();
        series.barBuilder().closePrice(0).volume(12).add();
        series.barBuilder().closePrice(0).volume(13).add();
        series.barBuilder().closePrice(0).volume(150).add();
        series.barBuilder().closePrice(0).volume(155).add();
        series.barBuilder().closePrice(0).volume(160).add();

        var volumeIndicator = new VolumeIndicator(series, 3);

        assertNumEquals(465, volumeIndicator.getValue(6));
        assertNumEquals(36, volumeIndicator.getValue(3));
        assertNumEquals(21, volumeIndicator.getValue(1));
        assertNumEquals(318, volumeIndicator.getValue(5));
        assertNumEquals(10, volumeIndicator.getValue(0));
        assertNumEquals(175, volumeIndicator.getValue(4));
        assertNumEquals(33, volumeIndicator.getValue(2));
    }

    @Test
    public void singleBarAndBarCountOne() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(100).volume(42).add();

        var volumeIndicator = new VolumeIndicator(series, 1);
        assertNumEquals(42, volumeIndicator.getValue(0));
    }

    @Test
    public void largeWindowBarCount() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 20; i++) {
            series.barBuilder().closePrice(i).volume(i + 1).add();
        }

        var volumeIndicator = new VolumeIndicator(series, 10);
        Num expected = numFactory.zero();
        for (int i = 10; i <= 19; i++) {
            expected = expected.plus(numFactory.numOf(i + 1));
        }
        assertNumEquals(expected, volumeIndicator.getValue(19));
    }

    @Test
    public void warmupAtEndIndexDoesNotOverflowOnColdCache() {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 35_041; i++) {
            series.barBuilder().closePrice(i).volume(i + 1).add();
        }

        final VolumeIndicator volumeIndicator = new VolumeIndicator(series);
        final int endIndex = series.getEndIndex();

        assertNumEquals(series.getBar(endIndex).getVolume(), volumeIndicator.getValue(endIndex));
    }

    @Test
    public void partialHeadAdvancePrefillsCacheHoleWithoutRecursiveOverflow() {
        final int barCount = 10_000;
        final int fullLength = barCount + 100;
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < fullLength; i++) {
            series.barBuilder().closePrice(0).volume(1).add();
        }
        final VolumeIndicator volumeIndicator = new VolumeIndicator(series, barCount);
        volumeIndicator.getValue(series.getEndIndex());

        // Retain the cached tail but evict the initial recursive base range.
        series.setMaximumBarCount(fullLength - 1);
        assertEquals(1, series.getBeginIndex());

        // Index 9_999 lies below the retained cache tail. It must be rebuilt
        // iteratively from the new begin index rather than recursively walking
        // ten thousand values back toward it.
        assertNumEquals(barCount - 1, volumeIndicator.getValue(barCount - 1));
    }

    @Test
    public void retainsCachedSuffixAfterHeadAdvance() {
        final int length = 128;
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(length);
        for (int index = 0; index < length; index++) {
            series.barBuilder().closePrice(index).volume(index + 1).add();
        }
        final java.util.concurrent.atomic.AtomicInteger calculations = new java.util.concurrent.atomic.AtomicInteger();
        final VolumeIndicator volumeIndicator = new VolumeIndicator(series) {
            @Override
            protected Num calculate(int index) {
                calculations.incrementAndGet();
                return super.calculate(index);
            }
        };
        volumeIndicator.getValue(series.getEndIndex());
        calculations.set(0);

        series.barBuilder().closePrice(length).volume(length + 1).add();

        assertNumEquals(length + 1, volumeIndicator.getValue(series.getEndIndex()));
        assertTrue(calculations.get() < 4);
    }

    @Test
    public void rollingSumRespectsBeginIndexAfterConstrainedEviction() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withMaxBarCount(3).build();
        series.barBuilder().closePrice(0).volume(10).add(); // index 0
        series.barBuilder().closePrice(0).volume(11).add(); // index 1
        series.barBuilder().closePrice(0).volume(12).add(); // index 2
        series.barBuilder().closePrice(0).volume(13).add(); // index 3 (index 0 removed)

        var volumeIndicator = new VolumeIndicator(series, 2);

        assertEquals(1, series.getBeginIndex());
        assertNumEquals(11, volumeIndicator.getValue(1));
        assertNumEquals(23, volumeIndicator.getValue(2));
        assertNumEquals(25, volumeIndicator.getValue(3));
    }

    /**
     * Regression: after a head advance on a bounded series, reads inside the
     * evicted unstable band must be rebuilt iteratively. A fixed trailing window
     * larger than the recursion prefill threshold would otherwise recurse one entry
     * per bar from the read index down to the cached suffix, which stack overflows
     * for large windows. The series keeps {@code barCount + 2} bars so that the
     * cached suffix survives the advance and the band below it is genuinely evicted
     * (with {@code barCount + 1} kept bars the floor clears the whole cache, which
     * prefills normally and masks the recursion).
     */
    @Test
    public void headAdvanceEvictsUnstableBandWithoutStackOverflow() {
        final int barCount = 50000;
        final int totalBars = 52000;
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(barCount + 2);
        for (int i = 0; i < totalBars; i++) {
            series.barBuilder().openPrice(i).closePrice(i).highPrice(i).lowPrice(i).volume(100).add();
        }
        VolumeIndicator volume = new VolumeIndicator(series, barCount);
        for (int i = series.getBeginIndex(); i < totalBars; i++) {
            volume.getValue(i);
        }
        series.barBuilder()
                .openPrice(totalBars)
                .closePrice(totalBars)
                .highPrice(totalBars)
                .lowPrice(totalBars)
                .volume(100)
                .add();
        assertEquals(1999, series.getBeginIndex());
        // Index 40000 sits deep inside the evicted band [1999, 51997] while the
        // suffix [51998, 51999] stays cached: the read must rebuild the band
        // iteratively and return the window sum over the retained bars only.
        assertNumEquals(3800200, volume.getValue(40000));
    }
}
