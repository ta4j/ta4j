/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastSpec;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

record ForecastSnapshot(BarSeries series, SeriesStamp stamp, NumFactory numFactory, int fromInclusive,
        int decisionCount, int horizon, int iterationCount, List<Double> quantileProbabilities,
        NativeForecastRequest nativeRequest) {

    static ForecastSnapshot capture(MonteCarloPriceForecastIndicator indicator, int fromInclusive, int toInclusive,
            String providerName) {
        MonteCarloPriceForecastSpec spec = indicator.accelerationSpec();
        BarSeries series = indicator.getBarSeries();
        if (series.numFactory() != DoubleNumFactory.getInstance()) {
            throw new IllegalArgumentException(providerName + " forecast provider requires DoubleNum precision");
        }
        if (!IndicatorUtils.isSameSeries(spec.priceIndicator().getBarSeries(), series)
                || !IndicatorUtils.isSameSeries(spec.stateIndicator().getBarSeries(), series)
                || !IndicatorUtils.isSameSeries(spec.stateIndicator().getReturnIndicator().getBarSeries(), series)) {
            throw new IllegalArgumentException(providerName + " forecast sources must share one BarSeries instance");
        }

        int decisionCount = Math.addExact(Math.subtractExact(toInclusive, fromInclusive), 1);
        int historyLength = Math.multiplyExact(decisionCount, spec.lookbackBarCount());
        SeriesStamp before = SeriesStamp.capture(series);
        int[] stable = new int[decisionCount];
        double[] prices = new double[decisionCount];
        double[] means = new double[decisionCount];
        double[] drifts = new double[decisionCount];
        double[] variances = new double[decisionCount];
        double[] historicalReturns = new double[historyLength];
        ReturnIndicator returnIndicator = spec.stateIndicator().getReturnIndicator();
        for (int offset = 0; offset < decisionCount; offset++) {
            int index = Math.addExact(fromInclusive, offset);
            if (!captureDecision(indicator, spec, returnIndicator, index, offset, stable, prices, means, drifts,
                    variances, historicalReturns)) {
                stable[offset] = 0;
            }
        }
        before.requireCurrent(series, "during " + providerName + " snapshot capture");
        double[] quantiles = spec.quantileProbabilities().stream().mapToDouble(Double::doubleValue).toArray();
        NativeForecastRequest request = new NativeForecastRequest(fromInclusive, decisionCount, spec.horizon(),
                spec.iterationCount(), spec.lookbackBarCount(), spec.seed(), spec.shockModel().ordinal(),
                spec.volatilityUpdateMode().ordinal(), spec.volatilityDecayFactor(), quantiles, stable, prices, means,
                drifts, variances, historicalReturns);
        return new ForecastSnapshot(series, before, series.numFactory(), fromInclusive, decisionCount, spec.horizon(),
                spec.iterationCount(), spec.quantileProbabilities(), request);
    }

    /**
     * Computes the peak native payload estimate from request dimensions alone, so
     * providers can enforce their memory ceiling before any capture work or
     * allocation happens. The estimate covers the Java-side staging arrays and
     * their native copies, the device result buffer, and the materialized sample or
     * quantile rows produced after the native call, plus a small fixed per-array
     * overhead.
     */
    static long estimatedPeakBytes(long decisionCount, long lookbackBarCount, long iterationCount, long quantileCount,
            boolean sampleOutput) {
        long inputDoubles = Math.addExact(Math.multiplyExact(decisionCount, 5L + lookbackBarCount), quantileCount);
        long inputsBytes = Math.multiplyExact(
                Math.addExact(Math.multiplyExact(inputDoubles, Double.BYTES), Math.multiplyExact(decisionCount, 4L)),
                2L);
        long outputsBytes;
        long materializationBytes;
        if (sampleOutput) {
            outputsBytes = Math.multiplyExact(Math.multiplyExact(decisionCount, iterationCount), Float.BYTES);
            materializationBytes = Math.multiplyExact(iterationCount, 48L);
        } else {
            outputsBytes = Math.multiplyExact(
                    Math.addExact(iterationCount, Math.multiplyExact(decisionCount, 4L + quantileCount)), Double.BYTES);
            materializationBytes = Math.multiplyExact(Math.multiplyExact(decisionCount, quantileCount), 48L);
        }
        return Math.addExact(Math.addExact(Math.addExact(inputsBytes, outputsBytes), materializationBytes),
                ARRAY_OVERHEAD_BYTES);
    }

    private static final long ARRAY_OVERHEAD_BYTES = 16L * 16L;

    List<Forecast> materializeRows(double[] rows, String providerName) {
        stamp.requireCurrent(series, "before " + providerName + " result publication");
        int rowLength = 4 + quantileProbabilities.size();
        if (rows.length != Math.multiplyExact(decisionCount, rowLength)) {
            throw new MalformedProviderResultException(
                    providerName + " result length does not match the immutable request");
        }
        List<Forecast> values = new ArrayList<>(decisionCount);
        for (int offset = 0; offset < decisionCount; offset++) {
            int rowOffset = offset * rowLength;
            int status = (int) rows[rowOffset];
            int index = fromInclusive + offset;
            Forecast forecast = switch (status) {
            case 0 -> stableForecast(index, rowOffset, rows, providerName);
            case 1, 2 -> Forecast.unstable(index, horizon);
            default -> throw new MalformedProviderResultException(
                    providerName + " decision " + index + " failed with status " + status);
            };
            values.add(forecast);
        }
        stamp.requireCurrent(series, "while publishing " + providerName + " results");
        return List.copyOf(values);
    }

    List<Forecast> materializeSamples(float[] terminalPrices, String providerName) {
        stamp.requireCurrent(series, "before " + providerName + " result publication");
        int expected = Math.multiplyExact(decisionCount, iterationCount);
        if (terminalPrices.length != expected) {
            throw new MalformedProviderResultException(
                    providerName + " sample count does not match the immutable request");
        }
        int[] stable = nativeRequest.stable();
        List<Forecast> values = new ArrayList<>(decisionCount);
        for (int offset = 0; offset < decisionCount; offset++) {
            int index = fromInclusive + offset;
            if (stable[offset] == 0) {
                values.add(Forecast.unstable(index, horizon));
                continue;
            }
            int sampleOffset = Math.multiplyExact(offset, iterationCount);
            List<Num> samples = new ArrayList<>(iterationCount);
            boolean valid = true;
            for (int path = 0; path < iterationCount; path++) {
                float sample = terminalPrices[sampleOffset + path];
                if (!Float.isFinite(sample) || sample <= 0f) {
                    valid = false;
                    break;
                }
                samples.add(numFactory.numOf((double) sample));
            }
            values.add(valid ? Forecast.ofSamples(index, horizon, samples, quantileProbabilities)
                    : Forecast.unstable(index, horizon));
        }
        stamp.requireCurrent(series, "while publishing " + providerName + " results");
        return List.copyOf(values);
    }

    private Forecast stableForecast(int index, int rowOffset, double[] rows, String providerName) {
        double mean = requireFinite(rows[rowOffset + 1], "mean", index, providerName);
        double median = requireFinite(rows[rowOffset + 2], "median", index, providerName);
        double standardDeviation = requireFinite(rows[rowOffset + 3], "standard deviation", index, providerName);
        if (standardDeviation < 0d) {
            throw new MalformedProviderResultException(
                    providerName + " standard deviation is negative at index " + index);
        }
        Map<Double, Num> mappedQuantiles = new LinkedHashMap<>();
        double previous = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < quantileProbabilities.size(); i++) {
            double probability = quantileProbabilities.get(i);
            double value = requireFinite(rows[rowOffset + 4 + i], "quantile", index, providerName);
            if (value < previous) {
                throw new MalformedProviderResultException(
                        providerName + " quantiles are not monotone at index " + index);
            }
            previous = value;
            mappedQuantiles.put(probability, numFactory.numOf(value));
            if (Double.doubleToLongBits(probability) == Double.doubleToLongBits(0.5d)) {
                median = value;
            }
        }
        return Forecast.builder(index, horizon, numFactory, ForecastSupport.empirical(iterationCount))
                .mean(numFactory.numOf(mean))
                .median(numFactory.numOf(median))
                .standardDeviation(numFactory.numOf(standardDeviation))
                .quantiles(mappedQuantiles)
                .build();
    }

    private static boolean captureDecision(MonteCarloPriceForecastIndicator indicator, MonteCarloPriceForecastSpec spec,
            ReturnIndicator returnIndicator, int index, int offset, int[] stable, double[] prices, double[] means,
            double[] drifts, double[] variances, double[] historicalReturns) {
        if (index < indicator.getCountOfUnstableBars() || index < indicator.getBarSeries().getRemovedBarsCount()) {
            return false;
        }
        Num price = spec.priceIndicator().getValue(index);
        ReturnMomentState state = spec.stateIndicator().getValue(index);
        ReturnMoments moments = state == null ? null : state.moments();
        if (!finitePositive(price) || moments == null || moments.index() != index || !moments.isStable()
                || moments.representation() != ReturnRepresentation.LOG || !finite(moments.mean())
                || !finite(moments.drift()) || !finiteNonNegative(moments.variance())) {
            return false;
        }
        int historyStart = index - spec.lookbackBarCount() + 1;
        if (historyStart < indicator.getBarSeries().getRemovedBarsCount()) {
            return false;
        }
        int historyOffset = Math.multiplyExact(offset, spec.lookbackBarCount());
        for (int i = 0; i < spec.lookbackBarCount(); i++) {
            Num value = returnIndicator.getValue(historyStart + i);
            if (!finite(value)) {
                return false;
            }
            historicalReturns[historyOffset + i] = value.doubleValue();
        }
        stable[offset] = 1;
        prices[offset] = price.doubleValue();
        means[offset] = moments.mean().doubleValue();
        drifts[offset] = moments.drift().doubleValue();
        variances[offset] = moments.variance().doubleValue();
        return true;
    }

    private static boolean finite(Num value) {
        return Num.isFinite(value) && Double.isFinite(value.doubleValue());
    }

    private static boolean finitePositive(Num value) {
        return finite(value) && value.isPositive();
    }

    private static boolean finiteNonNegative(Num value) {
        return finite(value) && !value.isNegative();
    }

    private static double requireFinite(double value, String field, int index, String providerName) {
        if (!Double.isFinite(value)) {
            throw new MalformedProviderResultException(providerName + " " + field + " is non-finite at index " + index);
        }
        return value;
    }
}

record NativeForecastRequest(int fromInclusive, int decisionCount, int horizon, int iterationCount,
        int lookbackBarCount, long seed, int shockModel, int volatilityMode, double volatilityDecayFactor,
        double[] quantiles, int[] stable, double[] prices, double[] means, double[] drifts, double[] variances,
        double[] historicalReturns) {
}

record SeriesStamp(int beginIndex, int endIndex, int removedBars, int barCount, long historyRevision,
        long dataFingerprint) {

    static SeriesStamp capture(BarSeries series) {
        return new SeriesStamp(series.getBeginIndex(), series.getEndIndex(), series.getRemovedBarsCount(),
                series.getBarCount(), series.getBarHistoryRevision(), fingerprint(series));
    }

    void requireCurrent(BarSeries series, String phase) {
        if (!equals(capture(series))) {
            throw new StaleSeriesException("BarSeries changed " + phase);
        }
    }

    private static long fingerprint(BarSeries series) {
        long value = 0xCBF29CE484222325L;
        for (Bar bar : series.getBarData()) {
            value = mix(value, bar.getBeginTime().getEpochSecond());
            value = mix(value, bar.getBeginTime().getNano());
            value = mix(value, bar.getEndTime().getEpochSecond());
            value = mix(value, bar.getEndTime().getNano());
            value = mix(value, bits(bar.getOpenPrice()));
            value = mix(value, bits(bar.getHighPrice()));
            value = mix(value, bits(bar.getLowPrice()));
            value = mix(value, bits(bar.getClosePrice()));
            value = mix(value, bits(bar.getVolume()));
            value = mix(value, bits(bar.getAmount()));
            value = mix(value, bar.getTrades());
        }
        return value;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001B3L;
    }

    private static long bits(Num value) {
        return value == null ? 0x7FF8000000000001L : Double.doubleToLongBits(value.doubleValue());
    }
}

final class StaleSeriesException extends IllegalStateException {

    StaleSeriesException(String message) {
        super(message);
    }
}

/**
 * Signals that a native provider returned output whose shape or values do not
 * match the immutable request captured in the snapshot, or that is otherwise
 * unusable for forecast materialization.
 */
final class MalformedProviderResultException extends IllegalStateException {

    MalformedProviderResultException(String message) {
        super(message);
    }
}
