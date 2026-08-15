/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class ForecastSnapshotTest {

    @Test
    void stampRejectsAppendReplacePruneClearAndLiveBarMutation() {
        assertInvalidated(this::appendBar);
        assertInvalidated(this::replaceLastBar);
        assertInvalidated(series -> series.setMaximumBarCount(series.getBarCount() - 1));
        assertInvalidated(BarSeries::clear);
        assertInvalidated(series -> series.addPrice(999d));
    }

    @Test
    void stampRejectsAConcurrentRevisionChangeBeforePublication() throws Exception {
        BarSeries series = series();
        SeriesStamp stamp = SeriesStamp.capture(series);

        Thread mutation = Thread.ofPlatform().start(() -> series.addPrice(999d));
        mutation.join();

        assertThrows(StaleSeriesException.class, () -> stamp.requireCurrent(series, "before publication"));
    }

    @Test
    void materializeSamplesRejectsNonFiniteAndNonPositiveTerminalPrices() {
        BarSeries series = series();
        ForecastSnapshot snapshot = snapshot(series);

        MalformedProviderResultException nanFailure = assertThrows(MalformedProviderResultException.class,
                () -> snapshot.materializeSamples(new float[] { 100f, Float.NaN, 101f }, "Metal"));
        assertThat(nanFailure.getMessage()).contains("Metal sample price is non-finite or non-positive")
                .contains("index 0 path 1");

        MalformedProviderResultException negativeFailure = assertThrows(MalformedProviderResultException.class,
                () -> snapshot.materializeSamples(new float[] { 100f, -5f, 101f }, "Metal"));
        assertThat(negativeFailure.getMessage()).contains("index 0 path 1");
    }

    @Test
    void materializeRowsTreatsStatusTwoAsMalformedProviderResult() {
        ForecastSnapshot snapshot = snapshot(series());

        double[] rows = new double[4 + 3];
        rows[0] = 2d;
        MalformedProviderResultException failure = assertThrows(MalformedProviderResultException.class,
                () -> snapshot.materializeRows(rows, "Metal"));

        assertThat(failure.getMessage()).contains("Metal decision 0 failed with status 2");
    }

    private void assertInvalidated(Consumer<BarSeries> mutation) {
        BarSeries series = series();
        SeriesStamp stamp = SeriesStamp.capture(series);

        mutation.accept(series);

        assertThrows(StaleSeriesException.class, () -> stamp.requireCurrent(series, "before publication"));
    }

    private void appendBar(BarSeries series) {
        Bar last = series.getLastBar();
        series.barBuilder()
                .timePeriod(last.getTimePeriod())
                .endTime(last.getEndTime().plus(last.getTimePeriod()))
                .openPrice(last.getClosePrice())
                .highPrice(last.getClosePrice())
                .lowPrice(last.getClosePrice())
                .closePrice(last.getClosePrice())
                .volume(series.numFactory().zero())
                .add();
    }

    private void replaceLastBar(BarSeries series) {
        Bar last = series.getLastBar();
        Bar replacement = series.barBuilder()
                .timePeriod(last.getTimePeriod())
                .endTime(last.getEndTime())
                .openPrice(last.getOpenPrice())
                .highPrice(series.numFactory().numOf(999d))
                .lowPrice(last.getLowPrice())
                .closePrice(series.numFactory().numOf(999d))
                .volume(last.getVolume())
                .build();
        series.addBar(replacement, true);
    }

    private ForecastSnapshot snapshot(BarSeries series) {
        NativeForecastRequest request = new NativeForecastRequest(0, 1, 2, 3, 1, 42L, 0, 0, 0.94d,
                new double[] { 0.05, 0.5, 0.95 }, new int[] { 1 }, new double[] { 100d }, new double[] { 0.01d },
                new double[] { 1e-4d }, new double[] { 1e-4d }, new double[] { 0.01d });
        return new ForecastSnapshot(series, SeriesStamp.capture(series), series.numFactory(), 0, 1, 2, 3,
                List.of(0.05, 0.5, 0.95), request);
    }

    private BarSeries series() {
        return new MockBarSeriesBuilder().withData(100, 101, 102, 103, 104).build();
    }
}
