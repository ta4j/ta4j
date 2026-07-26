/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.elliott.swing.AdaptiveZigZagConfig;
import org.ta4j.core.indicators.elliott.swing.SlopeChangeConfig;
import org.ta4j.core.indicators.elliott.swing.SwingDetector;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;
import org.ta4j.core.indicators.elliott.swing.SwingPivot;
import org.ta4j.core.indicators.elliott.swing.SwingPivotType;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingHighIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.num.Num;

/**
 * Factory and paired view for recent swing-high and swing-low indicators.
 *
 * <p>
 * {@link #defaultFor(BarSeries)} is the canonical general-purpose choice. It
 * uses intrabar highs/lows, close-price confirmation, and an ATR(14) ZigZag
 * reversal distance. Named factories preserve each methodology explicitly so
 * domain-specific consumers can continue to choose fractal, prominence,
 * slope-change, adaptive ZigZag, or consensus semantics.
 *
 * @since 0.23.1
 */
public final class RecentSwingIndicators {

    private static final int DEFAULT_FRACTAL_WINDOW = 3;
    private static final int DEFAULT_SLOPE_WINDOW = 5;
    private static final int DEFAULT_CONSENSUS_TOLERANCE = 2;
    private static final int DEFAULT_CONSENSUS_VOTES = 2;

    private RecentSwingIndicators() {
    }

    /**
     * Builds the canonical general-purpose recent swing pair.
     *
     * @param series source bar series
     * @return ATR-scaled ZigZag high/low pair
     * @since 0.23.1
     */
    public static Pair defaultFor(final BarSeries series) {
        return zigZag(series);
    }

    /**
     * Builds the default fractal pair using three preceding bars and three
     * following bars around the pivot.
     *
     * @param series source bar series
     * @return fractal high/low pair
     * @since 0.23.1
     */
    public static Pair fractal(final BarSeries series) {
        return fractal(series, DEFAULT_FRACTAL_WINDOW, DEFAULT_FRACTAL_WINDOW, 0);
    }

    /**
     * Builds a configured fractal pair.
     *
     * @param series           source bar series
     * @param precedingBars    bars required before a pivot
     * @param followingBars    bars required after a pivot
     * @param allowedEqualBars maximum additional equal bars in a plateau
     * @return fractal high/low pair
     * @since 0.23.1
     */
    public static Pair fractal(final BarSeries series, final int precedingBars, final int followingBars,
            final int allowedEqualBars) {
        Objects.requireNonNull(series, "series");
        return new Pair(Method.FRACTAL,
                new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), precedingBars, followingBars,
                        allowedEqualBars),
                new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), precedingBars, followingBars,
                        allowedEqualBars));
    }

    /**
     * Builds an OHLC-aware ATR(14) ZigZag pair with shared state.
     *
     * @param series source bar series
     * @return ZigZag high/low pair
     * @since 0.23.1
     */
    public static Pair zigZag(final BarSeries series) {
        final ZigZagStateIndicator state = new ZigZagStateIndicator(Objects.requireNonNull(series, "series"));
        return new Pair(Method.ZIGZAG, new RecentZigZagSwingHighIndicator(state),
                new RecentZigZagSwingLowIndicator(state));
    }

    /**
     * Builds an adaptive ATR ZigZag pair with
     * {@link AdaptiveZigZagConfig#defaults()}.
     *
     * @param series source bar series
     * @return adaptive ZigZag high/low pair
     * @since 0.23.1
     */
    public static Pair adaptiveZigZag(final BarSeries series) {
        return adaptiveZigZag(series, AdaptiveZigZagConfig.defaults());
    }

    /**
     * Builds an adaptive ATR ZigZag pair.
     *
     * @param series source bar series
     * @param config adaptive ZigZag configuration
     * @return adaptive ZigZag high/low pair
     * @since 0.23.1
     */
    public static Pair adaptiveZigZag(final BarSeries series, final AdaptiveZigZagConfig config) {
        return fromDetector(series, SwingDetectors.adaptiveZigZag(Objects.requireNonNull(config, "config")),
                Method.ADAPTIVE_ZIGZAG);
    }

    /**
     * Builds a slope-change pair with balanced defaults.
     *
     * @param series source bar series
     * @return slope-change high/low pair
     * @since 0.23.1
     */
    public static Pair slopeChange(final BarSeries series) {
        return slopeChange(series, SlopeChangeConfig.defaults(DEFAULT_SLOPE_WINDOW));
    }

    /**
     * Builds a configured slope-change pair.
     *
     * @param series source bar series
     * @param config slope-change configuration
     * @return slope-change high/low pair
     * @since 0.23.1
     */
    public static Pair slopeChange(final BarSeries series, final SlopeChangeConfig config) {
        return fromDetector(series, SwingDetectors.slopeChange(Objects.requireNonNull(config, "config")),
                Method.SLOPE_CHANGE);
    }

    /**
     * Builds a price-prominence pair with balanced defaults.
     *
     * @param series source bar series
     * @return prominence high/low pair
     * @since 0.23.1
     */
    public static Pair prominence(final BarSeries series) {
        return prominence(series, ProminenceSwingConfig.defaults());
    }

    /**
     * Builds a configured price-prominence pair.
     *
     * @param series source bar series
     * @param config prominence configuration
     * @return prominence high/low pair
     * @since 0.23.1
     */
    public static Pair prominence(final BarSeries series, final ProminenceSwingConfig config) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(config, "config");
        return new Pair(Method.PROMINENCE, new RecentProminenceSwingHighIndicator(series, config),
                new RecentProminenceSwingLowIndicator(series, config));
    }

    /**
     * Builds the standard two-of-three consensus: seven-bar fractal (three
     * preceding bars, pivot bar, three following bars), adaptive ZigZag, and
     * five-bar slope change, clustered within two bars.
     *
     * @param series source bar series
     * @return consensus high/low pair
     * @since 0.23.1
     */
    public static Pair consensus(final BarSeries series) {
        return fromDetector(series,
                SwingDetectors.consensus(DEFAULT_CONSENSUS_TOLERANCE, DEFAULT_CONSENSUS_VOTES,
                        SwingDetectors.fractal(DEFAULT_FRACTAL_WINDOW),
                        SwingDetectors.adaptiveZigZag(AdaptiveZigZagConfig.defaults()),
                        SwingDetectors.slopeChange(DEFAULT_SLOPE_WINDOW)),
                Method.CONSENSUS);
    }

    /**
     * Adapts any swing detector, including composite/consensus detectors, into
     * paired recent high/low indicators.
     *
     * @param series   source bar series
     * @param detector swing detector
     * @return detector-backed high/low pair
     * @since 0.23.1
     */
    public static Pair fromDetector(final BarSeries series, final SwingDetector detector) {
        return fromDetector(series, detector, Method.CUSTOM);
    }

    private static Pair fromDetector(final BarSeries series, final SwingDetector detector, final Method method) {
        Objects.requireNonNull(series, "series");
        return fromDetector(new HighPriceIndicator(series), new LowPriceIndicator(series), detector, method);
    }

    /**
     * Adapts a swing detector using custom high/low price sources.
     * <p>
     * Detector-backed indicators return detector-supplied
     * {@link SwingPivot#price()} values for pivots detected through the current
     * series end. The custom price sources define the shared bar series and remain
     * the fallback values for bars that are not known detector pivots.
     *
     * @param highPrice swing-high price source
     * @param lowPrice  swing-low price source
     * @param detector  swing detector
     * @return detector-backed high/low pair
     * @since 0.23.1
     */
    public static Pair fromDetector(final Indicator<Num> highPrice, final Indicator<Num> lowPrice,
            final SwingDetector detector) {
        return fromDetector(highPrice, lowPrice, detector, Method.CUSTOM);
    }

    private static Pair fromDetector(final Indicator<Num> highPrice, final Indicator<Num> lowPrice,
            final SwingDetector detector, final Method method) {
        IndicatorUtils.requireSameSeries(Objects.requireNonNull(highPrice, "highPrice"),
                Objects.requireNonNull(lowPrice, "lowPrice"));
        final DetectorPivotIndicator pivots = new DetectorPivotIndicator(highPrice.getBarSeries(),
                Objects.requireNonNull(detector, "detector"));
        final DetectorSwingPriceIndicator highPivotPrices = new DetectorSwingPriceIndicator(highPrice, pivots,
                SwingPivotType.HIGH);
        final DetectorSwingPriceIndicator lowPivotPrices = new DetectorSwingPriceIndicator(lowPrice, pivots,
                SwingPivotType.LOW);
        return new Pair(method, new DetectorRecentSwingIndicator(highPivotPrices, pivots, SwingPivotType.HIGH),
                new DetectorRecentSwingIndicator(lowPivotPrices, pivots, SwingPivotType.LOW));
    }

    /**
     * Swing methodology used by a paired factory result.
     *
     * @since 0.23.1
     */
    public enum Method {
        FRACTAL, ZIGZAG, ADAPTIVE_ZIGZAG, SLOPE_CHANGE, PROMINENCE, CONSENSUS, CUSTOM
    }

    /**
     * Confirmation state for a status-aware swing point.
     *
     * @since 0.23.1
     */
    public enum Confirmation {
        CONFIRMED, PROVISIONAL
    }

    /**
     * A confirmed or developing swing point.
     *
     * @param pivotIndex        bar containing the extreme
     * @param confirmationIndex bar that confirmed the pivot, {@code -1} while
     *                          provisional, or the evaluation index when adapting a
     *                          legacy indicator that cannot expose causal
     *                          confirmation timing
     * @param price             extreme price
     * @param type              high or low
     * @param confirmation      confirmation state
     * @since 0.23.1
     */
    public record SwingPoint(int pivotIndex, int confirmationIndex, Num price, SwingPivotType type,
            Confirmation confirmation) {

        public SwingPoint {
            if (pivotIndex < 0) {
                throw new IllegalArgumentException("pivotIndex must be non-negative");
            }
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(confirmation, "confirmation");
            if (confirmation == Confirmation.CONFIRMED && confirmationIndex < pivotIndex) {
                throw new IllegalArgumentException("confirmed points require confirmationIndex >= pivotIndex");
            }
            if (confirmation == Confirmation.PROVISIONAL && confirmationIndex != -1) {
                throw new IllegalArgumentException("provisional points require confirmationIndex == -1");
            }
        }
    }

    /**
     * Paired recent swing-high and swing-low indicators with a separate
     * status-aware view of the current developing terminal extreme.
     *
     * <p>
     * The underlying indicators remain confirmed-only and non-repainting.
     * {@link #formingPoint(int)} scans the leg after the latest confirmed pivot and
     * labels its most extreme point {@link Confirmation#PROVISIONAL}.
     *
     * @since 0.23.1
     */
    public static final class Pair {

        private final RecentSwingIndicator highs;
        private final RecentSwingIndicator lows;
        private final BarSeries series;
        private final Method method;

        /**
         * Creates a paired view from confirmed swing indicators.
         *
         * @param highs confirmed swing-high indicator
         * @param lows  confirmed swing-low indicator
         * @since 0.23.1
         */
        public Pair(final RecentSwingIndicator highs, final RecentSwingIndicator lows) {
            this(Method.CUSTOM, highs, lows);
        }

        private Pair(final Method method, final RecentSwingIndicator highs, final RecentSwingIndicator lows) {
            this.method = Objects.requireNonNull(method, "method");
            this.highs = Objects.requireNonNull(highs, "highs");
            this.lows = Objects.requireNonNull(lows, "lows");
            this.series = highs.getBarSeries();
            if (!IndicatorUtils.isSameSeries(series, lows.getBarSeries())) {
                throw new IllegalArgumentException("highs and lows must share the same bar series instance");
            }
        }

        /**
         * @return swing methodology or {@link Method#CUSTOM}
         * @since 0.23.1
         */
        public Method method() {
            return method;
        }

        /**
         * @return confirmed swing-high indicator
         * @since 0.23.1
         */
        public RecentSwingIndicator highs() {
            return highs;
        }

        /**
         * @return confirmed swing-low indicator
         * @since 0.23.1
         */
        public RecentSwingIndicator lows() {
            return lows;
        }

        /**
         * Returns the latest high, preferring the developing terminal high when the
         * active leg is rising.
         *
         * @param index evaluation index
         * @return latest confirmed or provisional high
         * @since 0.23.1
         */
        public Optional<SwingPoint> latestHigh(final int index) {
            final Optional<SwingPoint> forming = formingPoint(index);
            return forming.filter(point -> point.type() == SwingPivotType.HIGH)
                    .or(() -> confirmedPoint(highs, SwingPivotType.HIGH, index));
        }

        /**
         * Returns the latest low, preferring the developing terminal low when the
         * active leg is falling.
         *
         * @param index evaluation index
         * @return latest confirmed or provisional low
         * @since 0.23.1
         */
        public Optional<SwingPoint> latestLow(final int index) {
            final Optional<SwingPoint> forming = formingPoint(index);
            return forming.filter(point -> point.type() == SwingPivotType.LOW)
                    .or(() -> confirmedPoint(lows, SwingPivotType.LOW, index));
        }

        /**
         * Returns the active leg's unconfirmed extreme after the latest confirmed
         * pivot.
         *
         * @param index evaluation index
         * @return provisional terminal extreme, or empty before a direction is
         *         established
         * @since 0.23.1
         */
        public Optional<SwingPoint> formingPoint(final int index) {
            if (series == null || series.isEmpty() || index < series.getBeginIndex() || index > series.getEndIndex()) {
                return Optional.empty();
            }
            final Optional<SwingPoint> confirmedHigh = confirmedPoint(highs, SwingPivotType.HIGH, index);
            final Optional<SwingPoint> confirmedLow = confirmedPoint(lows, SwingPivotType.LOW, index);
            final SwingPoint lastConfirmed = laterPoint(confirmedHigh.orElse(null), confirmedLow.orElse(null));
            if (lastConfirmed == null || lastConfirmed.pivotIndex() >= index) {
                return Optional.empty();
            }

            final SwingPivotType formingType = lastConfirmed.type() == SwingPivotType.HIGH ? SwingPivotType.LOW
                    : SwingPivotType.HIGH;
            final Indicator<Num> priceIndicator = formingType == SwingPivotType.HIGH ? highs.getPriceIndicator()
                    : lows.getPriceIndicator();
            int extremeIndex = -1;
            Num extremePrice = null;
            for (int current = lastConfirmed.pivotIndex() + 1; current <= index; current++) {
                final Num price = priceIndicator.getValue(current);
                if (!Num.isFinite(price)) {
                    continue;
                }
                if (extremePrice == null || formingType == SwingPivotType.HIGH && price.isGreaterThan(extremePrice)
                        || formingType == SwingPivotType.LOW && price.isLessThan(extremePrice)) {
                    extremeIndex = current;
                    extremePrice = price;
                }
            }
            if (extremeIndex < 0
                    || formingType == SwingPivotType.HIGH && !extremePrice.isGreaterThan(lastConfirmed.price())
                    || formingType == SwingPivotType.LOW && !extremePrice.isLessThan(lastConfirmed.price())) {
                return Optional.empty();
            }
            return Optional.of(new SwingPoint(extremeIndex, -1, extremePrice, formingType, Confirmation.PROVISIONAL));
        }

        private Optional<SwingPoint> confirmedPoint(final RecentSwingIndicator indicator, final SwingPivotType type,
                final int index) {
            final int pivotIndex = indicator.getLatestSwingIndex(index);
            if (pivotIndex < series.getBeginIndex()) {
                return Optional.empty();
            }
            final Num price = indicator.getPriceIndicator().getValue(pivotIndex);
            if (!Num.isFinite(price)) {
                return Optional.empty();
            }
            final int reportedConfirmation = indicator.getLatestSwingConfirmationIndex(index);
            final int confirmationIndex = reportedConfirmation >= pivotIndex ? reportedConfirmation : index;
            return Optional.of(new SwingPoint(pivotIndex, confirmationIndex, price, type, Confirmation.CONFIRMED));
        }

        private SwingPoint laterPoint(final SwingPoint high, final SwingPoint low) {
            if (high == null) {
                return low;
            }
            if (low == null) {
                return high;
            }
            if (high.pivotIndex() != low.pivotIndex()) {
                return high.pivotIndex() > low.pivotIndex() ? high : low;
            }
            if (high.confirmationIndex() != low.confirmationIndex()) {
                return high.confirmationIndex() > low.confirmationIndex() ? high : low;
            }
            return null;
        }
    }

    private static final class DetectorRecentSwingIndicator extends AbstractRecentSwingIndicator {

        private final Indicator<List<SwingPivot>> pivots;
        private final SwingPivotType type;

        private DetectorRecentSwingIndicator(final Indicator<Num> priceIndicator,
                final Indicator<List<SwingPivot>> pivots, final SwingPivotType type) {
            super(priceIndicator, 0);
            this.pivots = pivots;
            this.type = type;
        }

        @Override
        protected int detectLatestSwingIndex(final int index) {
            final List<SwingPivot> detected = pivots.getValue(index);
            for (int pivotIndex = detected.size() - 1; pivotIndex >= 0; pivotIndex--) {
                final SwingPivot pivot = detected.get(pivotIndex);
                if (pivot.type() == type) {
                    return pivot.index();
                }
            }
            return -1;
        }
    }

    private static final class DetectorSwingPriceIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> fallbackPrice;
        private final Indicator<List<SwingPivot>> pivots;
        private final SwingPivotType type;
        private long observedRevision;
        private int observedEndIndex;
        private Bar observedLastBar;

        private DetectorSwingPriceIndicator(final Indicator<Num> fallbackPrice,
                final Indicator<List<SwingPivot>> pivots, final SwingPivotType type) {
            super(fallbackPrice);
            this.fallbackPrice = fallbackPrice;
            this.pivots = pivots;
            this.type = type;
            final BarSeries series = getBarSeries();
            this.observedRevision = series.getBarHistoryRevision();
            this.observedEndIndex = series.getEndIndex();
            this.observedLastBar = observedRevision < 0L && !series.isEmpty() ? series.getLastBar() : null;
        }

        @Override
        public Num getValue(final int index) {
            resetIfHistoryChanged();
            return super.getValue(index);
        }

        @Override
        protected Num calculate(final int index) {
            final BarSeries series = getBarSeries();
            if (!series.isEmpty() && index >= series.getBeginIndex() && index <= series.getEndIndex()) {
                final List<SwingPivot> detected = pivots.getValue(series.getEndIndex());
                for (int pivotIndex = detected.size() - 1; pivotIndex >= 0; pivotIndex--) {
                    final SwingPivot pivot = detected.get(pivotIndex);
                    if (pivot.type() == type && pivot.index() == index) {
                        return pivot.price();
                    }
                }
            }
            return fallbackPrice.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return fallbackPrice.getCountOfUnstableBars();
        }

        private synchronized void resetIfHistoryChanged() {
            final BarSeries series = getBarSeries();
            final long currentRevision = series.getBarHistoryRevision();
            final int currentEndIndex = series.getEndIndex();
            final Bar currentLastBar = currentRevision < 0L && !series.isEmpty() ? series.getLastBar() : null;
            final boolean trackedRevisionChanged = currentRevision >= 0L && observedRevision >= 0L
                    && currentRevision != observedRevision;
            final boolean fallbackHistoryChanged = currentRevision < 0L && (currentEndIndex < observedEndIndex
                    || currentEndIndex == observedEndIndex && currentLastBar != observedLastBar);
            if (trackedRevisionChanged || fallbackHistoryChanged) {
                invalidateCache();
            }
            observedRevision = currentRevision;
            observedEndIndex = currentEndIndex;
            observedLastBar = currentLastBar;
        }
    }

    private static final class DetectorPivotIndicator extends CachedIndicator<List<SwingPivot>> {

        private final SwingDetector detector;
        private long observedRevision;
        private int observedEndIndex;
        private Bar observedLastBar;

        private DetectorPivotIndicator(final BarSeries series, final SwingDetector detector) {
            super(series);
            this.detector = detector;
            this.observedRevision = series.getBarHistoryRevision();
            this.observedEndIndex = series.getEndIndex();
            this.observedLastBar = observedRevision < 0L && !series.isEmpty() ? series.getLastBar() : null;
        }

        @Override
        public synchronized List<SwingPivot> getValue(final int index) {
            invalidateIfHistoryChanged();
            final List<SwingPivot> result = super.getValue(index);
            final BarSeries series = getBarSeries();
            observedRevision = series.getBarHistoryRevision();
            observedEndIndex = series.getEndIndex();
            observedLastBar = observedRevision < 0L && !series.isEmpty() ? series.getLastBar() : null;
            return result;
        }

        @Override
        protected List<SwingPivot> calculate(final int index) {
            final BarSeries series = getBarSeries();
            if (series.isEmpty()) {
                return List.of();
            }
            final int clampedIndex = Math.max(series.getBeginIndex(), Math.min(index, series.getEndIndex()));
            return List.copyOf(detector.detectPivots(series, clampedIndex));
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private void invalidateIfHistoryChanged() {
            final BarSeries series = getBarSeries();
            final long currentRevision = series.getBarHistoryRevision();
            final int currentEndIndex = series.getEndIndex();
            final Bar currentLastBar = currentRevision < 0L && !series.isEmpty() ? series.getLastBar() : null;
            final boolean trackedRevisionChanged = currentRevision >= 0L && observedRevision >= 0L
                    && currentRevision != observedRevision;
            final boolean fallbackHistoryChanged = currentRevision < 0L && (currentEndIndex < observedEndIndex
                    || currentEndIndex == observedEndIndex && currentLastBar != observedLastBar);
            if (trackedRevisionChanged || fallbackHistoryChanged) {
                invalidateCache();
            } else {
                observedRevision = currentRevision;
                observedEndIndex = currentEndIndex;
                observedLastBar = currentLastBar;
            }
        }
    }
}
