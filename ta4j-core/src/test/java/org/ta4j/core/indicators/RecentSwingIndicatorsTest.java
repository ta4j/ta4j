/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecentSwingIndicators.Confirmation;
import org.ta4j.core.indicators.RecentSwingIndicators.Method;
import org.ta4j.core.indicators.RecentSwingIndicators.Pair;
import org.ta4j.core.indicators.RecentSwingIndicators.SwingPoint;
import org.ta4j.core.indicators.elliott.swing.SwingDetector;
import org.ta4j.core.indicators.elliott.swing.SwingDetectorResult;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;
import org.ta4j.core.indicators.elliott.swing.SwingPivot;
import org.ta4j.core.indicators.elliott.swing.SwingPivotType;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingHighIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RecentSwingIndicatorsTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public RecentSwingIndicatorsTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldUseAtrZigZagAsCanonicalDefault() {
        final Pair pair = RecentSwingIndicators.defaultFor(seriesFromCloses(10, 12, 9, 14, 8));

        assertThat(pair.highs()).isInstanceOf(RecentZigZagSwingHighIndicator.class);
        assertThat(pair.lows()).isInstanceOf(RecentZigZagSwingLowIndicator.class);
        assertThat(pair.method()).isEqualTo(Method.ZIGZAG);
    }

    @Test
    public void shouldRetainFactoryMethodProvenance() {
        final BarSeries series = seriesFromCloses(10, 12, 9, 14, 8, 15, 7);

        assertThat(List.of(RecentSwingIndicators.fractal(series).method(),
                RecentSwingIndicators.zigZag(series).method(), RecentSwingIndicators.adaptiveZigZag(series).method(),
                RecentSwingIndicators.slopeChange(series).method(), RecentSwingIndicators.prominence(series).method(),
                RecentSwingIndicators.consensus(series).method())).containsExactly(Method.FRACTAL, Method.ZIGZAG,
                        Method.ADAPTIVE_ZIGZAG, Method.SLOPE_CHANGE, Method.PROMINENCE, Method.CONSENSUS);
    }

    @Test
    public void shouldExposeDevelopingTerminalExtremeWithoutChangingConfirmedIndicators() {
        final BarSeries series = seriesFromCloses(10, 8, 10, 12, 15);
        final Pair pair = RecentSwingIndicators.fractal(series, 1, 1, 0);

        final SwingPoint provisionalHigh = pair.latestHigh(4).orElseThrow();
        assertThat(provisionalHigh.pivotIndex()).isEqualTo(4);
        assertThat(provisionalHigh.confirmationIndex()).isEqualTo(-1);
        assertThat(provisionalHigh.price()).isEqualByComparingTo(numOf(15));
        assertThat(provisionalHigh.type()).isEqualTo(SwingPivotType.HIGH);
        assertThat(provisionalHigh.confirmation()).isEqualTo(Confirmation.PROVISIONAL);
        assertThat(pair.highs().getLatestSwingIndex(4)).isEqualTo(-1);

        series.barBuilder().openPrice(13).highPrice(13).lowPrice(13).closePrice(13).add();

        final SwingPoint confirmedHigh = pair.latestHigh(5).orElseThrow();
        assertThat(confirmedHigh.pivotIndex()).isEqualTo(4);
        assertThat(confirmedHigh.confirmationIndex()).isEqualTo(5);
        assertThat(confirmedHigh.confirmation()).isEqualTo(Confirmation.CONFIRMED);
        assertThat(pair.formingPoint(5)).get().extracting(SwingPoint::type).isEqualTo(SwingPivotType.LOW);
    }

    @Test
    public void shouldAdaptDetectorPivotsToRecentHighAndLowIndicators() {
        final BarSeries series = seriesFromCloses(10, 8, 15, 11, 9);
        final SwingDetector detector = (bars, index, degree) -> index < 4
                ? new SwingDetectorResult(List.of(), List.of())
                : SwingDetectorResult.fromPivots(List.of(new SwingPivot(1, numOf(8), SwingPivotType.LOW),
                        new SwingPivot(2, numOf(15), SwingPivotType.HIGH)), degree);
        final Pair pair = RecentSwingIndicators.fromDetector(series, detector);

        assertThat(pair.lows().getLatestSwingIndex(4)).isEqualTo(1);
        assertThat(pair.highs().getLatestSwingIndex(4)).isEqualTo(2);
        assertThat(pair.highs().getLatestSwingConfirmationIndex(4)).isEqualTo(4);
        assertThat(pair.highs().getSwingPointIndexesUpTo(2)).isEmpty();
    }

    @Test
    public void shouldAdaptTolerantConsensusDetector() {
        final BarSeries series = seriesFromCloses(10, 12, 15, 14, 11);
        final SwingDetector first = fixedHighDetector(2, 15);
        final SwingDetector second = fixedHighDetector(3, 14);
        final Pair pair = RecentSwingIndicators.fromDetector(series, SwingDetectors.consensus(1, 2, first, second));

        assertThat(pair.highs().getLatestSwingIndex(4)).isEqualTo(2);
        assertThat(pair.highs().getValue(4)).isEqualByComparingTo(numOf(15));
    }

    @Test
    public void shouldRejectPairsFromDifferentSeries() {
        final BarSeries highSeries = seriesFromCloses(10, 12, 10);
        final BarSeries lowSeries = seriesFromCloses(10, 8, 10);

        assertThatThrownBy(() -> new Pair(new RecentFractalSwingHighIndicator(highSeries, 1),
                new RecentFractalSwingLowIndicator(lowSeries, 1))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("highs and lows must share the same bar series instance");
    }

    private SwingDetector fixedHighDetector(final int pivotIndex, final Number price) {
        return (series, index, degree) -> SwingDetectorResult
                .fromPivots(List.of(new SwingPivot(pivotIndex, numOf(price), SwingPivotType.HIGH)), degree);
    }

    private BarSeries seriesFromCloses(final double... closes) {
        final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).add();
        }
        return series;
    }
}
