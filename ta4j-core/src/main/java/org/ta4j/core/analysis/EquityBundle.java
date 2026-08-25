/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.cost.CostModel;

/**
 * Internal shared, lazily computed equity analysis curves for one
 * {@code (BarSeries, TradingRecord)} pair; not part of the public API.
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
 * The bundle is created and distributed internally by
 * {@link #evaluate(BarSeries, TradingRecord, Supplier)}: participating criteria
 * call {@link #current(BarSeries, TradingRecord)} from their regular
 * two-argument calculation and fall back to constructing their own curves when
 * no matching scope is active. The curves are memoized by their configuration
 * key; pulling the same key twice returns the identical instance. Cached cash
 * flow and cumulative PnL instances are immutable snapshots: their accumulating
 * operations ({@code calculate}, {@code calculatePosition}) throw
 * {@link UnsupportedOperationException} so a consumer cannot alter data shared
 * with other consumers. The bundle captures its inputs by reference: when bars
 * are appended to or removed from the series or new trades are recorded, every
 * memoized curve is dropped and rebuilt on the next request so consumers never
 * observe stale values.
 * </p>
 *
 * <p>
 * All curves are computed from a private copy of the series' bar data taken at
 * bundle construction (and refreshed whenever structural input changes drop the
 * cache), so in-place edits of retained {@link Bar} references can neither
 * alter nor mix already-produced curves.
 * </p>
 */
public final class EquityBundle {

    private record CurveKey(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {

        private CurveKey {
            // CashFlow and CumulativePnL compute every REALIZED curve as if open
            // positions were ignored, so different handlings must not produce
            // duplicate cache entries for identical curves.
            if (equityCurveMode == EquityCurveMode.REALIZED) {
                openPositionHandling = OpenPositionHandling.IGNORE;
            }
        }
    }

    private final BarSeries series;
    private final TradingRecord tradingRecord;
    private final Map<CurveKey, CashFlow> cashFlows = new ConcurrentHashMap<>();
    private final Map<CurveKey, CumulativePnL> cumulativePnLs = new ConcurrentHashMap<>();
    private final Map<OpenPositionHandling, InvestedInterval> investedIntervals = new ConcurrentHashMap<>();

    /**
     * Active evaluation scopes for this thread; innermost scope first.
     */
    private static final ThreadLocal<ArrayDeque<EquityBundle>> ACTIVE_SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Evaluates the given work against one shared curve cache scoped to exactly
     * this thread and the given inputs. Criteria running inside the work observe
     * the shared curves via {@link #current(BarSeries, TradingRecord)}; nested
     * evaluations for different inputs stack and resolve innermost-first.
     *
     * @param series        the bar series all calculations inside the work read,
     *                      not null
     * @param tradingRecord the trading record all calculations inside the work
     *                      analyze, not null
     * @param evaluation    the work to run, not null
     * @param <T>           the work's result type
     * @return the work's result
     */
    public static <T> T evaluate(BarSeries series, TradingRecord tradingRecord, Supplier<T> evaluation) {
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
        ArrayDeque<EquityBundle> scopes = ACTIVE_SCOPES.get();
        scopes.push(new EquityBundle(series, tradingRecord));
        try {
            return evaluation.get();
        } finally {
            scopes.pop();
            if (scopes.isEmpty()) {
                ACTIVE_SCOPES.remove();
            }
        }
    }

    /**
     * Returns the bundle of the innermost active evaluation scope captured for
     * exactly the given inputs, or {@code null} when no matching scope is active.
     * The identity check lets callers safely mix shared and locally constructed
     * curves without restating the scope's inputs.
     *
     * @param series        the bar series to look up, not null
     * @param tradingRecord the trading record to look up, not null
     * @return the matching active bundle, or {@code null}
     */
    public static EquityBundle current(BarSeries series, TradingRecord tradingRecord) {
        ArrayDeque<EquityBundle> scopes = ACTIVE_SCOPES.get();
        for (EquityBundle bundle : scopes) {
            if (bundle.series == series && bundle.tradingRecord == tradingRecord) {
                return bundle;
            }
        }
        return null;
    }

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
     * Private copy of the captured series' bar data backing every computed curve;
     * refreshed whenever structural input changes drop the cache.
     */
    private BarSeries curveSeries;

    /**
     * Creates a bundle for the given series and trading record. The record and the
     * series reference are captured for identity checks, while a private copy of
     * the bar data backs every curve this bundle computes, mirroring direct
     * indicator construction at creation time.
     *
     * @param series        the bar series to analyze, not null
     * @param tradingRecord the trading record to analyze, not null
     */
    EquityBundle(BarSeries series, TradingRecord tradingRecord) {
        this.series = Objects.requireNonNull(series, "series cannot be null");
        this.tradingRecord = Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        this.inputRevision = currentInputRevision();
        this.curveSeries = snapshotSeries(this.series);
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
            curveSeries = snapshotSeries(series);
            cashFlows.clear();
            cumulativePnLs.clear();
            investedIntervals.clear();
        }
    }

    private long currentInputRevision() {
        long revision = series.getBarHistoryRevision();
        revision = revision * 1_000_003L + series.getEndIndex();
        revision = revision * 1_000_003L + series.getRemovedBarsCount();
        // Include reconstructed positions and open exposure: AVG_COST may merge
        // fills while keeping the reconstructed trade count unchanged.
        revision = revision * 1_000_003L + tradingRecord.getPositions().hashCode();
        revision = revision * 1_000_003L + tradingRecord.getTrades().hashCode();
        revision = revision * 1_000_003L + tradingRecord.getOpenPositions().hashCode();
        revision = revision * 1_000_003L + Objects.hashCode(tradingRecord.getCurrentPosition());
        return revision;
    }

    /**
     * Creates a series mirroring the given one, but owning deep copies of its bar
     * data so later in-place edits of the original bars cannot reach the curves
     * computed from the copy. The snapshot keeps the source's absolute indexing:
     * when the source has already pruned bars, its retained bars keep their
     * original indices instead of being renumbered from zero.
     */
    private static BarSeries snapshotSeries(final BarSeries barSeries) {
        Objects.requireNonNull(barSeries);
        List<Bar> copiedBars = new ArrayList<>(barSeries.getBarData().size());
        for (Bar bar : barSeries.getBarData()) {
            copiedBars.add(new BaseBar(bar.getTimePeriod(), bar.getBeginTime(), bar.getEndTime(), bar.getOpenPrice(),
                    bar.getHighPrice(), bar.getLowPrice(), bar.getClosePrice(), bar.getVolume(), bar.getAmount(),
                    bar.getTrades()));
        }
        return new BaseBarSeriesBuilder().withName(barSeries.getName())
                .withNumFactory(barSeries.numFactory())
                .withBars(copiedBars)
                .withBeginIndex(Math.max(0, barSeries.getBeginIndex()))
                .withMaxBarCount(barSeries.getMaximumBarCount())
                .build();
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
                CashFlow cashFlow = new CashFlow(curveSeries, tradingRecord, key.equityCurveMode(),
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
                CumulativePnL cumulativePnL = new CumulativePnL(curveSeries, tradingRecord, key.equityCurveMode(),
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
                    handling -> new InvestedInterval(snapshotSeries(curveSeries), tradingRecord, handling));
        }
    }

    /**
     * Returns the series this bundle was created for.
     *
     * @return the captured bar series reference
     */
    BarSeries getBarSeries() {
        return series;
    }

    /**
     * Returns the trading record this bundle was created for.
     *
     * @return the captured trading record reference
     */
    TradingRecord getTradingRecord() {
        return tradingRecord;
    }

}
