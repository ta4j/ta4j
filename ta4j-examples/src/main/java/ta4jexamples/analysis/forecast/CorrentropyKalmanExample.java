/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.forecast;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CorrentropyKalmanFilterIndicator;
import org.ta4j.core.indicators.CorrentropyKalmanWeightIndicator;
import org.ta4j.core.indicators.KalmanNoiseIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Demonstrates robust correntropy Kalman smoothing of an ossified S&amp;P 500
 * weekly series.
 *
 * <p>
 * The example builds dynamic process and measurement noise variances from ATR
 * so that Q and R stay in squared-price units:
 *
 * <pre>
 * Q = max(1e-8, 0.0625 * ATR(14)^2)
 * R = max(1e-8, 0.25 * ATR(14)^2)
 * </pre>
 *
 * This is an explicitly illustrative recipe, not a calibrated or universal
 * claim about Q/R: real deployments should derive noise from the source's
 * own scale and verify it against a benchmark.
 *
 * <p>
 * The kernel bandwidth is dimensionless because the correntropy error is
 * whitened by the measurement-noise standard deviation. The default
 * {@code sigma = 2.0} rejects single measurements roughly three standard
 * deviations or more away from the predicted state while leaving ordinary
 * noise largely untouched.
 *
 * <p>
 * The walkthrough logs the robust estimate, residual, and measurement weight
 * at the latest bar, then scans history for one isolated wick and one
 * sustained move to show how the weight drops and the estimate stays level.
 * Finally it composes rejection-weighted residual evidence — without
 * activating any trading strategy.
 *
 * @since 0.24.2
 */
public final class CorrentropyKalmanExample {

    static final String SP500_RESOURCE = "YahooFinance-SP500-PT7D-19500103_20260730.json";
    static final int ATR_BAR_COUNT = 14;
    static final int SUSTAINED_WINDOW = 20;
    static final double MINIMUM_VARIANCE = 1e-8;
    static final double PROCESS_ATR_VARIANCE_SCALE = 0.0625;
    static final double MEASUREMENT_ATR_VARIANCE_SCALE = 0.25;
    static final double KERNEL_BANDWIDTH = 2.0;

    private static final Logger LOG = LogManager.getLogger(CorrentropyKalmanExample.class);

    private CorrentropyKalmanExample() {
    }

    /**
     * Runs the offline correntropy Kalman walkthrough.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        BarSeries series = loadSeries();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ATRIndicator atr = new ATRIndicator(series, ATR_BAR_COUNT);

        NumericIndicator atrVariance = NumericIndicator.of(atr).squared();
        KalmanNoiseIndicator processNoise = new KalmanNoiseIndicator(
                atrVariance.multipliedBy(PROCESS_ATR_VARIANCE_SCALE).max(MINIMUM_VARIANCE));
        KalmanNoiseIndicator measurementNoise = new KalmanNoiseIndicator(
                atrVariance.multipliedBy(MEASUREMENT_ATR_VARIANCE_SCALE).max(MINIMUM_VARIANCE));

        CorrentropyKalmanFilterIndicator filter = new CorrentropyKalmanFilterIndicator(close, processNoise,
                measurementNoise, DoubleNum.valueOf(KERNEL_BANDWIDTH));
        CorrentropyKalmanWeightIndicator weight = filter.measurementWeight();
        Indicator<Num> residual = filter.residual();
        Indicator<Num> rejectionWeightedResidual = BinaryOperationIndicator.product(residual, weight);
        SMAIndicator smoothedResidualMagnitude = new SMAIndicator(
                NumericIndicator.of(residual).abs(), SUSTAINED_WINDOW);

        int endIndex = series.getEndIndex();
        int firstIndex = Math.max(filter.getCountOfUnstableBars(), atr.getCountOfUnstableBars());
        LOG.info("Ossified ^GSPC weekly series: bars={}, first={}, last={}, resource={}", series.getBarCount(),
                series.getFirstBar().getEndTime(), series.getLastBar().getEndTime(), SP500_RESOURCE);
        LOG.info(
                "Noise recipe (illustrative, not calibrated): Q=max({}, {}*ATR^2); R=max({}, {}*ATR^2). Bandwidth sigma={} (dimensionless).",
                MINIMUM_VARIANCE, PROCESS_ATR_VARIANCE_SCALE, MINIMUM_VARIANCE, MEASUREMENT_ATR_VARIANCE_SCALE,
                KERNEL_BANDWIDTH);
        LOG.info("Latest bar: close={}, robustEstimate={}, residual={}, measurementWeight={}",
                close.getValue(endIndex), filter.getValue(endIndex), residual.getValue(endIndex),
                weight.getValue(endIndex));

        int wickIndex = largestSingleBarMoveIndex(close, firstIndex, endIndex);
        LOG.info("Isolated wick at index {} (one-bar move {}): inspecting estimate/weight/residual around it",
                wickIndex, String.format("%.2f", oneBarMove(close, wickIndex)));
        for (int i = Math.max(firstIndex, wickIndex - 1); i <= Math.min(endIndex, wickIndex + 1); i++) {
            LOG.info("  index {}: close={}, estimate={}, weight={}, residual={}", i, close.getValue(i),
                    filter.getValue(i), weight.getValue(i), residual.getValue(i));
        }

        int sustainedStart = largestCumulativeMoveStart(close, firstIndex, endIndex - SUSTAINED_WINDOW + 1);
        LOG.info("Sustained move: {} bars starting at index {} (cumulative move {}); weight stays low while the "
                + "source outruns the state", SUSTAINED_WINDOW, sustainedStart,
                String.format("%.2f", cumulativeMove(close, sustainedStart, sustainedStart + SUSTAINED_WINDOW - 1)));
        for (int i = sustainedStart; i <= Math.min(endIndex, sustainedStart + SUSTAINED_WINDOW - 1); i++) {
            LOG.info("  index {}: close={}, estimate={}, weight={}, residual={}", i, close.getValue(i),
                    filter.getValue(i), weight.getValue(i), residual.getValue(i));
        }

        LOG.info(
                "Downstream evidence composition (no trading strategy): rejection-weighted residual product={} at {}, "
                        + "SMA({}) of |residual|={} at {}, latest close={}",
                rejectionWeightedResidual.getValue(endIndex), endIndex, SUSTAINED_WINDOW,
                smoothedResidualMagnitude.getValue(endIndex), endIndex, close.getValue(endIndex));
    }

    static BarSeries loadSeries() {
        return Objects.requireNonNull(JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(SP500_RESOURCE),
                "S&P 500 resource was not available");
    }

    private static int largestSingleBarMoveIndex(ClosePriceIndicator close, int fromIndex, int toIndex) {
        int best = fromIndex;
        double bestMove = Math.abs(oneBarMove(close, fromIndex));
        for (int i = fromIndex + 1; i <= toIndex; i++) {
            double move = Math.abs(oneBarMove(close, i));
            if (move > bestMove) {
                bestMove = move;
                best = i;
            }
        }
        return best;
    }

    private static double oneBarMove(ClosePriceIndicator close, int index) {
        return close.getValue(index).doubleValue() - close.getValue(index - 1).doubleValue();
    }

    private static int largestCumulativeMoveStart(ClosePriceIndicator close, int fromIndex, int toIndex) {
        int best = fromIndex;
        double bestMove = Math.abs(cumulativeMove(close, fromIndex, fromIndex + SUSTAINED_WINDOW - 1));
        for (int i = fromIndex + 1; i <= toIndex; i++) {
            double move = Math.abs(cumulativeMove(close, i, i + SUSTAINED_WINDOW - 1));
            if (move > bestMove) {
                bestMove = move;
                best = i;
            }
        }
        return best;
    }

    private static double cumulativeMove(ClosePriceIndicator close, int startIndex, int endIndex) {
        return close.getValue(endIndex).doubleValue() - close.getValue(startIndex).doubleValue();
    }
}