/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Utility for post-processing swing sequences.
 *
 * <p>
 * The compressor removes or merges swings that do not meet minimum amplitude or
 * minimum bar-length constraints. Use it to reduce noise before counting waves
 * or generating scenarios, especially when swing detection is noisy or
 * high-frequency.
 *
 * <p>
 * This class does not detect swings itself; it operates on the output of
 * {@link ElliottSwingIndicator} or
 * {@link org.ta4j.core.indicators.elliott.swing.SwingDetector}.
 *
 * @since 0.22.0
 */
public class ElliottSwingCompressor {

    private final Num minimumAmplitude;
    private final int minimumLength;

    /**
     * Creates a compressor with no filtering (all swings are retained).
     *
     * @since 0.22.0
     */
    public ElliottSwingCompressor() {
        this((Num) null, 0);
    }

    /**
     * Creates a compressor with absolute price and bar length thresholds.
     * <p>
     * This is the main constructor for creating a compressor with explicit
     * thresholds. Swings must meet both the minimum amplitude (absolute price
     * delta) and minimum bar length to be retained.
     *
     * @param minimumAmplitude swings must reach this absolute price delta to be
     *                         retained (may be {@code null} to disable amplitude
     *                         filtering)
     * @param minimumBars      swings must cover at least this many bars to be
     *                         retained
     * @since 0.22.0
     */
    public ElliottSwingCompressor(final Num minimumAmplitude, final int minimumBars) {
        this.minimumAmplitude = minimumAmplitude;
        if (minimumBars < 0) {
            throw new IllegalArgumentException("minimumBars must be non-negative");
        }
        this.minimumLength = minimumBars;
    }

    /**
     * Creates a compressor with relative price-based filtering using a price
     * indicator.
     * <p>
     * This convenience constructor creates a compressor that filters swings based
     * on a percentage of the current price from the indicator. The amplitude
     * threshold is calculated as a percentage of the price at the end index of the
     * indicator's series, making it scale appropriately with the asset's price
     * level.
     *
     * @param indicator  the price indicator used to determine the current price
     *                   context
     * @param percentage the percentage of current price to use as minimum amplitude
     *                   threshold in {@link ReturnRepresentation#DECIMAL decimal
     *                   format} (e.g., 0.01 for 1%, 0.005 for 0.5%)
     * @param minBars    the minimum number of bars a swing must span to be retained
     * @since 0.22.0
     */
    public ElliottSwingCompressor(final Indicator<Num> indicator, final double percentage, final int minBars) {
        Objects.requireNonNull(indicator, "indicator cannot be null");
        if (percentage <= 0.0 || percentage > 1.0) {
            throw new IllegalArgumentException("percentage must be in range (0.0, 1.0]");
        }
        if (minBars < 0) {
            throw new IllegalArgumentException("minBars must be non-negative");
        }
        BarSeries series = indicator.getBarSeries();
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }
        int endIndex = series.getEndIndex();
        Num currentPrice = indicator.getValue(endIndex);
        this.minimumAmplitude = currentPrice.multipliedBy(series.numFactory().numOf(percentage));
        this.minimumLength = minBars;
    }

    /**
     * Creates a compressor with relative price-based filtering (1% of current
     * price, 2 bars minimum).
     * <p>
     * This convenience constructor creates a compressor that filters swings based
     * on 1% of the current price and a minimum bar length of 2 bars. This is a
     * common configuration for Elliott Wave analysis that filters out minor price
     * fluctuations and single-bar artifacts while retaining meaningful swing
     * structures.
     * <p>
     * The amplitude threshold is calculated as 1% of the closing price at the end
     * of the series, making it scale appropriately with the asset's price level.
     * For example, for BTC at $30,000, the threshold is $300; at $60,000, it's
     * $600.
     *
     * @param series the bar series used to determine the current price context
     * @since 0.22.0
     */
    public ElliottSwingCompressor(final BarSeries series) {
        Objects.requireNonNull(series, "series cannot be null");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        int endIndex = series.getEndIndex();
        Num currentPrice = closePrice.getValue(endIndex);
        this.minimumAmplitude = currentPrice.multipliedBy(series.numFactory().numOf(0.01));
        this.minimumLength = 2;
    }

    /**
     * Filters the supplied swings according to the configured thresholds.
     *
     * @param swings original swing sequence
     * @return immutable filtered view
     * @since 0.22.0
     */
    public List<ElliottSwing> compress(final List<ElliottSwing> swings) {
        Objects.requireNonNull(swings, "swings cannot be null");
        if (swings.isEmpty()) {
            return List.of();
        }
        final List<ElliottSwing> filtered = new ArrayList<>(swings.size());
        for (final ElliottSwing swing : swings) {
            if (swing == null) {
                continue;
            }
            if (minimumAmplitude != null && swing.amplitude().isLessThan(minimumAmplitude)) {
                continue;
            }
            if (minimumLength > 0 && swing.length() < minimumLength) {
                continue;
            }
            filtered.add(swing);
        }
        if (filtered.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(filtered);
    }
}
