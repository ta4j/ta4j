/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.performance;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.KalmanFilterIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

final class KalmanFilterPerformanceExperiment implements PerformanceExperiment {

    static final String ID = "kalman-filter";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "KalmanFilterIndicator access-pattern performance";
    }

    @Override
    public List<PerformanceScenario> scenarios() {
        return List.of(new SequentialScenario(), new EndOnlyScenario(), new EndThenReverseScenario(),
                new SparseAfterHighWatermarkScenario());
    }

    private abstract static class KalmanScenario implements PerformanceScenario {

        @Override
        public String profileHint() {
            return "Run with -XX:StartFlightRecording=filename=<scenario>.jfr,settings=profile for allocation and CPU hotspots.";
        }

        protected final ScenarioInput input(int barCount) {
            BarSeries series = buildSeries(barCount);
            CountingIndicator source = new CountingIndicator(new ClosePriceIndicator(series));
            KalmanFilterIndicator indicator = new KalmanFilterIndicator(source);
            return new ScenarioInput(series, source, indicator);
        }

        protected final long checksum(long checksum, Num value) {
            double primitive = value.doubleValue();
            long scaled = Double.isFinite(primitive) ? Math.round(primitive * 100_000d) : 0x7ff8_0000_0000_0000L;
            return 31L * checksum + scaled;
        }

        protected final PerformanceScenario.Measurement measurement(long operations, long durationNanos, long checksum,
                CountingIndicator source) {
            return new PerformanceScenario.Measurement(operations, durationNanos, checksum,
                    Map.of("sourceReads", source.readCount()));
        }
    }

    private static final class SequentialScenario extends KalmanScenario {

        @Override
        public String id() {
            return "sequential";
        }

        @Override
        public String description() {
            return "Read every Kalman value in ascending index order.";
        }

        @Override
        public String hypothesis() {
            return "Scalar state updates should reduce per-index matrix overhead for normal forward access.";
        }

        @Override
        public PerformanceScenario.Measurement measure(Context context) {
            ScenarioInput input = input(context.barCount());
            long checksum = 0L;
            long startedNanos = System.nanoTime();
            for (int i = 0; i < context.barCount(); i++) {
                checksum = checksum(checksum, input.indicator().getValue(i));
            }
            long durationNanos = System.nanoTime() - startedNanos;
            return measurement(context.barCount(), durationNanos, checksum, input.source());
        }
    }

    private static final class EndOnlyScenario extends KalmanScenario {

        @Override
        public String id() {
            return "endOnly";
        }

        @Override
        public String description() {
            return "Read only the final Kalman value for the dataset.";
        }

        @Override
        public String hypothesis() {
            return "Recursive state prefill should keep one high-index request linear in bar count.";
        }

        @Override
        public PerformanceScenario.Measurement measure(Context context) {
            ScenarioInput input = input(context.barCount());
            long startedNanos = System.nanoTime();
            long checksum = checksum(0L, input.indicator().getValue(context.barCount() - 1));
            long durationNanos = System.nanoTime() - startedNanos;
            return measurement(1L, durationNanos, checksum, input.source());
        }
    }

    private static final class EndThenReverseScenario extends KalmanScenario {

        @Override
        public String id() {
            return "endThenReverse";
        }

        @Override
        public String description() {
            return "Read the final value first, then walk every earlier value backward.";
        }

        @Override
        public String hypothesis() {
            return "Per-index state caching should eliminate reset-and-replay behavior after high-watermark reads.";
        }

        @Override
        public PerformanceScenario.Measurement measure(Context context) {
            ScenarioInput input = input(context.barCount());
            long checksum = 0L;
            long startedNanos = System.nanoTime();
            checksum = checksum(checksum, input.indicator().getValue(context.barCount() - 1));
            for (int i = context.barCount() - 2; i >= 0; i--) {
                checksum = checksum(checksum, input.indicator().getValue(i));
            }
            long durationNanos = System.nanoTime() - startedNanos;
            return measurement(context.barCount(), durationNanos, checksum, input.source());
        }
    }

    private static final class SparseAfterHighWatermarkScenario extends KalmanScenario {

        @Override
        public String id() {
            return "sparseAfterHighWatermark";
        }

        @Override
        public String description() {
            return "Read the final value first, then read a sparse set of earlier values.";
        }

        @Override
        public String hypothesis() {
            return "Sparse historical reads after a final-value request should reuse cached state instead of replaying history.";
        }

        @Override
        public PerformanceScenario.Measurement measure(Context context) {
            ScenarioInput input = input(context.barCount());
            int step = Math.max(1, context.barCount() / 128);
            long operations = 1L;
            long checksum = 0L;
            long startedNanos = System.nanoTime();
            checksum = checksum(checksum, input.indicator().getValue(context.barCount() - 1));
            for (int i = 0; i < context.barCount(); i += step) {
                checksum = checksum(checksum, input.indicator().getValue(i));
                operations++;
            }
            long durationNanos = System.nanoTime() - startedNanos;
            return measurement(operations, durationNanos, checksum, input.source());
        }
    }

    private record ScenarioInput(BarSeries series, CountingIndicator source, KalmanFilterIndicator indicator) {
    }

    private static final class CountingIndicator implements Indicator<Num> {

        private final Indicator<Num> delegate;
        private long readCount;

        private CountingIndicator(Indicator<Num> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Num getValue(int index) {
            readCount++;
            return delegate.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return delegate.getCountOfUnstableBars();
        }

        @Override
        public BarSeries getBarSeries() {
            return delegate.getBarSeries();
        }

        private long readCount() {
            return readCount;
        }
    }

    private static BarSeries buildSeries(int barCount) {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        Duration timePeriod = Duration.ofDays(1);
        Instant endTime = Instant.EPOCH;
        for (int i = 0; i < barCount; i++) {
            endTime = endTime.plus(timePeriod);
            double close = 100d + i * 0.05d + Math.sin(i / 12d) * 4d;
            double high = close + 1d;
            double low = close - 1d;
            series.barBuilder()
                    .timePeriod(timePeriod)
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .volume(1_000d + i)
                    .add();
        }
        return series;
    }
}
