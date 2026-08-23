/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;

/**
 * Shared, lazily computed equity analysis curves for one
 * {@code (BarSeries, TradingRecord)} pair.
 *
 * <p>
 * Evaluating several equity-curve-based criteria over the same trading record
 * rebuilds the identical cash flow once per criterion. A bundle computes every
 * distinct curve at most once and hands the same instance to all consumers, so
 * a batch evaluation performs a single sweep per requested
 * ({@link EquityCurveMode}, {@link OpenPositionHandling}) combination instead
 * of one sweep per criterion.
 * </p>
 *
 * <p>
 * Typical use is indirect, through batch evaluation entry points that create
 * and distribute a bundle internally. The curves are memoized by their
 * configuration key; pulling the same key twice returns the identical instance.
 * </p>
 *
 * @since 0.24.2
 */
public final class EquityBundle {

    private record CurveKey(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
    }

    private final BarSeries series;
    private final TradingRecord tradingRecord;
    private final Map<CurveKey, CashFlow> cashFlows = new HashMap<>();
    private final Map<CurveKey, CumulativePnL> cumulativePnLs = new HashMap<>();
    private final Map<OpenPositionHandling, InvestedInterval> investedIntervals = new EnumMap<>(
            OpenPositionHandling.class);

    /**
     * Creates a bundle for the given series and trading record. Neither the series
     * nor the record is copied: the curves computed on demand are constructed from
     * these exact inputs, mirroring direct indicator construction.
     *
     * @param series        the bar series to analyze, not null
     * @param tradingRecord the trading record to analyze, not null
     * @since 0.24.2
     */
    public EquityBundle(BarSeries series, TradingRecord tradingRecord) {
        this.series = Objects.requireNonNull(series, "series cannot be null");
        this.tradingRecord = Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
    }

    /**
     * Returns the cash flow for the given mode and open position handling,
     * computing it on first request and reusing the same instance afterwards.
     *
     * @param equityCurveMode      the equity curve calculation mode, not null
     * @param openPositionHandling how open positions should be handled, not null
     * @return the shared cash flow instance for this key
     * @since 0.24.2
     */
    public CashFlow cashFlow(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(equityCurveMode, "equityCurveMode cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        return cashFlows.computeIfAbsent(new CurveKey(equityCurveMode, openPositionHandling),
                key -> new CashFlow(series, tradingRecord, key.equityCurveMode(), key.openPositionHandling()));
    }

    /**
     * Returns the cumulative profit-and-loss series for the given mode and open
     * position handling, computing it on first request and reusing the same
     * instance afterwards.
     *
     * @param equityCurveMode      the equity curve calculation mode, not null
     * @param openPositionHandling how open positions should be handled, not null
     * @return the shared cumulative PnL instance for this key
     * @since 0.24.2
     */
    public CumulativePnL cumulativePnL(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(equityCurveMode, "equityCurveMode cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        return cumulativePnLs.computeIfAbsent(new CurveKey(equityCurveMode, openPositionHandling),
                key -> new CumulativePnL(series, tradingRecord, key.equityCurveMode(), key.openPositionHandling()));
    }

    /**
     * Returns the invested interval indicator for the given open position handling,
     * computing it on first request and reusing the same instance afterwards.
     *
     * @param openPositionHandling how open positions should be handled, not null
     * @return the shared invested interval instance for this key
     * @since 0.24.2
     */
    public InvestedInterval investedInterval(OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        return investedIntervals.computeIfAbsent(openPositionHandling,
                handling -> new InvestedInterval(series, tradingRecord, handling));
    }
}
