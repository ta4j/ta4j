/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;

/**
 * Shares lazily computed equity curves between the criteria evaluated for one
 * {@code (BarSeries, TradingRecord)} pair.
 *
 * <p>
 * Evaluating several equity-curve-based criteria over the same trading record
 * would otherwise rebuild the identical cash flow once per criterion. Inside an
 * {@link #evaluate(BarSeries, TradingRecord, Supplier) evaluation scope}, every
 * distinct ({@link EquityCurveMode}, {@link OpenPositionHandling}) curve is
 * computed at most once and handed to all participating criteria:
 *
 * <pre>{@code
 * List<AnalysisCriterion> criteria = List.of(new SharpeRatioCriterion(), new MaximumDrawdownCriterion(),
 *         new CalmarRatioCriterion());
 * Map<AnalysisCriterion, Num> report = EquityCurveCache.evaluate(series, record, () -> {
 *     Map<AnalysisCriterion, Num> values = new LinkedHashMap<>();
 *     for (AnalysisCriterion criterion : criteria) {
 *         values.put(criterion, criterion.calculate(series, record));
 *     }
 *     return values;
 * });
 * }</pre>
 *
 * <p>
 * Results are identical to evaluating each criterion on its own; the scope only
 * removes redundant work. {@code BacktestExecutionResult#getTopStrategies} and
 * {@code TradingStatementExecutionResult#rankTradingStatements} open a scope
 * automatically. The built-in drawdown, Calmar, RoMaD, Sharpe, and Sortino
 * criteria participate; a custom {@link AnalysisCriterion} participates by
 * obtaining its curves through
 * {@link #cashFlow(BarSeries, TradingRecord, EquityCurveMode, OpenPositionHandling)}
 * or
 * {@link #cumulativePnL(BarSeries, TradingRecord, EquityCurveMode, OpenPositionHandling)},
 * which build a fresh curve when no matching scope is active. Criteria that do
 * neither simply evaluate as usual.
 * </p>
 *
 * <p>
 * Curves handed out from a scope are shared, read-only snapshots: their
 * accumulating operations ({@code calculate}, {@code calculatePosition}) throw
 * {@link UnsupportedOperationException}. They are computed from a private copy
 * of the series' bar data, so in-place edits of retained {@link Bar} references
 * cannot alter them. Editing, appending, or removing bars, recording trades, or
 * swapping the record's cost models while the scope is open drops every
 * memoized curve; the next request rebuilds it from current contents. Series
 * that do not track bar-history revisions
 * ({@link BarSeries#getBarHistoryRevision()} returns {@code -1}) never reuse
 * curves.
 * </p>
 *
 * @since 0.25.1
 */
public final class EquityCurveCache {

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

    /**
     * Pair of shared curves captured from one coherent input revision by
     * {@link #sharedCurves(EquityCurveMode, OpenPositionHandling)}.
     *
     * @param investedInterval the shared invested-interval indicator
     * @param cashFlow         the shared cash-flow snapshot
     */
    record SharedCurves(InvestedInterval investedInterval, CashFlow cashFlow) {
    }

    private final BarSeries series;
    private final TradingRecord tradingRecord;
    // Guarded by this cache's monitor.
    private final Map<CurveKey, CashFlow> cashFlows = new HashMap<>();
    private final Map<CurveKey, CumulativePnL> cumulativePnLs = new HashMap<>();
    private final Map<OpenPositionHandling, InvestedInterval> investedIntervals = new EnumMap<>(
            OpenPositionHandling.class);

    /**
     * Active evaluation scopes for this thread, innermost first; unset while no
     * scope is open so threads that never evaluate a scope keep no entry.
     */
    private static final ThreadLocal<ArrayDeque<EquityCurveCache>> ACTIVE_SCOPES = new ThreadLocal<>();

    /**
     * Runs the given work with one shared curve cache for exactly these inputs on
     * the current thread. Criteria evaluated inside the work against the same
     * {@code series} and {@code tradingRecord} instances share their curves; nested
     * scopes for other inputs stack and resolve innermost-first. A {@code null}
     * trading record has no curves to share, so the work simply runs without a
     * scope.
     *
     * @param series        the bar series the criteria inside the work read, not
     *                      null
     * @param tradingRecord the trading record the criteria inside the work analyze;
     *                      {@code null} runs the work without a scope
     * @param evaluation    the work to run, not null
     * @param <T>           the work's result type
     * @return the work's result
     * @since 0.25.1
     */
    public static <T> T evaluate(BarSeries series, TradingRecord tradingRecord, Supplier<T> evaluation) {
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
        if (tradingRecord == null) {
            return evaluation.get();
        }
        ArrayDeque<EquityCurveCache> scopes = ACTIVE_SCOPES.get();
        if (scopes == null) {
            scopes = new ArrayDeque<>();
            ACTIVE_SCOPES.set(scopes);
        }
        scopes.push(new EquityCurveCache(series, tradingRecord));
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
     * Returns the scope's shared cash flow when an {@link #evaluate evaluation
     * scope} for exactly these inputs is active on this thread, otherwise a new
     * {@link CashFlow} equal to
     * {@code new CashFlow(series, tradingRecord, equityCurveMode, openPositionHandling)}.
     *
     * @param series               the bar series, not null
     * @param tradingRecord        the trading record, not null
     * @param equityCurveMode      the equity curve calculation mode, not null
     * @param openPositionHandling how open positions should be handled, not null
     * @return the shared read-only curve, or a freshly built one outside a scope
     * @since 0.25.1
     */
    public static CashFlow cashFlow(BarSeries series, TradingRecord tradingRecord, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        EquityCurveCache cache = current(series, tradingRecord);
        return cache != null ? cache.cashFlow(equityCurveMode, openPositionHandling)
                : new CashFlow(series, tradingRecord, equityCurveMode, openPositionHandling);
    }

    /**
     * Returns the scope's shared cumulative PnL when an {@link #evaluate evaluation
     * scope} for exactly these inputs is active on this thread, otherwise a new
     * {@link CumulativePnL} equal to
     * {@code new CumulativePnL(series, tradingRecord, equityCurveMode, openPositionHandling)}.
     *
     * @param series               the bar series, not null
     * @param tradingRecord        the trading record, not null
     * @param equityCurveMode      the equity curve calculation mode, not null
     * @param openPositionHandling how open positions should be handled, not null
     * @return the shared read-only curve, or a freshly built one outside a scope
     * @since 0.25.1
     */
    public static CumulativePnL cumulativePnL(BarSeries series, TradingRecord tradingRecord,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        EquityCurveCache cache = current(series, tradingRecord);
        return cache != null ? cache.cumulativePnL(equityCurveMode, openPositionHandling)
                : new CumulativePnL(series, tradingRecord, equityCurveMode, openPositionHandling);
    }

    /**
     * Returns the cache of the innermost active evaluation scope captured for
     * exactly the given inputs, or {@code null} when no matching scope is active.
     * The identity check lets callers safely mix shared and locally constructed
     * curves without restating the scope's inputs.
     *
     * @param series        the bar series to look up
     * @param tradingRecord the trading record to look up
     * @return the matching active cache, or {@code null}
     */
    static EquityCurveCache current(BarSeries series, TradingRecord tradingRecord) {
        ArrayDeque<EquityCurveCache> scopes = ACTIVE_SCOPES.get();
        if (scopes == null) {
            return null;
        }
        for (EquityCurveCache scope : scopes) {
            if (scope.series == series && scope.tradingRecord == tradingRecord) {
                return scope;
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
     * Creates a cache for the given series and trading record. The record and the
     * series reference are captured for identity checks; a private deep-copy of the
     * bar data backs every curve this cache computes and is taken lazily when the
     * first curve is requested.
     *
     * @param series        the bar series to analyze, not null
     * @param tradingRecord the trading record to analyze, not null
     */
    EquityCurveCache(BarSeries series, TradingRecord tradingRecord) {
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
        while (true) {
            long revision = currentInputRevision();
            CostModel recordTransactionCostModel = tradingRecord.getTransactionCostModel();
            CostModel recordHoldingCostModel = tradingRecord.getHoldingCostModel();
            boolean costModelsChanged = !transactionCostModel.equals(recordTransactionCostModel)
                    || !holdingCostModel.equals(recordHoldingCostModel);
            // A series whose getBarHistoryRevision() returns the documented
            // "unsupported" default of -1 gives no guarantee that replacing a
            // retained bar bumps any observable state: end index and removed-bar
            // count stay fixed while prices change. Reuse must be disabled for
            // such series, so they never hit this fast path.
            if (revision == inputRevision && !costModelsChanged && curveSeries != null && tracksBarHistory()) {
                return;
            }
            // Copy the bars and bounds coherently: the live series may append or
            // prune while the snapshot is taken, which would mix pre- and
            // post-mutation state into one snapshot (bars copied before a prune
            // paired with bounds read after it). Revalidate afterwards and retry.
            BarSeries snapshot = SeriesSnapshots.deepCopy(series);
            if (currentInputRevision() != revision
                    || !tradingRecord.getTransactionCostModel().equals(recordTransactionCostModel)
                    || !tradingRecord.getHoldingCostModel().equals(recordHoldingCostModel)) {
                continue;
            }
            inputRevision = revision;
            transactionCostModel = recordTransactionCostModel;
            holdingCostModel = recordHoldingCostModel;
            curveSeries = snapshot;
            cashFlows.clear();
            cumulativePnLs.clear();
            investedIntervals.clear();
            return;
        }
    }

    private long currentInputRevision() {
        long revision = series.getBarHistoryRevision();
        revision = revision * 1_000_003L + series.getEndIndex();
        revision = revision * 1_000_003L + series.getRemovedBarsCount();
        if (tradingRecord instanceof BaseTradingRecord baseTradingRecord) {
            // Constant-time structural revision: every recorded fill bumps it.
            return revision * 1_000_003L + baseTradingRecord.getModificationCount();
        }
        // Custom TradingRecord implementations expose no modification counter:
        // fall back to hashing their reconstructed positions and open exposure.
        revision = revision * 1_000_003L + tradingRecord.getPositions().hashCode();
        revision = revision * 1_000_003L + tradingRecord.getTrades().hashCode();
        revision = revision * 1_000_003L + tradingRecord.getOpenPositions().hashCode();
        return revision * 1_000_003L + Objects.hashCode(tradingRecord.getCurrentPosition());
    }

    /**
     * Runs the given curve factory and only accepts its result when the inputs did
     * not change while it ran: the cache lock does not block the record's
     * independent write lock, so a fill recorded mid-sweep would otherwise publish
     * a stale or mixed curve under the superseded input revision.
     *
     * @param <T>          the produced curve type
     * @param curveFactory builds the curve from the current snapshot state
     * @return the curve built against stable inputs
     */
    private <T> T buildUnderStableInputs(Supplier<T> curveFactory) {
        while (true) {
            long revision = currentInputRevision();
            CostModel transactionCostModel = tradingRecord.getTransactionCostModel();
            CostModel holdingCostModel = tradingRecord.getHoldingCostModel();
            T curve = curveFactory.get();
            if (currentInputRevision() == revision
                    && tradingRecord.getTransactionCostModel().equals(transactionCostModel)
                    && tradingRecord.getHoldingCostModel().equals(holdingCostModel)) {
                return curve;
            }
            invalidateIfInputsChanged();
        }
    }

    /**
     * Returns whether the captured series supports bar-history revision tracking;
     * series returning the documented unsupported default of {@code -1} cannot
     * prove retained-bar replacement, so cached curves are rebuilt for them.
     */
    private boolean tracksBarHistory() {
        return series.getBarHistoryRevision() >= 0;
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
     */
    CashFlow cashFlow(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(equityCurveMode, "equityCurveMode cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        synchronized (this) {
            invalidateIfInputsChanged();
            CurveKey key = new CurveKey(equityCurveMode, openPositionHandling);
            CashFlow existing = cashFlows.get(key);
            if (existing != null) {
                return existing;
            }
            CashFlow cashFlow = buildUnderStableInputs(
                    () -> CashFlow.overOwnedSnapshot(curveSeries, tradingRecord, 0, curveSeries.getEndIndex(),
                            tradingRecord.getEndIndex(curveSeries), key.equityCurveMode(), key.openPositionHandling()));
            cashFlow.freeze();
            cashFlows.put(key, cashFlow);
            return cashFlow;
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
     */
    CumulativePnL cumulativePnL(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(equityCurveMode, "equityCurveMode cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        synchronized (this) {
            invalidateIfInputsChanged();
            CurveKey key = new CurveKey(equityCurveMode, openPositionHandling);
            CumulativePnL existing = cumulativePnLs.get(key);
            if (existing != null) {
                return existing;
            }
            CumulativePnL cumulativePnL = buildUnderStableInputs(
                    () -> CumulativePnL.overOwnedSnapshot(curveSeries, tradingRecord,
                            tradingRecord.getEndIndex(curveSeries), key.equityCurveMode(), key.openPositionHandling()));
            cumulativePnL.freeze();
            cumulativePnLs.put(key, cumulativePnL);
            return cumulativePnL;
        }
    }

    /**
     * Returns the invested interval indicator for the given open position handling,
     * computing it on first request and reusing the same instance afterwards.
     *
     * @param openPositionHandling how open positions should be handled, not null
     * @return the shared invested interval instance for this key
     */
    InvestedInterval investedInterval(OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        synchronized (this) {
            invalidateIfInputsChanged();
            InvestedInterval existing = investedIntervals.get(openPositionHandling);
            if (existing != null) {
                return existing;
            }
            InvestedInterval investedInterval = buildUnderStableInputs(
                    () -> InvestedInterval.overOwnedSnapshot(curveSeries, tradingRecord, openPositionHandling));
            investedIntervals.put(openPositionHandling, investedInterval);
            return investedInterval;
        }
    }

    /**
     * Returns the invested-interval and cash-flow curves for excess-return
     * calculation, both resolved against one coherent input revision. Requesting
     * the two curves separately could straddle an input change: a fill recorded
     * between the calls would be reflected in the rebuilt cash flow while the
     * invested interval still reported the superseded in-market state, mixing new
     * equity with stale invested flags.
     *
     * @param equityCurveMode      the equity curve calculation mode, not null
     * @param openPositionHandling how open positions should be handled, not null
     * @return both shared curves captured atomically
     */
    SharedCurves sharedCurves(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(equityCurveMode, "equityCurveMode cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        synchronized (this) {
            while (true) {
                long revision = currentInputRevision();
                CostModel transactionCostModel = tradingRecord.getTransactionCostModel();
                CostModel holdingCostModel = tradingRecord.getHoldingCostModel();
                invalidateIfInputsChanged();
                InvestedInterval existingInterval = investedIntervals.get(openPositionHandling);
                CurveKey key = new CurveKey(equityCurveMode, openPositionHandling);
                CashFlow existingFlow = cashFlows.get(key);
                if (existingInterval != null && existingFlow != null) {
                    return new SharedCurves(existingInterval, existingFlow);
                }
                InvestedInterval investedInterval = existingInterval != null ? existingInterval
                        : buildUnderStableInputs(() -> InvestedInterval.overOwnedSnapshot(curveSeries, tradingRecord,
                                openPositionHandling));
                CashFlow cashFlow = existingFlow != null ? existingFlow
                        : buildUnderStableInputs(() -> CashFlow.overOwnedSnapshot(curveSeries, tradingRecord, 0,
                                curveSeries.getEndIndex(), tradingRecord.getEndIndex(curveSeries),
                                key.equityCurveMode(), key.openPositionHandling()));
                if (currentInputRevision() == revision
                        && tradingRecord.getTransactionCostModel().equals(transactionCostModel)
                        && tradingRecord.getHoldingCostModel().equals(holdingCostModel)) {
                    cashFlow.freeze();
                    investedIntervals.putIfAbsent(openPositionHandling, investedInterval);
                    cashFlows.putIfAbsent(key, cashFlow);
                    return new SharedCurves(investedIntervals.get(openPositionHandling), cashFlows.get(key));
                }
                invalidateIfInputsChanged();
            }
        }
    }

    /**
     * Returns the series this cache was created for.
     *
     * @return the captured bar series reference
     */
    BarSeries getBarSeries() {
        return series;
    }

    /**
     * Returns the trading record this cache was created for.
     *
     * @return the captured trading record reference
     */
    TradingRecord getTradingRecord() {
        return tradingRecord;
    }

}
