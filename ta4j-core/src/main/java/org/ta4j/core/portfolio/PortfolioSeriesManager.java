/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.ExitOnRunEndModel;
import org.ta4j.core.backtest.PositionSizer;
import org.ta4j.core.backtest.RunWindowBarSeries;
import org.ta4j.core.backtest.TradeExecutionModel;
import org.ta4j.core.backtest.TradeExecutionModel.ExecutionTarget;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.portfolio.PortfolioSnapshot.RebalanceStatus;

/**
 * Runs deterministic static target-weight backtests over a
 * {@link PortfolioSeries}.
 *
 * <p>
 * Like ta4j's single-series {@code BarSeriesManager}, the constructor owns the
 * series-level configuration (data and transaction costs) while each
 * {@code run(...)} call supplies the allocation, starting capital, and
 * rebalance schedule, which makes side-by-side comparisons natural:
 * </p>
 *
 * <pre>{@code
 * PortfolioSeriesManager manager = new PortfolioSeriesManager(new PortfolioSeries(spy, tlt),
 *         new LinearTransactionCostModel(0.001));
 * PortfolioExecutionResult buyAndHold = manager.run(new PortfolioAllocation(Map.of("SPY", 0.6, "TLT", 0.4)), 10_000);
 * PortfolioExecutionResult monthly = manager.run(new PortfolioAllocation(Map.of("SPY", 0.6, "TLT", 0.4)), 10_000,
 *         RebalancePolicy.firstBarOf(ChronoUnit.MONTHS, ZoneOffset.UTC));
 * }</pre>
 *
 * <p>
 * <b>Accounting model.</b> The portfolio starts with the initial cash only and
 * trades exclusively on bars selected by the {@link RebalancePolicy}. Trades
 * execute at the aligned close price with fractional, long-only units, and all
 * prices share one quote currency. On a rebalance the manager solves for the
 * post-cost portfolio value, so every asset reaches its target weight and cash
 * reaches the allocation's cash weight after transaction costs are paid. Cash
 * never becomes negative: sells that release cash execute first, sells whose
 * costs consume cash next, and buys are scaled down uniformly when cash cannot
 * cover them. Trades smaller than one part per billion of portfolio value are
 * treated as already at target, so numerical residue never triggers fees. Every
 * snapshot reports whether its rebalance {@link RebalanceStatus completed, was
 * partial, or was skipped}.
 * </p>
 *
 * <p>
 * Transaction costs are computed per trade with
 * {@link CostModel#calculate(Num, Num)} (price and units). The post-cost solve
 * is exact for proportional models and converges in a few fixed-point steps;
 * for fixed or minimum fees it picks the largest post-cost value found, always
 * considering the option of leaving the current holdings untouched. Pair fixed
 * fees with {@link RebalancePolicy#whenDriftExceeds(double)} so negligible
 * drift is not corrected at full fee.
 * </p>
 *
 * @since 0.25.1
 */
public final class PortfolioSeriesManager {

    private static final int MAX_SOLVER_ITERATIONS = 200;
    private static final int MAX_FIXED_POINT_ITERATIONS = 32;

    private final PortfolioSeries series;
    private final PortfolioSeriesView view;
    private final CostModel transactionCostModel;

    /**
     * Creates a manager with zero transaction costs.
     *
     * @param series aligned portfolio series
     * @since 0.25.1
     */
    public PortfolioSeriesManager(PortfolioSeries series) {
        this(series, new ZeroCostModel());
    }

    /**
     * Creates a manager.
     *
     * @param series               aligned portfolio series
     * @param transactionCostModel transaction cost model applied to every trade
     * @since 0.25.1
     */
    public PortfolioSeriesManager(PortfolioSeries series, CostModel transactionCostModel) {
        this((PortfolioSeriesView) series, transactionCostModel);
    }

    /**
     * Creates a strategy manager over an aligned snapshot or live view with zero
     * transaction costs. Allocation runs require a {@link PortfolioSeries}.
     *
     * @param view aligned portfolio view
     * @since 0.27.1
     */
    public PortfolioSeriesManager(PortfolioSeriesView view) {
        this(view, new ZeroCostModel());
    }

    /**
     * Creates a strategy manager over an aligned view.
     *
     * @param view                 aligned portfolio view
     * @param transactionCostModel transaction cost model applied to each trade
     * @since 0.27.1
     */
    public PortfolioSeriesManager(PortfolioSeriesView view, CostModel transactionCostModel) {
        this.view = Objects.requireNonNull(view, "view");
        this.series = view instanceof PortfolioSeries snapshot ? snapshot : null;
        this.transactionCostModel = Objects.requireNonNull(transactionCostModel, "transactionCostModel");
    }

    /**
     * @return managed portfolio series
     * @throws IllegalStateException if the managed view is not a snapshot
     * @since 0.25.1
     */
    public PortfolioSeries getPortfolioSeries() {
        if (series == null) {
            throw new IllegalStateException("Allocation runs require a PortfolioSeries snapshot");
        }
        return series;
    }

    /**
     * @return transaction cost model
     * @since 0.25.1
     */
    public CostModel getTransactionCostModel() {
        return transactionCostModel;
    }

    /**
     * Runs a buy-and-hold portfolio: one investment at the first aligned bar, no
     * later rebalances.
     *
     * @param allocation  target allocation
     * @param initialCash positive starting cash
     * @return execution result
     * @since 0.25.1
     */
    public PortfolioExecutionResult run(PortfolioAllocation allocation, Num initialCash) {
        return run(allocation, initialCash, RebalancePolicy.atStart());
    }

    /**
     * Runs a buy-and-hold portfolio: one investment at the first aligned bar, no
     * later rebalances.
     *
     * @param allocation  target allocation
     * @param initialCash positive starting cash
     * @return execution result
     * @since 0.25.1
     */
    public PortfolioExecutionResult run(PortfolioAllocation allocation, Number initialCash) {
        return run(allocation, initialCash, RebalancePolicy.atStart());
    }

    /**
     * Runs a portfolio that trades toward its targets on the bars selected by
     * {@code rebalancePolicy}. The policy also schedules the initial investment:
     * the portfolio holds only cash until the first selected bar.
     *
     * @param allocation      target allocation
     * @param initialCash     positive starting cash
     * @param rebalancePolicy rebalance schedule
     * @return execution result
     * @since 0.25.1
     */
    public PortfolioExecutionResult run(PortfolioAllocation allocation, Number initialCash,
            RebalancePolicy rebalancePolicy) {
        Objects.requireNonNull(initialCash, "initialCash");
        return run(allocation, getPortfolioSeries().numFactory().numOf(initialCash), rebalancePolicy);
    }

    /**
     * Runs a portfolio that trades toward its targets on the bars selected by
     * {@code rebalancePolicy}. The policy also schedules the initial investment:
     * the portfolio holds only cash until the first selected bar.
     *
     * @param allocation      target allocation
     * @param initialCash     positive starting cash
     * @param rebalancePolicy rebalance schedule
     * @return execution result
     * @since 0.25.1
     */
    public PortfolioExecutionResult run(PortfolioAllocation allocation, Num initialCash,
            RebalancePolicy rebalancePolicy) {
        getPortfolioSeries();
        return new Execution(allocation, initialCash, rebalancePolicy).run();
    }

    /**
     * Runs a strategy with a fixed amount, executing on each asset's current close
     * and closing any remaining position at that asset's last processed close.
     *
     * @param strategy strategy to evaluate
     * @param amount   fixed entry amount
     * @return per-asset trading records
     * @since 0.27.1
     */
    public PortfolioTradingRecord run(PortfolioStrategy strategy, Num amount) {
        validateAmount(amount);
        return runPortfolio(strategy, null, new TradeOnCurrentCloseModel(), amount);
    }

    /**
     * Runs an asset-scoped strategy over aligned bars. Assets without a bar at an
     * aligned time are skipped. Execution callbacks for all available bars run
     * before any strategy decision at that time. A trade targeting a later source
     * bar becomes visible when the portfolio reaches that bar's end time; in
     * particular, next-open executions are observed at the target bar's end.
     * Accepted pending entry orders reserve position-limit capacity until filled,
     * rejected, or expired. The execution model must report its pending orders via
     * {@link TradeExecutionModel#hasPendingOrder(TradingRecord)}.
     *
     * <p>
     * An {@link ExitOnRunEndModel} finalizes each asset at its own last processed
     * close. Sizing is called only for entries. Its single-series strategy context
     * exposes this portfolio strategy projected onto the asset. Execution and
     * sizing cannot see bars beyond the captured run window.
     * </p>
     *
     * @param strategy       strategy to evaluate
     * @param positionSizer  entry position sizing
     * @param executionModel execution model, shared across asset records
     * @return per-asset trading records
     * @since 0.27.1
     */
    public PortfolioTradingRecord run(PortfolioStrategy strategy, PositionSizer positionSizer,
            TradeExecutionModel executionModel) {
        Objects.requireNonNull(positionSizer, "positionSizer");
        return runPortfolio(strategy, positionSizer, executionModel, null);
    }

    private PortfolioTradingRecord runPortfolio(PortfolioStrategy strategy, PositionSizer positionSizer,
            TradeExecutionModel executionModel, Num fixedAmount) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(executionModel, "executionModel");
        strategy.bind(view);
        TradeType tradeType = strategy.getStartingType();
        CostModel holdingCost = new ZeroCostModel();
        List<DeferredTradingRecord> deferredRecords = new ArrayList<>();
        boolean[] completed = { false };
        BasePortfolioTradingRecord baseRecord = new BasePortfolioTradingRecord(view, asset -> {
            if (completed[0]) {
                return new BaseTradingRecord(tradeType, transactionCostModel, holdingCost);
            }
            DeferredTradingRecord deferred = new DeferredTradingRecord(tradeType, transactionCostModel, holdingCost);
            deferredRecords.add(deferred);
            return deferred;
        });
        List<AssetExecution> assets = new ArrayList<>();
        int begin = view.getBeginIndex();
        int end = view.getEndIndex();
        if (end < begin) {
            completed[0] = true;
            return baseRecord;
        }
        TradeExecutionModel closingModel = new ExitOnRunEndModel(executionModel);
        PortfolioTradingRecord record = new RunPortfolioRecord(baseRecord, assets, closingModel);
        for (String asset : view.assets()) {
            int sourceEnd = view.getSourceIndex(asset, end);
            if (sourceEnd >= 0) {
                RunWindowBarSeries window = new RunWindowBarSeries(view.getBarSeries(asset), sourceEnd);
                Strategy projection = positionSizer == null ? null : projectStrategy(strategy, record, asset);
                assets.add(new AssetExecution(asset, window, (DeferredTradingRecord) baseRecord.record(asset),
                        projection, sourceEnd));
            }
        }
        for (int index = begin;; index++) {
            for (AssetExecution asset : assets) {
                asset.currentIndex = -1;
                if (!view.hasBar(asset.name, index)) {
                    continue;
                }
                asset.currentIndex = view.getSourceIndex(asset.name, index);
                asset.lastProcessedIndex = asset.currentIndex;
                asset.window.markBarProcessed();
                asset.record.advance(asset.currentIndex);
                closingModel.onBar(asset.currentIndex, asset.record, asset.window);
            }
            for (AssetExecution asset : assets) {
                if (asset.currentIndex < 0 || asset.record.hasDeferredTrades()
                        || closingModel.hasPendingOrder(asset.record)) {
                    continue;
                }
                if (asset.record.isClosed()) {
                    if (strategy.shouldEnter(asset.name, index, record)) {
                        Num amount = positionSizer == null ? fixedAmount
                                : entryAmount(positionSizer, closingModel, asset.projection, asset.record, asset.window,
                                        asset.currentIndex);
                        closingModel.execute(asset.currentIndex, asset.record, asset.window, amount);
                    }
                } else if (strategy.shouldExit(asset.name, index, record)) {
                    closingModel.execute(asset.currentIndex, asset.record, asset.window,
                            asset.record.getCurrentPosition().amount());
                }
            }
            for (AssetExecution asset : assets) {
                if (asset.currentIndex == asset.sourceEnd) {
                    closingModel.onRunEnd(asset.currentIndex, asset.record, asset.window);
                    asset.record.finish();
                    asset.finished = true;
                }
            }
            if (index == end) {
                break;
            }
        }
        for (AssetExecution asset : assets) {
            if (!asset.finished && asset.lastProcessedIndex >= 0) {
                asset.record.discardUnprocessedTrades();
                closingModel.onRunEnd(asset.lastProcessedIndex, asset.record, asset.window);
                asset.record.finish();
            }
        }
        for (DeferredTradingRecord deferred : deferredRecords) {
            if (!deferred.finished) {
                deferred.finish();
            }
        }
        deferredRecords.clear();
        completed[0] = true;
        return baseRecord;
    }

    private Num entryAmount(PositionSizer sizer, TradeExecutionModel model, Strategy strategy, TradingRecord record,
            BarSeries window, int sourceIndex) {
        TradeType type = record.getStartingType();
        ExecutionTarget target = model.estimateEntryTarget(sourceIndex, window, type);
        if (target == null) {
            target = new ExecutionTarget(sourceIndex, window.getBar(sourceIndex).getClosePrice());
        }
        Num amount = sizer.amount(new PositionSizer.Context(sourceIndex, target.index(), target.price(), strategy,
                window, type, record, record.getTransactionCostModel(), record.getHoldingCostModel()));
        validateAmount(amount);
        return amount;
    }

    private static void validateAmount(Num amount) {
        if (amount == null || amount.isNaN() || amount.isNegativeOrZero() || !Double.isFinite(amount.doubleValue())) {
            throw new IllegalArgumentException("Amount must be positive and finite");
        }
    }

    private static final class AssetExecution {
        private final String name;
        private final RunWindowBarSeries window;
        private final DeferredTradingRecord record;
        private final Strategy projection;
        private final int sourceEnd;
        private int currentIndex = -1;
        private int lastProcessedIndex = -1;
        private boolean finished;

        private AssetExecution(String name, RunWindowBarSeries window, DeferredTradingRecord record,
                Strategy projection, int sourceEnd) {
            this.name = name;
            this.window = window;
            this.record = record;
            this.projection = projection;
            this.sourceEnd = sourceEnd;
        }
    }

    private static final class RunPortfolioRecord implements PortfolioTradingRecord {
        private final BasePortfolioTradingRecord delegate;
        private final List<AssetExecution> assets;
        private final TradeExecutionModel model;

        private RunPortfolioRecord(BasePortfolioTradingRecord delegate, List<AssetExecution> assets,
                TradeExecutionModel model) {
            this.delegate = delegate;
            this.assets = assets;
            this.model = model;
        }

        @Override
        public PortfolioSeriesView series() {
            return delegate.series();
        }

        @Override
        public TradingRecord record(String asset) {
            return delegate.record(asset);
        }

        @Override
        public int openPositionCount() {
            return delegate.openPositionCount();
        }

        @Override
        public int pendingEntryCount() {
            int count = 0;
            for (AssetExecution asset : assets) {
                if (asset.record.isClosed()
                        && (asset.record.hasDeferredTrades() || model.hasPendingOrder(asset.record))) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Map<String, org.ta4j.core.Position> openPositions() {
            return delegate.openPositions();
        }
    }

    private static final class DeferredTradingRecord extends BaseTradingRecord {
        @Serial
        private static final long serialVersionUID = 1L;

        private record DeferredTrade(int index, Runnable apply) {
        }

        private transient List<DeferredTrade> deferred;
        private int processedIndex = -1;
        private boolean finished;

        private DeferredTradingRecord(TradeType type, CostModel transactionCost, CostModel holdingCost) {
            super(type, transactionCost, holdingCost);
        }

        private boolean hasDeferredTrades() {
            return deferred != null && !deferred.isEmpty();
        }

        private void enqueue(int index, Runnable apply) {
            if (deferred == null) {
                deferred = new ArrayList<>();
            }
            deferred.add(new DeferredTrade(index, apply));
        }

        private void advance(int index) {
            processedIndex = index;
            if (deferred == null) {
                return;
            }
            for (int i = 0; i < deferred.size();) {
                if (deferred.get(i).index() <= index) {
                    deferred.remove(i).apply().run();
                } else {
                    i++;
                }
            }
            if (deferred.isEmpty()) {
                deferred = null;
            }
        }

        private void discardUnprocessedTrades() {
            deferred = null;
        }

        private void finish() {
            if (hasDeferredTrades()) {
                throw new IllegalStateException("Execution model scheduled a trade beyond the asset's run window");
            }
            finished = true;
        }

        @Override
        public void operate(int index, Num price, Num amount) {
            if (!finished && index > processedIndex) {
                enqueue(index, () -> super.operate(index, price, amount));
            } else {
                super.operate(index, price, amount);
            }
        }

        @Override
        public void operate(Trade trade) {
            int latestIndex = trade.getIndex();
            for (var fill : Trade.executionFillsOf(trade)) {
                latestIndex = Math.max(latestIndex, fill.index());
            }
            if (!finished && latestIndex > processedIndex) {
                enqueue(latestIndex, () -> super.operate(trade));
            } else {
                super.operate(trade);
            }
        }

        @Override
        public boolean enter(int index, Num price, Num amount) {
            if (!finished && index > processedIndex) {
                if (!isClosed() || hasDeferredTrades()) {
                    return false;
                }
                enqueue(index, () -> super.enter(index, price, amount));
                return true;
            }
            return super.enter(index, price, amount);
        }

        @Override
        public boolean exit(int index, Num price, Num amount) {
            if (!finished && index > processedIndex) {
                if (isClosed() || hasDeferredTrades()) {
                    return false;
                }
                enqueue(index, () -> super.exit(index, price, amount));
                return true;
            }
            return super.exit(index, price, amount);
        }
    }

    private Strategy projectStrategy(PortfolioStrategy strategy, PortfolioTradingRecord record, String asset) {
        Rule entry = (sourceIndex, ignored) -> projectedDecision(strategy, record, asset, sourceIndex, true);
        Rule exit = (sourceIndex, ignored) -> projectedDecision(strategy, record, asset, sourceIndex, false);
        return new BaseStrategy(strategy.getName(), entry, exit, strategy.getUnstableBars(),
                strategy.getStartingType());
    }

    private boolean projectedDecision(PortfolioStrategy strategy, PortfolioTradingRecord record, String asset,
            int sourceIndex, boolean entry) {
        BarSeries assetSeries = view.getBarSeries(asset);
        if (sourceIndex < assetSeries.getBeginIndex() || sourceIndex > assetSeries.getEndIndex()) {
            return false;
        }
        Instant time = assetSeries.getBar(sourceIndex).getEndTime();
        int low = view.getBeginIndex();
        int high = view.getEndIndex();
        while (low <= high) {
            int middle = low + (high - low) / 2;
            int comparison = view.getEndTime(middle).compareTo(time);
            if (comparison < 0) {
                low = middle + 1;
            } else if (comparison > 0) {
                high = middle - 1;
            } else {
                return view.hasBar(asset, middle) && (entry ? strategy.shouldEnter(asset, middle, record)
                        : strategy.shouldExit(asset, middle, record));
            }
        }
        return false;
    }

    /** One run's mutable state; assets are addressed by portfolio position. */
    private final class Execution {

        private final PortfolioAllocation allocation;
        private final Num initialCash;
        private final RebalancePolicy rebalancePolicy;
        private final NumFactory numFactory;
        private final List<String> assets;
        private final Num[] targetWeights;
        private final Num zero;

        private Execution(PortfolioAllocation allocation, Num initialCash, RebalancePolicy rebalancePolicy) {
            this.allocation = Objects.requireNonNull(allocation, "allocation");
            this.rebalancePolicy = Objects.requireNonNull(rebalancePolicy, "rebalancePolicy");
            this.numFactory = series.numFactory();
            this.assets = series.getAssets();
            this.zero = numFactory.zero();
            this.initialCash = series.toPortfolioNum(Objects.requireNonNull(initialCash, "initialCash"));
            if (!Num.isFinite(this.initialCash) || this.initialCash.isNegativeOrZero()) {
                throw new IllegalArgumentException("initialCash must be finite and > 0 but was " + initialCash);
            }
            Set<String> assetSet = new HashSet<>(assets);
            for (String asset : allocation.getTargetWeights().keySet()) {
                if (!assetSet.contains(asset)) {
                    throw new IllegalArgumentException(
                            "allocation asset is not in the portfolio series: " + asset + " (assets: " + assets + ")");
                }
            }
            this.targetWeights = new Num[assets.size()];
            for (int position = 0; position < assets.size(); position++) {
                targetWeights[position] = series.toPortfolioNum(allocation.getTargetWeight(assets.get(position)));
            }
        }

        private PortfolioExecutionResult run() {
            int assetCount = assets.size();
            Num[] units = new Num[assetCount];
            Arrays.fill(units, zero);
            Map<String, Num> holdings = toMap(units);
            Num cash = initialCash;
            Num previousValue = initialCash;
            List<PortfolioSnapshot> snapshots = new ArrayList<>(series.getBarCount());
            Num[] prices = new Num[assetCount];
            List<Instant> endTimes = series.getEndTimes();

            for (int index = 0; index < series.getBarCount(); index++) {
                pricesAt(index, prices);
                Num preTradeValue = portfolioValue(cash, units, prices);
                Num transactionCost = zero;
                Num tradedNotional = zero;
                RebalanceStatus status = RebalanceStatus.NOT_SCHEDULED;

                if (rebalancePolicy.shouldRebalance(
                        new RebalancePolicy.Context(series, index, units, cash, preTradeValue, targetWeights))) {
                    Rebalance rebalance = new Rebalance(prices, units, cash, preTradeValue);
                    rebalance.execute();
                    units = rebalance.units;
                    cash = rebalance.cash;
                    transactionCost = rebalance.transactionCost;
                    tradedNotional = rebalance.tradedNotional;
                    status = rebalance.status();
                    holdings = toMap(units);
                }

                Num portfolioValue = portfolioValue(cash, units, prices);
                Num periodReturn = previousValue.isZero() ? zero
                        : portfolioValue.minus(previousValue).dividedBy(previousValue);
                Num turnover = preTradeValue.isZero() ? zero : tradedNotional.dividedBy(preTradeValue);
                snapshots.add(new PortfolioSnapshot(index, endTimes.get(index), toMap(prices), holdings, cash,
                        portfolioValue, periodReturn, transactionCost, tradedNotional, turnover, status));
                previousValue = portfolioValue;
            }

            return new PortfolioExecutionResult(series, allocation, initialCash, snapshots);
        }

        private void pricesAt(int index, Num[] prices) {
            for (int position = 0; position < prices.length; position++) {
                Num price = series.closePrice(position, index);
                if (!Num.isFinite(price) || price.isNegativeOrZero()) {
                    throw new IllegalArgumentException("close price for " + assets.get(position) + " at "
                            + series.getEndTimes().get(index) + " must be finite and > 0 but was " + price);
                }
                prices[position] = price;
            }
        }

        private Num portfolioValue(Num cash, Num[] units, Num[] prices) {
            Num value = cash;
            for (int position = 0; position < units.length; position++) {
                value = value.plus(prices[position].multipliedBy(units[position]));
            }
            return value;
        }

        private Map<String, Num> toMap(Num[] values) {
            Map<String, Num> map = new LinkedHashMap<>();
            for (int position = 0; position < values.length; position++) {
                map.put(assets.get(position), values[position]);
            }
            return map;
        }

        /**
         * Costs of trading {@code gross} notional; trades within the no-trade band are
         * free because they are never executed.
         */
        private Num tradeCost(Num price, Num gross, Num noTradeBand) {
            if (gross.isLessThanOrEqual(noTradeBand)) {
                return zero;
            }
            Num cost = transactionCostModel.calculate(price, gross.dividedBy(price));
            cost = series.toPortfolioNum(Objects.requireNonNull(cost, "transaction cost"));
            if (!Num.isFinite(cost) || cost.isNegative()) {
                throw new IllegalArgumentException("transaction cost must be finite and >= 0 but was " + cost);
            }
            return cost;
        }

        /**
         * Bisection midpoint, or {@code null} once the interval is below the tolerance
         * or numeric precision can no longer split it.
         */
        private Num midpoint(Num low, Num high, Num tolerance) {
            if (high.minus(low).isLessThanOrEqual(tolerance)) {
                return null;
            }
            Num mid = low.plus(high).dividedBy(numFactory.two());
            return mid.isEqual(low) || mid.isEqual(high) ? null : mid;
        }

        /** Trades one aligned bar toward the target weights. */
        private final class Rebalance {

            private final Num[] prices;
            private final Num[] startUnits;
            private final Num preTradeValue;
            private final Num noTradeBand;
            private final Num solverTolerance;
            private final Num[] units;
            private Num cash;
            private Num transactionCost = zero;
            private Num tradedNotional = zero;

            private Rebalance(Num[] prices, Num[] units, Num cash, Num preTradeValue) {
                this.prices = prices;
                this.startUnits = units;
                this.units = units.clone();
                this.cash = cash;
                this.preTradeValue = preTradeValue;
                // Scale-aware tolerances: relative to portfolio value, never absolute.
                this.solverTolerance = preTradeValue.multipliedBy(numFactory.epsilon());
                this.noTradeBand = solverTolerance.multipliedBy(numFactory.thousand());
            }

            private void execute() {
                if (preTradeValue.isZero()) {
                    return;
                }
                Num targetValue = postCostTargetValue();
                if (targetValue == null) {
                    return;
                }
                Num[] deltas = new Num[units.length];
                List<Integer> sells = new ArrayList<>();
                List<Integer> buys = new ArrayList<>();
                for (int position = 0; position < units.length; position++) {
                    deltas[position] = targetWeights[position].multipliedBy(targetValue).minus(notional(position));
                    if (deltas[position].abs().isGreaterThan(noTradeBand)) {
                        (deltas[position].isNegative() ? sells : buys).add(position);
                    }
                }

                // Sells that release cash cannot fail and fund everything else, so they
                // go first; fee-dominated sells follow in ascending cash need. Ties
                // break by asset name, never by presentation order.
                List<Integer> cashConsumingSells = new ArrayList<>();
                for (int position : sells) {
                    Num gross = deltas[position].abs();
                    if (gross.isGreaterThanOrEqual(tradeCost(prices[position], gross, noTradeBand))) {
                        trade(position, gross.negate(), true);
                    } else {
                        cashConsumingSells.add(position);
                    }
                }
                cashConsumingSells.sort(Comparator.comparing((Integer position) -> {
                    Num gross = deltas[position].abs();
                    return tradeCost(prices[position], gross, noTradeBand).minus(gross);
                }).thenComparing(assets::get));
                for (int position : cashConsumingSells) {
                    sell(position, deltas[position].abs());
                }

                buy(buys, deltas);
                if (cash.isNegative()) {
                    // Affordability checks tolerate rounding noise up to the solver
                    // tolerance, so cash can only land a hair below zero.
                    if (cash.abs().isGreaterThan(solverTolerance)) {
                        throw new IllegalStateException("rebalance produced negative cash: " + cash);
                    }
                    cash = zero;
                }
            }

            /** Compares achieved with target weights of the post-trade value. */
            private RebalanceStatus status() {
                Num value = portfolioValue(cash, units, prices);
                for (int position = 0; position < units.length; position++) {
                    Num achieved = prices[position].multipliedBy(units[position]);
                    if (achieved.minus(targetWeights[position].multipliedBy(value)).abs().isGreaterThan(noTradeBand)) {
                        return tradedNotional.isZero() ? RebalanceStatus.SKIPPED : RebalanceStatus.PARTIAL;
                    }
                }
                return RebalanceStatus.COMPLETED;
            }

            /**
             * Largest post-cost portfolio value {@code T} with
             * {@code T + cost(T) <= preTradeValue}, where {@code cost(T)} is the cost of
             * trading every asset to {@code weight * T}; {@code null} when no positive
             * value is feasible, so costs would consume the whole portfolio.
             */
            private Num postCostTargetValue() {
                if (rebalanceCost(preTradeValue).isZero()) {
                    return preTradeValue;
                }
                Num low = fixedPointTargetValue();
                if (low == null) {
                    low = bisectTargetValue();
                }
                // Leaving holdings untouched is a feasible candidate that the solvers can
                // miss when fixed fees make feasibility discontinuous.
                Num holdValue = holdValue();
                if (holdValue != null && holdValue.isGreaterThan(low)) {
                    return holdValue;
                }
                return low.isZero() ? null : low;
            }

            /**
             * Iterates {@code T = preTradeValue - cost(T)}. Proportional costs make this a
             * contraction (factor = fee rate), so it converges in a few steps instead of a
             * full bisection; returns {@code null} when it does not settle on a feasible
             * value, leaving the general bisection to decide.
             */
            private Num fixedPointTargetValue() {
                Num value = preTradeValue;
                for (int iteration = 0; iteration < MAX_FIXED_POINT_ITERATIONS; iteration++) {
                    Num next = preTradeValue.minus(rebalanceCost(value));
                    if (next.isNegativeOrZero()) {
                        return null;
                    }
                    if (next.minus(value).abs().isLessThanOrEqual(solverTolerance)) {
                        // Iterates alternate around the root; the smaller one is the feasible side.
                        Num candidate = next.min(value);
                        return isFeasible(candidate) ? candidate : null;
                    }
                    value = next;
                }
                return null;
            }

            private Num bisectTargetValue() {
                Num low = zero;
                Num high = preTradeValue;
                for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
                    Num mid = midpoint(low, high, solverTolerance);
                    if (mid == null) {
                        break;
                    }
                    if (isFeasible(mid)) {
                        low = mid;
                    } else {
                        high = mid;
                    }
                }
                return low;
            }

            private boolean isFeasible(Num targetValue) {
                return targetValue.plus(rebalanceCost(targetValue)).isLessThanOrEqual(preTradeValue);
            }

            private Num rebalanceCost(Num targetValue) {
                Num totalCost = zero;
                for (int position = 0; position < units.length; position++) {
                    Num gross = targetWeights[position].multipliedBy(targetValue).minus(notional(position)).abs();
                    totalCost = totalCost.plus(tradeCost(prices[position], gross, noTradeBand));
                }
                return totalCost;
            }

            /**
             * Post-cost value at which the current holdings already match the targets, or
             * {@code null} when holding is not a consistent option.
             */
            private Num holdValue() {
                Num investedValue = zero;
                Num totalWeight = zero;
                for (int position = 0; position < units.length; position++) {
                    investedValue = investedValue.plus(notional(position));
                    totalWeight = totalWeight.plus(targetWeights[position]);
                }
                if (totalWeight.isZero()) {
                    return null;
                }
                Num holdValue = investedValue.dividedBy(totalWeight);
                if (holdValue.isGreaterThan(preTradeValue)) {
                    return null;
                }
                for (int position = 0; position < units.length; position++) {
                    Num gross = targetWeights[position].multipliedBy(holdValue).minus(notional(position)).abs();
                    if (gross.isGreaterThan(noTradeBand)) {
                        return null;
                    }
                }
                return holdValue;
            }

            private void sell(int position, Num desiredGross) {
                Num price = prices[position];
                if (isSellAffordable(price, desiredGross)) {
                    trade(position, desiredGross.negate(), true);
                    return;
                }
                Num low = zero;
                Num high = desiredGross;
                for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
                    Num mid = midpoint(low, high, solverTolerance);
                    if (mid == null) {
                        break;
                    }
                    if (isSellAffordable(price, mid)) {
                        low = mid;
                    } else {
                        high = mid;
                    }
                }
                if (low.isGreaterThan(noTradeBand)) {
                    trade(position, low.negate(), false);
                }
            }

            /** Affordable up to rounding noise, which the final cash clamp absorbs. */
            private boolean isSellAffordable(Num price, Num gross) {
                return cash.plus(gross)
                        .minus(tradeCost(price, gross, noTradeBand))
                        .isGreaterThanOrEqual(solverTolerance.negate());
            }

            /**
             * Buys every underweight asset, scaling all buys by one common factor when cash
             * cannot cover them so no asset is favored by its position.
             */
            private void buy(List<Integer> buys, Num[] deltas) {
                if (buys.isEmpty()) {
                    return;
                }
                Num one = numFactory.one();
                Num scale = one;
                Num availableCash = cash.plus(solverTolerance);
                if (buyCashNeed(buys, deltas, one).isGreaterThan(availableCash)) {
                    Num low = zero;
                    Num high = one;
                    for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
                        Num mid = midpoint(low, high, numFactory.epsilon());
                        if (mid == null) {
                            break;
                        }
                        if (buyCashNeed(buys, deltas, mid).isLessThanOrEqual(availableCash)) {
                            low = mid;
                        } else {
                            high = mid;
                        }
                    }
                    scale = low;
                }
                for (int position : buys) {
                    Num gross = deltas[position].multipliedBy(scale);
                    if (gross.isGreaterThan(noTradeBand)) {
                        trade(position, gross, false);
                    }
                }
            }

            private Num buyCashNeed(List<Integer> buys, Num[] deltas, Num scale) {
                Num need = zero;
                for (int position : buys) {
                    Num gross = deltas[position].multipliedBy(scale);
                    need = need.plus(gross).plus(tradeCost(prices[position], gross, noTradeBand));
                }
                return need;
            }

            /**
             * Applies a signed notional trade; a complete sale of a zero-target asset lands
             * exactly on zero units instead of rounding residue.
             */
            private void trade(int position, Num signedGross, boolean fullSale) {
                Num price = prices[position];
                Num gross = signedGross.abs();
                Num cost = tradeCost(price, gross, noTradeBand);
                Num targetUnits = startUnits[position].plus(signedGross.dividedBy(price));
                units[position] = fullSale && targetWeights[position].isZero() ? zero : targetUnits;
                cash = cash.minus(signedGross).minus(cost);
                transactionCost = transactionCost.plus(cost);
                tradedNotional = tradedNotional.plus(gross);
            }

            private Num notional(int position) {
                return prices[position].multipliedBy(startUnits[position]);
            }
        }
    }
}
