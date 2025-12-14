/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ta4jexamples.analysis;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.elliott.ElliottChannel;
import org.ta4j.core.indicators.elliott.ElliottChannelIndicator;
import org.ta4j.core.indicators.elliott.ElliottConfluenceIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottFibonacciValidator;
import org.ta4j.core.indicators.elliott.ElliottInvalidationIndicator;
import org.ta4j.core.indicators.elliott.ElliottPhaseIndicator;
import org.ta4j.core.indicators.elliott.ElliottRatio;
import org.ta4j.core.indicators.elliott.ElliottRatioIndicator;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottSwingCompressor;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;
import org.ta4j.core.indicators.elliott.ElliottSwingMetadata;
import org.ta4j.core.indicators.elliott.ElliottWaveCountIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Demonstrates the Elliott Wave indicator suite (swings, phases, Fibonacci
 * validation, channels, ratios, confluence scoring, invalidation checks) along
 * with chart visualisation and pivot labels.
 */
public class ElliottWaveAnalysis {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveAnalysis.class);

    private static final String OSSIFIED_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";

    private static final ElliottDegree DEGREE = ElliottDegree.PRIMARY;
    private static final double FIB_TOLERANCE = 0.25;

    public static void main(String[] args) {
        BarSeries series = loadOssifiedSeries();
        Objects.requireNonNull(series, "Bar series was null");
        if (series.isEmpty()) {
            LOG.error("Series is empty, nothing to analyse.");
            return;
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        ElliottSwingIndicator swingIndicator = ElliottSwingIndicator.zigZag(series, DEGREE);
        ElliottFibonacciValidator validator = new ElliottFibonacciValidator(series.numFactory(),
                series.numFactory().numOf(FIB_TOLERANCE));
        ElliottPhaseIndicator phaseIndicator = new ElliottPhaseIndicator(swingIndicator, validator);
        ElliottInvalidationIndicator invalidationIndicator = new ElliottInvalidationIndicator(phaseIndicator);

        ElliottChannelIndicator channelIndicator = new ElliottChannelIndicator(swingIndicator);
        ElliottRatioIndicator ratioIndicator = new ElliottRatioIndicator(swingIndicator);
        ElliottConfluenceIndicator confluenceIndicator = new ElliottConfluenceIndicator(closePrice, ratioIndicator,
                channelIndicator);

        ElliottWaveCountIndicator swingCount = new ElliottWaveCountIndicator(swingIndicator);
        ElliottSwingCompressor compressor = new ElliottSwingCompressor(
                closePrice.getValue(series.getEndIndex()).multipliedBy(series.numFactory().numOf(0.01)), 2);
        ElliottWaveCountIndicator filteredSwingCount = new ElliottWaveCountIndicator(swingIndicator, compressor);

        int endIndex = series.getEndIndex();
        ElliottSwingMetadata snapshot = ElliottSwingMetadata.of(swingIndicator.getValue(endIndex), series.numFactory());
        LOG.info("Elliott swing snapshot valid={}, swings={}, high={}, low={}", snapshot.isValid(), snapshot.size(),
                snapshot.highestPrice(), snapshot.lowestPrice());

        ElliottRatio latestRatio = ratioIndicator.getValue(endIndex);
        ElliottChannel latestChannel = channelIndicator.getValue(endIndex);
        LOG.info("Latest phase={} impulseConfirmed={} correctiveConfirmed={}", phaseIndicator.getValue(endIndex),
                phaseIndicator.isImpulseConfirmed(endIndex), phaseIndicator.isCorrectiveConfirmed(endIndex));
        LOG.info("Latest ratio type={} value={}", latestRatio.type(), latestRatio.value());
        LOG.info("Latest channel valid={} upper={} lower={} median={}", latestChannel.isValid(), latestChannel.upper(),
                latestChannel.lower(), latestChannel.median());
        LOG.info("Latest confluence score={} confluent={}", confluenceIndicator.getValue(endIndex),
                confluenceIndicator.isConfluent(endIndex));
        LOG.info("Latest invalidation={}", invalidationIndicator.getValue(endIndex));

        BarSeriesLabelIndicator waveLabels = buildWaveLabels(series, phaseIndicator);

        Indicator<Num> channelUpper = new ChannelBoundaryIndicator(series, channelIndicator, ChannelBoundary.UPPER);
        Indicator<Num> channelLower = new ChannelBoundaryIndicator(series, channelIndicator, ChannelBoundary.LOWER);
        Indicator<Num> channelMedian = new ChannelBoundaryIndicator(series, channelIndicator, ChannelBoundary.MEDIAN);
        Indicator<Num> ratioValue = new RatioValueIndicator(series, ratioIndicator, "Elliott ratio value");
        Indicator<Num> swingCountAsNum = new IntegerAsNumIndicator(series, swingCount, "Swings (raw)");
        Indicator<Num> filteredSwingCountAsNum = new IntegerAsNumIndicator(series, filteredSwingCount,
                "Swings (compressed)");

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        ChartPlan plan = chartWorkflow.builder()
                .withTitle("Elliott Wave (" + DEGREE + ") - BTC-USD (Coinbase, ossified)")
                .withSeries(series)
                .withIndicatorOverlay(channelUpper)
                .withLineColor(new Color(0xF05454))
                .withLineWidth(1.4f)
                .withOpacity(0.85f)
                .withLabel("Elliott channel upper")
                .withIndicatorOverlay(channelLower)
                .withLineColor(new Color(0x26A69A))
                .withLineWidth(1.4f)
                .withOpacity(0.85f)
                .withLabel("Elliott channel lower")
                .withIndicatorOverlay(channelMedian)
                .withLineColor(Color.LIGHT_GRAY)
                .withLineWidth(1.2f)
                .withOpacity(0.55f)
                .withLabel("Elliott channel median")
                .withIndicatorOverlay(waveLabels)
                .withLineColor(new Color(0x03DAC6))
                .withLineWidth(2.0f)
                .withOpacity(0.9f)
                .withLabel("Wave pivots")
                .withSubChart(swingCountAsNum)
                .withIndicatorOverlay(filteredSwingCountAsNum)
                .withLineColor(new Color(0x90CAF9))
                .withOpacity(0.85f)
                .withLabel("Swings (compressed)")
                .withSubChart(ratioValue)
                .withHorizontalMarker(1.0)
                .withLineColor(Color.GRAY)
                .withOpacity(0.3f)
                .withSubChart(confluenceIndicator)
                .withHorizontalMarker(2.0)
                .withLineColor(Color.GRAY)
                .withOpacity(0.3f)
                .toPlan();

        if (!GraphicsEnvironment.isHeadless()) {
            chartWorkflow.display(plan);
        }
        chartWorkflow.save(plan, "temp/charts", "elliott-wave-btc-usd-primary");
    }

    private static BarSeries loadOssifiedSeries() {
        try (InputStream stream = ElliottWaveAnalysis.class.getClassLoader()
                .getResourceAsStream(OSSIFIED_OHLCV_RESOURCE)) {
            if (stream == null) {
                LOG.error("Missing resource: {}", OSSIFIED_OHLCV_RESOURCE);
                return null;
            }
            BarSeries loaded = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(stream);
            if (loaded == null) {
                return null;
            }

            BarSeries series = new BaseBarSeriesBuilder().withName("BTC-USD_PT1D@Coinbase (ossified)").build();
            for (int i = 0; i < loaded.getBarCount(); i++) {
                series.addBar(loaded.getBar(i));
            }
            return series;
        } catch (Exception ex) {
            LOG.error("Failed to load Elliott wave dataset: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private static BarSeriesLabelIndicator buildWaveLabels(BarSeries series, ElliottPhaseIndicator phaseIndicator) {
        int endIndex = series.getEndIndex();
        List<ElliottSwing> impulse = phaseIndicator.impulseSwings(endIndex);
        List<ElliottSwing> correction = phaseIndicator.correctiveSwings(endIndex);

        List<BarLabel> labels = new ArrayList<>();
        if (!impulse.isEmpty()) {
            ElliottSwing first = impulse.get(0);
            labels.add(new BarLabel(first.fromIndex(), first.fromPrice(), "", placementForPivot(!first.isRising())));
        }

        for (int i = 0; i < impulse.size(); i++) {
            ElliottSwing swing = impulse.get(i);
            labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), String.valueOf(i + 1),
                    placementForPivot(swing.isRising())));
        }

        for (int i = 0; i < correction.size(); i++) {
            ElliottSwing swing = correction.get(i);
            String label = switch (i) {
            case 0 -> "A";
            case 1 -> "B";
            default -> "C";
            };
            labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), label, placementForPivot(swing.isRising())));
        }

        return new BarSeriesLabelIndicator(series, labels);
    }

    private static LabelPlacement placementForPivot(boolean isHighPivot) {
        return isHighPivot ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
    }

    private enum ChannelBoundary {
        UPPER, LOWER, MEDIAN
    }

    private static final class ChannelBoundaryIndicator extends CachedIndicator<Num> {

        private final ElliottChannelIndicator channelIndicator;
        private final ChannelBoundary boundary;

        private ChannelBoundaryIndicator(BarSeries series, ElliottChannelIndicator channelIndicator,
                ChannelBoundary boundary) {
            super(series);
            this.channelIndicator = Objects.requireNonNull(channelIndicator, "channelIndicator");
            this.boundary = Objects.requireNonNull(boundary, "boundary");
        }

        @Override
        protected Num calculate(int index) {
            ElliottChannel channel = channelIndicator.getValue(index);
            if (channel == null) {
                return getBarSeries().numFactory().numOf(Double.NaN);
            }
            return switch (boundary) {
            case UPPER -> channel.upper();
            case LOWER -> channel.lower();
            case MEDIAN -> channel.median();
            };
        }

        @Override
        public int getCountOfUnstableBars() {
            return channelIndicator.getCountOfUnstableBars();
        }
    }

    private static final class RatioValueIndicator extends CachedIndicator<Num> {

        private final ElliottRatioIndicator ratioIndicator;
        private final String label;

        private RatioValueIndicator(BarSeries series, ElliottRatioIndicator ratioIndicator, String label) {
            super(series);
            this.ratioIndicator = Objects.requireNonNull(ratioIndicator, "ratioIndicator");
            this.label = Objects.requireNonNull(label, "label");
        }

        @Override
        protected Num calculate(int index) {
            ElliottRatio ratio = ratioIndicator.getValue(index);
            if (ratio == null) {
                return getBarSeries().numFactory().numOf(Double.NaN);
            }
            return ratio.value();
        }

        @Override
        public int getCountOfUnstableBars() {
            return ratioIndicator.getCountOfUnstableBars();
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class IntegerAsNumIndicator extends CachedIndicator<Num> {

        private final Indicator<Integer> delegate;
        private final String label;

        private IntegerAsNumIndicator(BarSeries series, Indicator<Integer> delegate, String label) {
            super(series);
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.label = Objects.requireNonNull(label, "label");
        }

        @Override
        protected Num calculate(int index) {
            Integer value = delegate.getValue(index);
            return value == null ? getBarSeries().numFactory().numOf(Double.NaN)
                    : getBarSeries().numFactory().numOf(value);
        }

        @Override
        public int getCountOfUnstableBars() {
            return delegate.getCountOfUnstableBars();
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
