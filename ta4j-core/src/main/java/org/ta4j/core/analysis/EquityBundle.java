/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;

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
 * Cached cash flow and cumulative PnL instances are immutable snapshots: their
 * accumulating operations ({@code calculate}, {@code calculatePosition}) throw
 * {@link UnsupportedOperationException} so a consumer cannot alter data shared
 * with other consumers. The bundle captures its inputs by reference: when bars
 * are appended to or removed from the series or new trades are recorded, every
 * memoized curve is dropped and rebuilt on the next request so consumers never
 * observe stale values.
 * </p>
 *
 * @since 0.24.2
 */
public final class EquityBundle {

    private record CurveKey(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
    }

    private final BarSeries series;
    private final TradingRecord tradingRecord;
    private final Map<CurveKey, CashFlow> cashFlows = new ConcurrentHashMap<>();
    private final Map<CurveKey, CumulativePnL> cumulativePnLs = new ConcurrentHashMap<>();
    private final Map<OpenPositionHandling, InvestedInterval> investedIntervals = new ConcurrentHashMap<>();

    /**
     * Fingerprint of the captured inputs at the time the cached curves were built;
     * a change rebuilds every memoized curve on the next request.
     */
    private long inputRevision;

    /**
     * Cost models observed when the cached curves were built; a swap (e.g. through
     * {@code rehydrate}) rebuilds every memoized curve on the next request.
     */
    private CostModel transactionCostModel;
    private CostModel holdingCostModel;

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
        this.inputRevision = currentInputRevision();
        this.transactionCostModel = tradingRecord.getTransactionCostModel();
        this.holdingCostModel = tradingRecord.getHoldingCostModel();
    }

    /**
     * Drops every memoized curve when the captured inputs changed since the cached
     * curves were built (bars appended or removed, trades recorded), so subsequent
     * requests observe up-to-date values instead of stale ones.
     */
    private void invalidateIfInputsChanged() {
        long revision = currentInputRevision();
        CostModel recordTransactionCostModel = tradingRecord.getTransactionCostModel();
        CostModel recordHoldingCostModel = tradingRecord.getHoldingCostModel();
        boolean costModelsChanged = transactionCostModel != recordTransactionCostModel
                || holdingCostModel != recordHoldingCostModel;
        if (revision != inputRevision || costModelsChanged) {
            inputRevision = revision;
            transactionCostModel = recordTransactionCostModel;
            holdingCostModel = recordHoldingCostModel;
            cashFlows.clear();
            cumulativePnLs.clear();
            investedIntervals.clear();
        }
    }

    private long currentInputRevision() {
        long revision = series.getBarHistoryRevision();
        revision = revision * 1_000_003L + series.getEndIndex();
        revision = revision * 1_000_003L + series.getRemovedBarsCount();
        revision = revision * 1_000_003L + tradingRecord.getTrades().size();
        return revision;
    }

    /**
     * Returns the cash flow for the given mode and open position handling,
     * computing it on first request and reusing the same instance afterwards.
     *
     * @param equityCurveMode      the equity curve calculation mode, not null
     * @param openPositionHandling how open positions should be handled, not null
     * @return the shared immutable cash flow snapshot for this key; calling
     *         {@code calculate} or {@code calculatePosition} on it throws
     *         {@link UnsupportedOperationException}
     * @since 0.24.2
     */
    public CashFlow cashFlow(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(equityCurveMode, "equityCurveMode cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        synchronized (this) {
            invalidateIfInputsChanged();
            return cashFlows.computeIfAbsent(new CurveKey(equityCurveMode, openPositionHandling), key -> {
                CashFlow cashFlow = new CashFlow(series, tradingRecord, key.equityCurveMode(),
                        key.openPositionHandling());
                cashFlow.freeze();
                return cashFlow;
            });
        }
    }

    /**
     * Returns the cumulative profit-and-loss series for the given mode and open
     * position handling, computing it on first request and reusing the same
     * instance afterwards.
     *
     * @param equityCurveMode      the equity curve calculation mode, not null
     * @param openPositionHandling how open positions should be handled, not null
     * @return the shared immutable cumulative PnL snapshot for this key; calling
     *         {@code calculate} or {@code calculatePosition} on it throws
     *         {@link UnsupportedOperationException}
     * @since 0.24.2
     */
    public CumulativePnL cumulativePnL(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(equityCurveMode, "equityCurveMode cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        synchronized (this) {
            invalidateIfInputsChanged();
            return cumulativePnLs.computeIfAbsent(new CurveKey(equityCurveMode, openPositionHandling), key -> {
                CumulativePnL cumulativePnL = new CumulativePnL(series, tradingRecord, key.equityCurveMode(),
                        key.openPositionHandling());
                cumulativePnL.freeze();
                return cumulativePnL;
            });
        }
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
        synchronized (this) {
            invalidateIfInputsChanged();
            return investedIntervals.computeIfAbsent(openPositionHandling,
                    handling -> new InvestedInterval(series, tradingRecord, handling));
        }
    }

    /**
     * Returns the series this bundle was created for.
     *
     * @return the captured bar series reference
     * @since 0.24.2
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "getBarSeries intentionally returns the captured series reference; the bundle "
            + "documents that it captures its inputs by reference and requireInputsFor pins callers "
            + "to those exact instances")
    public BarSeries getBarSeries() {
        return series;
    }

    /**
     * Returns the trading record this bundle was created for.
     *
     * @return the captured trading record reference
     * @since 0.24.2
     */
    public TradingRecord getTradingRecord() {
        return tradingRecord;
    }

    /**
     * Ensures that this bundle was created for exactly the given inputs. The bundle
     * captures its inputs by reference, so curve consumers must pass the same
     * instances; a mismatch would otherwise mix datasets between the shared curves
     * and the caller's arguments.
     *
     * @param series        the bar series the curves will be read against
     * @param tradingRecord the trading record the curves were computed from
     * @throws IllegalArgumentException if either input differs from the references
     *                                  this bundle was created with
     * @since 0.24.2
     */
    public void requireInputsFor(BarSeries series, TradingRecord tradingRecord) {
        if (this.series != series || this.tradingRecord != tradingRecord) {
            throw new IllegalArgumentException(
                    "EquityBundle was created for a different BarSeries/TradingRecord combination");
        }
    }
}
