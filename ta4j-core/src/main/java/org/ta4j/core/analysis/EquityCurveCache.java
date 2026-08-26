/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Bar;
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
 * All curves are computed from a private copy of the series' bar data taken
 * when the first curve is requested (and refreshed whenever structural input
 * changes drop the cache), so in-place edits of retained {@link Bar} references
 * can neither alter nor mix already-produced curves. The copy is deferred so
 * scopes that end up evaluating no equity-curve criteria never pay for it.
 * </p>
 *
 * @since 0.24.2
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
     * @since 0.24.2
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "SharedCurves deliberately hands the cached "
            + "shared curve instances to ExcessReturns so both inputs come from one coherent revision; the curves "
            + "are frozen and immutable once published")
    public record SharedCurves(InvestedInterval investedInterval, CashFlow cashFlow) {
    }

    private final BarSeries series;
    private final TradingRecord tradingRecord;
    private final Map<CurveKey, CashFlow> cashFlows = new ConcurrentHashMap<>();
    private final Map<CurveKey, CumulativePnL> cumulativePnLs = new ConcurrentHashMap<>();
    private final Map<OpenPositionHandling, InvestedInterval> investedIntervals = new ConcurrentHashMap<>();

    /**
     * Active evaluation scopes for this thread; innermost scope first.
     */
    private static final ThreadLocal<ArrayDeque<EquityCurveCache>> ACTIVE_SCOPES = ThreadLocal
            .withInitial(ArrayDeque::new);

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
     * @since 0.24.2
     */
    public static <T> T evaluate(BarSeries series, TradingRecord tradingRecord, Supplier<T> evaluation) {
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(evaluation, "evaluation cannot be null");
        ArrayDeque<EquityCurveCache> scopes = ACTIVE_SCOPES.get();
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
     * Returns the bundle of the innermost active evaluation scope captured for
     * exactly the given inputs, or {@code null} when no matching scope is active.
     * The identity check lets callers safely mix shared and locally constructed
     * curves without restating the scope's inputs.
     *
     * @param series        the bar series to look up, not null
     * @param tradingRecord the trading record to look up, not null
     * @return the matching active bundle, or {@code null}
     * @since 0.24.2
     */
    public static EquityCurveCache current(BarSeries series, TradingRecord tradingRecord) {
        ArrayDeque<EquityCurveCache> scopes = ACTIVE_SCOPES.get();
        for (EquityCurveCache bundle : scopes) {
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
     * series reference are captured for identity checks; a private deep-copy of the
     * bar data backs every curve this bundle computes and is taken lazily when the
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
     * @since 0.24.2
     */
    public CashFlow cashFlow(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
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
     * @since 0.24.2
     */
    public CumulativePnL cumulativePnL(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
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
     * @since 0.24.2
     */
    public InvestedInterval investedInterval(OpenPositionHandling openPositionHandling) {
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
     * @since 0.24.2
     */
    public SharedCurves sharedCurves(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
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
                CashFlow cashFlow = existingFlow != null ? existingFlow : buildUnderStableInputs(() -> {
                    CashFlow flow = CashFlow.overOwnedSnapshot(curveSeries, tradingRecord, 0, curveSeries.getEndIndex(),
                            tradingRecord.getEndIndex(curveSeries), key.equityCurveMode(), key.openPositionHandling());
                    return flow;
                });
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
