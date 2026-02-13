/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AnchoredVWAPIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    /**
     * Creates a new AnchoredVWAPIndicatorTest instance.
     */
    public AnchoredVWAPIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    /**
     * Initializes the test fixtures used by these scenarios.
     */
    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(100).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(200).add();
        series.barBuilder().openPrice(12).closePrice(12).highPrice(12).lowPrice(12).volume(150).add();
        series.barBuilder().openPrice(13).closePrice(13).highPrice(13).lowPrice(13).volume(300).add();
        series.barBuilder().openPrice(14).closePrice(14).highPrice(14).lowPrice(14).volume(250).add();
    }

    /**
     * Implements anchored from fixed index.
     */
    @Test
    public void anchoredFromFixedIndex() {
        var anchored = new AnchoredVWAPIndicator(series, 2);

        assertThat(anchored.getAnchorIndex(4)).isEqualTo(2);
        assertThat(anchored.getWindowStartIndex(4)).isEqualTo(2);
        assertNumEquals((12 * 150 + 13 * 300 + 14 * 250) / 700d, anchored.getValue(4));
    }

    /**
     * Implements anchor resets on signal.
     */
    @Test
    public void anchorResetsOnSignal() {
        var signal = new AnchorSignal(series, 0, 0, 3);
        var anchored = new AnchoredVWAPIndicator(series, signal);

        assertThat(anchored.getAnchorIndex(2)).isEqualTo(0);
        assertNumEquals((10 * 100 + 11 * 200 + 12 * 150) / 450d, anchored.getValue(2));

        assertThat(anchored.getAnchorIndex(3)).isEqualTo(3);
        assertNumEquals(13d, anchored.getValue(3));

        assertThat(anchored.getAnchorIndex(4)).isEqualTo(3);
        assertNumEquals((13 * 300 + 14 * 250) / 550d, anchored.getValue(4));
    }

    /**
     * Implements clamps anchor before series start.
     */
    @Test
    public void clampsAnchorBeforeSeriesStart() {
        var anchored = new AnchoredVWAPIndicator(series, -5);

        assertThat(anchored.getAnchorIndex(0)).isEqualTo(series.getBeginIndex());
        assertNumEquals(10d, anchored.getValue(0));
    }

    /**
     * Implements rejects anchor signal from different series.
     */
    @Test
    public void rejectsAnchorSignalFromDifferentSeries() {
        var otherSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        otherSeries.barBuilder().openPrice(1).closePrice(1).highPrice(1).lowPrice(1).volume(1).add();
        var alienSignal = new AnchorSignal(otherSeries, 0, 0);

        assertThatThrownBy(() -> new AnchoredVWAPIndicator(series, alienSignal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bar series");
    }

    /**
     * Verifies that round trip serialize and deserialize.
     */
    @Test
    public void shouldRoundTripSerializeAndDeserialize() {
        var anchored = new AnchoredVWAPIndicator(series, 1);

        String json = anchored.toJson();
        Indicator<?> restored = Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(AnchoredVWAPIndicator.class);
        var restoredIndicator = (AnchoredVWAPIndicator) restored;
        assertThat(restoredIndicator.toDescriptor()).isEqualTo(anchored.toDescriptor());
        assertThat(restoredIndicator.getCountOfUnstableBars()).isEqualTo(anchored.getCountOfUnstableBars());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restoredIndicator.getValue(i)).isEqualByComparingTo(anchored.getValue(i));
            assertThat(restoredIndicator.getAnchorIndex(i)).isEqualTo(anchored.getAnchorIndex(i));
        }
    }

    /**
     * Verifies that unstable bars track input warmup.
     */
    @Test
    public void unstableBarsTrackInputWarmup() {
        BarSeries syntheticSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 1, 1, 1, 1, 1)
                .build();
        MockIndicator price = new MockIndicator(syntheticSeries, 1,
                List.of(numOf(10), numOf(11), numOf(12), numOf(13), numOf(14), numOf(15)));
        MockIndicator volume = new MockIndicator(syntheticSeries, 4, List.of(numFactory.one(), numFactory.one(),
                numFactory.one(), numFactory.one(), numFactory.one(), numFactory.one()));
        AnchoredVWAPIndicator indicator = new AnchoredVWAPIndicator(price, volume, 0);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(indicator.getValue(3).isNaN()).isTrue();
        assertNumEquals(12, indicator.getValue(4));
    }

    /**
     * Verifies that unstable bars include anchor signal warmup.
     */
    @Test
    public void unstableBarsIncludeAnchorSignalWarmup() {
        BarSeries syntheticSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 1, 1, 1, 1, 1)
                .build();
        MockIndicator price = new MockIndicator(syntheticSeries, 1,
                List.of(numOf(10), numOf(11), numOf(12), numOf(13), numOf(14), numOf(15)));
        MockIndicator volume = new MockIndicator(syntheticSeries, 4, List.of(numFactory.one(), numFactory.one(),
                numFactory.one(), numFactory.one(), numFactory.one(), numFactory.one()));
        Indicator<Boolean> signal = new AnchorSignal(syntheticSeries, 5, 2);

        AnchoredVWAPIndicator indicator = new AnchoredVWAPIndicator(price, volume, signal, 0);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(5);
    }

    private static final class AnchorSignal implements Indicator<Boolean> {

        private final BarSeries series;
        private final Set<Integer> anchors;
        private final int unstableBars;

        /**
         * Implements anchor signal.
         */
        private AnchorSignal(BarSeries series, int unstableBars, int... anchorIndexes) {
            this.series = series;
            this.unstableBars = unstableBars;
            this.anchors = new HashSet<>();
            for (int index : anchorIndexes) {
                anchors.add(index);
            }
        }

        /**
         * Returns the value at the requested index.
         */
        @Override
        public Boolean getValue(int index) {
            return anchors.contains(index);
        }

        /**
         * Returns the bar series bound to this indicator.
         */
        @Override
        public BarSeries getBarSeries() {
            return series;
        }

        /**
         * Returns the number of unstable bars required before values become reliable.
         */
        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }
}
