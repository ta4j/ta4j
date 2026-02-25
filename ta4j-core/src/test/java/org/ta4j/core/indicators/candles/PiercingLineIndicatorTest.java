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
import org.ta4j.core.indicators.trend.DownTrendIndicator;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PiercingLineIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public PiercingLineIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withBars(generateDowntrend()).build();
    }

    @Test
    public void shouldDetectPatternWhenAllConditionsAreSatisfied() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(22).add();
        series.barBuilder().openPrice(22).closePrice(27).highPrice(28).lowPrice(21).add();

        final PiercingLineIndicator indicator = new PiercingLineIndicator(series);

        assertThat(indicator.getValue(18)).isTrue();
    }

    @Test
    public void shouldReturnFalseBeforeUnstableBoundary() {
        final PiercingLineIndicator indicator = new PiercingLineIndicator(series);
        final int expectedUnstableBars = Math.max(1, new DownTrendIndicator(series).getCountOfUnstableBars());

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(expectedUnstableBars);
        assertThat(indicator.getValue(expectedUnstableBars - 1)).isFalse();
    }

    @Test
    public void shouldNotDetectPatternWhenSecondCandleDoesNotGapDown() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(22).add();
        series.barBuilder().openPrice(23).closePrice(27).highPrice(28).lowPrice(22).add();

        final PiercingLineIndicator indicator = new PiercingLineIndicator(series);

        assertThat(indicator.getValue(18)).isFalse();
    }

    @Test
    public void shouldRespectConfiguredGapThreshold() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(22).add();
        series.barBuilder().openPrice(22).closePrice(27).highPrice(28).lowPrice(21).add();

        final PiercingLineIndicator indicator = new PiercingLineIndicator(series, numFactory.numOf(0.03),
                numFactory.numOf(0.05), numFactory.numOf(0.5));

        assertThat(indicator.getValue(18)).isFalse();
    }

    @Test
    public void shouldDetectPatternWhenGapMeetsConfiguredThreshold() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(22).add();
        series.barBuilder().openPrice(21).closePrice(27).highPrice(28).lowPrice(20).add();

        final PiercingLineIndicator indicator = new PiercingLineIndicator(series, numFactory.numOf(0.03),
                numFactory.numOf(0.08), numFactory.numOf(0.5));

        assertThat(indicator.getValue(18)).isTrue();
    }

    @Test
    public void shouldNotDetectPatternWhenCloseEqualsPenetrationBoundary() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(22).add();
        series.barBuilder().openPrice(22).closePrice(26).highPrice(27).lowPrice(21).add();

        final PiercingLineIndicator indicator = new PiercingLineIndicator(series);

        assertThat(indicator.getValue(18)).isFalse();
    }

    @Test
    public void shouldDetectPatternWithEqualRealBodies() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(22).add();
        series.barBuilder().openPrice(22).closePrice(28).highPrice(28).lowPrice(21).add();

        final PiercingLineIndicator indicator = new PiercingLineIndicator(series);

        assertThat(indicator.getValue(18)).isTrue();
    }

    @Test
    public void shouldRejectNullConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new PiercingLineIndicator(null));
        assertThrows(NullPointerException.class,
                () -> new PiercingLineIndicator(series, null, numFactory.zero(), numFactory.numOf(0.5)));
        assertThrows(NullPointerException.class,
                () -> new PiercingLineIndicator(series, numFactory.numOf(0.03), null, numFactory.numOf(0.5)));
        assertThrows(NullPointerException.class,
                () -> new PiercingLineIndicator(series, numFactory.numOf(0.03), numFactory.zero(), null));
    }

    @Test
    public void shouldNotDetectPatternWhenBullishCloseTouchesFirstOpen() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(22).add();
        series.barBuilder().openPrice(22).closePrice(29).highPrice(30).lowPrice(21).add();

        final PiercingLineIndicator indicator = new PiercingLineIndicator(series);

        assertThat(indicator.getValue(18)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRoundTripSerializeAndDeserialize() {
        series.barBuilder().openPrice(29).closePrice(23).highPrice(29).lowPrice(22).add();
        series.barBuilder().openPrice(21).closePrice(27).highPrice(28).lowPrice(20).add();
        series.barBuilder().openPrice(27).closePrice(30).highPrice(31).lowPrice(26).add();

        final PiercingLineIndicator original = new PiercingLineIndicator(series, numFactory.numOf(0.03),
                numFactory.numOf(0.08), numFactory.numOf(0.5));
        final String json = original.toJson();
        final Indicator<Boolean> restored = (Indicator<Boolean>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(PiercingLineIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(original.getValue(i));
        }
    }

    private List<Bar> generateDowntrend() {
        final List<Bar> bars = new ArrayList<>(30);
        for (int i = 46; i > 29; --i) {
            bars.add(
                    new MockBarBuilder(numFactory).openPrice(i).closePrice(i - 6).highPrice(i).lowPrice(i - 8).build());
        }
        return bars;
    }
}
