/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.trend.UpTrendIndicator;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DarkCloudCoverIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public DarkCloudCoverIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withBars(generateUptrend()).build();
    }

    @Test
    public void shouldDetectPatternWhenAllConditionsAreSatisfied() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(27).closePrice(20).highPrice(28).lowPrice(19).add();

        final DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series);

        assertThat(indicator.getValue(18)).isTrue();
    }

    @Test
    public void shouldReturnFalseBeforeUnstableBoundary() {
        final DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series);
        final int expectedUnstableBars = Math.max(1, new UpTrendIndicator(series).getCountOfUnstableBars());

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(expectedUnstableBars);
        assertThat(indicator.getValue(expectedUnstableBars - 1)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenSecondCandleDoesNotGapUp() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(25).closePrice(20).highPrice(26).lowPrice(19).add();

        final DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series);

        assertThat(indicator.getValue(18)).isFalse();
    }

    @Test
    public void shouldRespectConfiguredGapThreshold() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(27).closePrice(20).highPrice(28).lowPrice(19).add();

        final DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series, numFactory.numOf(0.03),
                numFactory.numOf(0.09), numFactory.numOf(0.5));

        assertThat(indicator.getValue(18)).isFalse();
    }

    @Test
    public void shouldDetectPatternWhenGapMeetsConfiguredThreshold() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(27.5).closePrice(20).highPrice(28).lowPrice(19).add();

        final DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series, numFactory.numOf(0.03),
                numFactory.numOf(0.1), numFactory.numOf(0.5));

        assertThat(indicator.getValue(18)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenCloseEqualsPenetrationBoundary() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(27).closePrice(21).highPrice(28).lowPrice(20).add();

        final DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series);

        assertThat(indicator.getValue(18)).isFalse();
    }

    @Test
    public void shouldDetectPatternWithEqualRealBodies() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(27).closePrice(19).highPrice(28).lowPrice(18).add();

        final DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series);

        assertThat(indicator.getValue(18)).isTrue();
    }

    @Test
    public void shouldRejectNullConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new DarkCloudCoverIndicator(null));
        assertThrows(NullPointerException.class,
                () -> new DarkCloudCoverIndicator(series, null, numFactory.zero(), numFactory.numOf(0.5)));
        assertThrows(NullPointerException.class,
                () -> new DarkCloudCoverIndicator(series, numFactory.numOf(0.03), null, numFactory.numOf(0.5)));
        assertThrows(NullPointerException.class,
                () -> new DarkCloudCoverIndicator(series, numFactory.numOf(0.03), numFactory.zero(), null));
    }

    @Test
    public void shouldNotDetectPatternWhenBearishCloseTouchesFirstOpen() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(27).closePrice(17).highPrice(28).lowPrice(16).add();

        final DarkCloudCoverIndicator indicator = new DarkCloudCoverIndicator(series);

        assertThat(indicator.getValue(18)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRoundTripSerializeAndDeserialize() {
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(27.5).closePrice(20).highPrice(28).lowPrice(19).add();
        series.barBuilder().openPrice(20).closePrice(18).highPrice(21).lowPrice(17).add();

        final DarkCloudCoverIndicator original = new DarkCloudCoverIndicator(series, numFactory.numOf(0.03),
                numFactory.numOf(0.1), numFactory.numOf(0.5));
        final String json = original.toJson();
        final Indicator<Boolean> restored = (Indicator<Boolean>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(DarkCloudCoverIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(original.getValue(i));
        }
    }

    private List<Bar> generateUptrend() {
        final List<Bar> bars = new ArrayList<>(30);
        for (int i = 0; i < 17; ++i) {
            bars.add(
                    new MockBarBuilder(numFactory).openPrice(i).closePrice(i + 6).highPrice(i + 8).lowPrice(i).build());
        }
        return bars;
    }
}
