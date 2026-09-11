/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Unified {@link TradingRecord} implementation used for backtest and live
 * flows.
 *
 * <p>
 * This class combines classic index/price/amount operations with fill-aware
 * {@link #operate(Trade)} support, so a single record type can model both
 * simulated and live execution behavior.
 * </p>
 *
 * <p>
 * Usage guidance:
 * </p>
 * <ul>
 * <li>Backtests with synthetic fills: use index/price/amount operations through
 * strategy runners.</li>
 * <li>Broker-confirmed fills: use {@link #operate(TradeFill)} or
 * {@link #operate(Trade)}.</li>
 * <li>Lot matching behavior is controlled by {@link ExecutionMatchPolicy}.</li>
 * </ul>
 *
 * @since 0.22.2
 */
@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Every constructor validates the trading record configuration - match policy, futures contract and initial capital - before an instance is published, so invalid records are rejected fail-fast instead of escaping partially initialized")
public class BaseTradingRecord implements TradingRecord {

    @Serial
    private static final long serialVersionUID = 7960596064337713648L;

    private static final Gson GSON = new Gson();

    private transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final TradeType startingType;
    private final ExecutionMatchPolicy matchPolicy;
    private transient CostModel transactionCostModel;
    private transient CostModel holdingCostModel;
    private final PositionBook positionBook;
    private final Integer startIndex;
    private final Integer endIndex;
    private final FuturesContract futuresContract;
    private final Num initialCapital;
    private final Num initialMarginRate;
    private List<FuturesFunding> fundingSchedule;
    private String name;
    private int nextTradeIndex;
    private transient List<Trade> tradesCache;
    private transient long tradesCacheVersion;
    private long modificationCount;
    private Num totalFees;
    private transient NumFactory numFactory;
    private long nextSequence;
    private List<FuturesCashFlow> cashFlows = new ArrayList<>();
    private Map<String, FuturesCashFlow> processedEvents = new LinkedHashMap<>();
    private List<FuturesMarketSnapshot> marketSnapshots = new ArrayList<>();
    private List<FuturesPositionSnapshot> positionSnapshots = new ArrayList<>();
    private int fundingCursor;
    private Instant eventHorizon;
    private boolean readOnly;

    /** Constructor with {@link #startingType} = BUY and FIFO matching. */
    public BaseTradingRecord() {
        this(defaultRecordConfig(TradeType.BUY));
    }

    /**
     * Constructor with {@link #startingType} = BUY and FIFO matching.
     *
     * @param name record name
     */
    public BaseTradingRecord(String name) {
        this(defaultRecordConfig(TradeType.BUY));
        this.name = name;
    }

    /**
     * Constructor.
     *
     * @param name      record name
     * @param tradeType entry trade type
     */
    public BaseTradingRecord(String name, TradeType tradeType) {
        this(defaultRecordConfig(tradeType));
        this.name = name;
    }

    /**
     * Constructor with FIFO matching.
     *
     * @param startingType entry trade type
     */
    public BaseTradingRecord(TradeType startingType) {
        this(defaultRecordConfig(startingType));
    }

    /**
     * Constructor with FIFO matching.
     *
     * @param startingType         entry trade type
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     */
    public BaseTradingRecord(TradeType startingType, CostModel transactionCostModel, CostModel holdingCostModel) {
        this(startingType, ExecutionMatchPolicy.FIFO, transactionCostModel, holdingCostModel, null, null);
    }

    /**
     * Constructor with FIFO matching.
     *
     * @param startingType         entry trade type
     * @param startIndex           optional start index
     * @param endIndex             optional end index
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     */
    public BaseTradingRecord(TradeType startingType, Integer startIndex, Integer endIndex,
            CostModel transactionCostModel, CostModel holdingCostModel) {
        this(startingType, ExecutionMatchPolicy.FIFO, transactionCostModel, holdingCostModel, startIndex, endIndex);
    }

    /**
     * Constructor.
     *
     * @param startingType         entry trade type
     * @param matchPolicy          lot matching policy
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     * @param startIndex           optional start index
     * @param endIndex             optional end index
     */
    public BaseTradingRecord(TradeType startingType, ExecutionMatchPolicy matchPolicy, CostModel transactionCostModel,
            CostModel holdingCostModel, Integer startIndex, Integer endIndex) {
        this(recordConfig(startingType, matchPolicy, transactionCostModel, holdingCostModel, startIndex, endIndex));
    }

    private BaseTradingRecord(RecordConfig config) {
        this.startingType = config.startingType();
        this.matchPolicy = config.matchPolicy();
        this.transactionCostModel = config.transactionCostModel();
        this.holdingCostModel = config.holdingCostModel();
        this.positionBook = config.positionBook();
        this.startIndex = config.startIndex();
        this.endIndex = config.endIndex();
        this.futuresContract = config.futuresContract();
        this.initialCapital = config.initialCapital();
        this.initialMarginRate = config.initialMarginRate();
        this.fundingSchedule = config.fundingSchedule() == null ? List.of() : config.fundingSchedule();
        this.nextTradeIndex = config.nextTradeIndex();
        this.modificationCount = config.modificationCount();
        this.totalFees = config.totalFees();
        this.numFactory = config.numFactory();
        this.nextSequence = config.nextSequence();
    }

    private static RecordConfig recordConfig(TradeType startingType, ExecutionMatchPolicy matchPolicy,
            CostModel transactionCostModel, CostModel holdingCostModel, Integer startIndex, Integer endIndex) {
        return recordConfig(startingType, matchPolicy, transactionCostModel, holdingCostModel, startIndex, endIndex,
                null, null, null, List.of());
    }

    private static RecordConfig recordConfig(TradeType startingType, ExecutionMatchPolicy matchPolicy,
            CostModel transactionCostModel, CostModel holdingCostModel, Integer startIndex, Integer endIndex,
            FuturesContract futuresContract, Num initialCapital, Num initialMarginRate,
            List<FuturesFunding> fundingSchedule) {
        Objects.requireNonNull(startingType, "startingType");
        Objects.requireNonNull(matchPolicy, "matchPolicy");
        validateFuturesConfig(futuresContract, initialCapital, initialMarginRate, fundingSchedule);
        CostModel resolvedTransactionCostModel = resolveTransactionCostModel(transactionCostModel, futuresContract);
        CostModel resolvedHoldingCostModel = defaultCostModel(holdingCostModel);
        PositionBook positionBook = new PositionBook(startingType, matchPolicy, resolvedTransactionCostModel,
                resolvedHoldingCostModel, futuresContract);
        return new RecordConfig(startingType, matchPolicy, resolvedTransactionCostModel, resolvedHoldingCostModel,
                positionBook, startIndex, endIndex, 0, 0L, null, null, 0L, futuresContract, initialCapital,
                initialMarginRate, sortedFundingSchedule(fundingSchedule));
    }

    private static List<FuturesFunding> sortedFundingSchedule(List<FuturesFunding> fundingSchedule) {
        if (fundingSchedule == null || fundingSchedule.isEmpty()) {
            return List.of();
        }
        List<FuturesFunding> sorted = new ArrayList<>(fundingSchedule);
        sorted.sort(Comparator.comparing(FuturesFunding::time).thenComparing(FuturesFunding::eventId));
        return List.copyOf(sorted);
    }

    private static void validateFuturesConfig(FuturesContract futuresContract, Num initialCapital,
            Num initialMarginRate, List<FuturesFunding> fundingSchedule) {
        if (futuresContract == null) {
            if (initialCapital != null || initialMarginRate != null) {
                throw new IllegalArgumentException("initialCapital and initialMarginRate require a futures contract");
            }
            if (fundingSchedule != null && !fundingSchedule.isEmpty()) {
                throw new IllegalArgumentException("A funding schedule requires a futures contract");
            }
            return;
        }
        if (initialCapital != null) {
            FuturesValidation.requirePositiveFinite(initialCapital, "initialCapital");
            requireSettlementCurrency(futuresContract, "initialCapital");
        }
        if (initialMarginRate != null) {
            FuturesValidation.requirePositiveFinite(initialMarginRate, "initialMarginRate");
        }
        if (fundingSchedule != null) {
            for (FuturesFunding funding : fundingSchedule) {
                Objects.requireNonNull(funding, "fundingSchedule entry");
                if (!futuresContract.equals(funding.contract())) {
                    throw new IllegalArgumentException("Funding schedule contract must match the record contract");
                }
            }
        }
    }

    private static void requireSettlementCurrency(FuturesContract futuresContract, String field) {
        String currency = futuresContract.settlementCurrency();
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(field + " requires a settlement currency on the futures contract");
        }
    }

    private static CostModel resolveTransactionCostModel(CostModel transactionCostModel,
            FuturesContract futuresContract) {
        if (transactionCostModel != null) {
            return transactionCostModel;
        }
        return futuresContract == null ? new ZeroCostModel() : RecordedTradeCostModel.INSTANCE;
    }

    private static RecordConfig defaultRecordConfig(TradeType startingType) {
        return recordConfig(startingType, ExecutionMatchPolicy.FIFO, new ZeroCostModel(), new ZeroCostModel(), null,
                null);
    }

    /**
     * Creates a builder for a fully configured record.
     *
     * @return new builder
     * @since 0.25.1
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for trading records, including native futures configuration.
     *
     * <p>
     * A futures record fixes exactly one {@link FuturesContract}. Fees are read
     * from the recorded fill components, so the transaction cost model defaults to
     * {@link RecordedTradeCostModel} unless a modelled cost is supplied explicitly.
     * </p>
     *
     * @since 0.25.1
     */
    public static final class Builder {

        private TradeType startingType = TradeType.BUY;
        private ExecutionMatchPolicy matchPolicy = ExecutionMatchPolicy.FIFO;
        private CostModel transactionCostModel;
        private CostModel holdingCostModel;
        private Integer startIndex;
        private Integer endIndex;
        private String name;
        private FuturesContract futuresContract;
        private Num initialCapital;
        private Num initialMarginRate;
        private List<FuturesFunding> fundingSchedule = List.of();

        private Builder() {
        }

        /**
         * @param startingType entry trade type
         * @return this builder
         */
        public Builder startingType(TradeType startingType) {
            this.startingType = Objects.requireNonNull(startingType, "startingType");
            return this;
        }

        /**
         * @param matchPolicy lot matching policy
         * @return this builder
         */
        public Builder matchPolicy(ExecutionMatchPolicy matchPolicy) {
            this.matchPolicy = Objects.requireNonNull(matchPolicy, "matchPolicy");
            return this;
        }

        /**
         * @param transactionCostModel transaction cost model
         * @return this builder
         */
        public Builder transactionCostModel(CostModel transactionCostModel) {
            this.transactionCostModel = transactionCostModel;
            return this;
        }

        /**
         * @param holdingCostModel holding cost model
         * @return this builder
         */
        public Builder holdingCostModel(CostModel holdingCostModel) {
            this.holdingCostModel = holdingCostModel;
            return this;
        }

        /**
         * @param startIndex optional start index
         * @return this builder
         */
        public Builder startIndex(Integer startIndex) {
            this.startIndex = startIndex;
            return this;
        }

        /**
         * @param endIndex optional end index
         * @return this builder
         */
        public Builder endIndex(Integer endIndex) {
            this.endIndex = endIndex;
            return this;
        }

        /**
         * @param name record name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * @param futuresContract traded contract, {@code null} for a spot record
         * @return this builder
         */
        @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The builder stores the immutable FuturesContract by reference; the contract is never mutated after the value is built")
        public Builder futuresContract(FuturesContract futuresContract) {
            this.futuresContract = futuresContract;
            return this;
        }

        /**
         * @param initialCapital account capital in the settlement currency
         * @return this builder
         */
        public Builder initialCapital(Num initialCapital) {
            this.initialCapital = initialCapital;
            return this;
        }

        /**
         * @param initialMarginRate initial margin rate applied to the position notional
         * @return this builder
         */
        public Builder initialMarginRate(Num initialMarginRate) {
            this.initialMarginRate = initialMarginRate;
            return this;
        }

        /**
         * @param fundingSchedule funding events applied to open positions
         * @return this builder
         */
        public Builder fundingSchedule(List<FuturesFunding> fundingSchedule) {
            this.fundingSchedule = fundingSchedule == null ? List.of() : List.copyOf(fundingSchedule);
            return this;
        }

        /**
         * @return configured record
         */
        public BaseTradingRecord build() {
            RecordConfig config = recordConfig(startingType, matchPolicy, transactionCostModel, holdingCostModel,
                    startIndex, endIndex, futuresContract, initialCapital, initialMarginRate, fundingSchedule);
            BaseTradingRecord record = new BaseTradingRecord(config);
            record.setName(name);
            return record;
        }
    }

    /**
     * Constructor.
     *
     * @param trades trades to record (must not be empty)
     */
    public BaseTradingRecord(Trade... trades) {
        this(tradesConfig(trades));
    }

    /**
     * Constructor.
     *
     * @param position position to record (entry required)
     * @since 0.22.2
     */
    public BaseTradingRecord(Position position) {
        this(positionConfig(position));
    }

    /**
     * Constructor.
     *
     * @param positions positions to record (must not be empty)
     * @since 0.22.2
     */
    public BaseTradingRecord(List<Position> positions) {
        this(positionsConfig(positions));
    }

    /**
     * Constructor.
     *
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     * @param trades               trades to record (must not be empty)
     */
    public BaseTradingRecord(CostModel transactionCostModel, CostModel holdingCostModel, Trade... trades) {
        this(tradesConfig(transactionCostModel, holdingCostModel, trades));
    }

    private static RecordConfig positionConfig(Position position) {
        Objects.requireNonNull(position, "position must not be null");
        return tradesConfig(defaultCostModel(position.getTransactionCostModel()),
                defaultCostModel(position.getHoldingCostModel()), positionToTrades(position));
    }

    private static RecordConfig positionsConfig(List<Position> positions) {
        Objects.requireNonNull(positions, "positions must not be null");
        FuturesContract contract = contractOfPositions(positions);
        if (contract == null) {
            return tradesConfig(new ZeroCostModel(), new ZeroCostModel(), positionsToTrades(positions));
        }
        return futuresPositionsConfig(contract, positions);
    }

    private static FuturesContract contractOfPositions(List<Position> positions) {
        FuturesContract contract = null;
        for (Position position : positions) {
            Objects.requireNonNull(position, "position must not be null");
            FuturesContract positionContract = position.getFuturesContract();
            if (positionContract == null) {
                if (contract != null) {
                    throw new IllegalArgumentException("Cannot mix spot and futures positions in one record");
                }
                continue;
            }
            if (contract == null) {
                contract = positionContract;
            } else if (!contract.equals(positionContract)) {
                throw new IllegalArgumentException("All positions must reference the same futures contract");
            }
        }
        return contract;
    }

    private static RecordConfig futuresPositionsConfig(FuturesContract contract, List<Position> positions) {
        Trade entry = positions.getFirst().getEntry();
        if (entry == null) {
            throw new IllegalArgumentException("Position entry must not be null");
        }
        BaseTradingRecord initialized = new BaseTradingRecord(recordConfig(entry.getType(), ExecutionMatchPolicy.FIFO,
                RecordedTradeCostModel.INSTANCE, new ZeroCostModel(), null, null, contract, null, null, List.of()));
        Num totalFees = null;
        for (Position position : positions) {
            initialized.adoptPosition(position);
            totalFees = accumulateRecordedFees(totalFees, position);
        }
        initialized.aggregateProjectedCashFlows(positions, Integer.MAX_VALUE);
        initialized.totalFees = totalFees == null ? initialized.defaultNumFactory().zero() : totalFees;
        return initialized.toRecordConfig();
    }

    /**
     * Copies already-matched futures positions into a read-only projection.
     *
     * <p>
     * The selected {@link Position} snapshots are adopted unchanged: their entries
     * and exits are never replayed, so a projection cannot rematch overlapping lots
     * or reorder executed timestamps. Contract, match policy, cost models and the
     * explicit initial capital of the source record are preserved, while the live
     * funding schedule is deliberately not copied: the projected event totals are
     * rebuilt from the cash flows allocated to exactly the selected positions. An
     * allocation held by more than one selected position is summed under its event
     * id, so a single event is never charged twice.
     * </p>
     *
     * <p>
     * Every mutation path of the projection throws
     * {@link UnsupportedOperationException}; the recorded fee total covers the
     * executed fees of the selected positions.
     * </p>
     *
     * @param source    futures trading record the projection is derived from
     * @param positions already-matched positions to include, each carrying the
     *                  source contract
     * @param start     first index of the projected window
     * @param end       last index of the projected window
     * @return read-only futures trading record containing only the selected
     *         positions and their allocations
     * @since 0.25.1
     */
    static BaseTradingRecord projectedFutures(TradingRecord source, List<Position> positions, int start, int end) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(positions, "positions");
        FuturesContract contract = source.getFuturesContract();
        if (contract == null) {
            throw new IllegalArgumentException("A futures projection requires a futures trading record");
        }
        ExecutionMatchPolicy matchPolicy = source instanceof BaseTradingRecord baseRecord ? baseRecord.matchPolicy
                : ExecutionMatchPolicy.FIFO;
        BaseTradingRecord projected = new BaseTradingRecord(recordConfig(source.getStartingType(), matchPolicy,
                source.getTransactionCostModel(), source.getHoldingCostModel(), start, end, contract,
                source.getInitialCapital(), source.getInitialMarginRate(), List.of()));
        Num totalFees = null;
        for (Position position : positions) {
            Position projectedPosition = trimmedToWindow(position, end);
            projected.adoptPosition(projectedPosition);
            totalFees = accumulateRecordedFees(totalFees, projectedPosition);
        }
        projected.totalFees = totalFees == null ? projected.defaultNumFactory().zero() : totalFees;
        projected.aggregateProjectedCashFlows(positions, end);
        projected.readOnly = true;
        return projected;
    }

    /**
     * Trims the cash flows allocated to a position to a window, keeping the
     * position itself whenever all of its allocations already fall inside.
     *
     * @param position already-matched position
     * @param end      last index of the projected window
     * @return the position, or a copy holding only the allocations up to
     *         {@code end}
     */
    private static Position trimmedToWindow(Position position, int end) {
        List<FuturesCashFlow> cashFlows = position.getCashFlows();
        List<FuturesCashFlow> retainedCashFlows = new ArrayList<>(cashFlows.size());
        for (FuturesCashFlow cashFlow : cashFlows) {
            if (cashFlow.index() <= end) {
                retainedCashFlows.add(cashFlow);
            }
        }

        Trade originalEntry = position.getEntry();
        List<TradeFill> retainedEntryFills = Trade.executionFillsOf(originalEntry)
                .stream()
                .filter(fill -> fill.index() <= end)
                .toList();
        Trade originalExit = position.getExit();
        List<TradeFill> retainedExitFills = originalExit == null ? List.of()
                : Trade.executionFillsOf(originalExit).stream().filter(fill -> fill.index() <= end).toList();
        boolean trimmed = retainedCashFlows.size() != cashFlows.size()
                || retainedEntryFills.size() != Trade.executionFillsOf(originalEntry).size()
                || (originalExit != null && retainedExitFills.size() != Trade.executionFillsOf(originalExit).size());
        if (!trimmed) {
            return position;
        }

        CostModel transactionCostModel = position.getTransactionCostModel();
        CostModel holdingCostModel = position.getHoldingCostModel();
        Trade entry = Trade.fromFills(originalEntry.getType(), retainedEntryFills, originalEntry.getCostModel());
        if (originalExit == null || retainedExitFills.isEmpty()) {
            return new Position(entry, transactionCostModel, holdingCostModel, retainedCashFlows);
        }
        Trade exit = Trade.fromFills(originalExit.getType(), retainedExitFills, originalExit.getCostModel());
        return new Position(entry, exit, transactionCostModel, holdingCostModel, retainedCashFlows);
    }

    /**
     * Rebuilds the projected event log from the allocations held by the selected
     * positions.
     *
     * @param positions selected positions
     * @param end       last index of the projected window
     */
    private void aggregateProjectedCashFlows(List<Position> positions, int end) {
        Map<String, Num> amounts = new LinkedHashMap<>();
        Map<String, Num> settlements = new LinkedHashMap<>();
        Map<String, FuturesCashFlow> templates = new LinkedHashMap<>();
        for (Position position : positions) {
            for (FuturesCashFlow cashFlow : position.getCashFlows()) {
                if (cashFlow.index() > end) {
                    continue;
                }
                String eventId = cashFlow.eventId();
                templates.putIfAbsent(eventId, cashFlow);
                amounts.merge(eventId, cashFlow.amount(), Num::plus);
                settlements.merge(eventId, cashFlow.settlementAmount(), Num::plus);
            }
        }
        List<FuturesCashFlow> aggregated = new ArrayList<>(templates.size());
        for (Map.Entry<String, FuturesCashFlow> entry : templates.entrySet()) {
            String eventId = entry.getKey();
            FuturesCashFlow template = entry.getValue();
            Num amount = amounts.get(eventId);
            Num settlement = settlements.get(eventId);
            FuturesCashFlow recorded = template.amount().isEqual(amount)
                    && template.settlementAmount().isEqual(settlement) ? template
                            : template.toBuilder().amount(amount).settlementAmount(settlement).build();
            processedEvents.put(eventId, recorded);
            aggregated.add(recorded);
        }
        aggregated.sort(Comparator.comparing(FuturesCashFlow::time));
        cashFlows.addAll(aggregated);
    }

    private static Num accumulateRecordedFees(Num totalFees, Position position) {
        Num accumulated = plusFee(totalFees, position.getEntry());
        return plusFee(accumulated, position.getExit());
    }

    private static Num plusFee(Num totalFees, Trade trade) {
        if (trade == null) {
            return totalFees;
        }
        Num fee = feeOf(trade);
        return totalFees == null ? fee : totalFees.plus(fee);
    }

    private void adoptPosition(Position position) {
        long entrySequence = nextSequence++;
        long exitSequence = nextSequence++;
        positionBook.adopt(position, entrySequence, exitSequence);
        Num price = position.getEntry().getPricePerAsset();
        if ((numFactory == null || numFactory.one().isNaN()) && price != null && !price.isNaN()) {
            numFactory = price.getNumFactory();
        }
    }

    private static RecordConfig tradesConfig(Trade... trades) {
        return tradesConfig(new ZeroCostModel(), new ZeroCostModel(), trades);
    }

    private static RecordConfig tradesConfig(CostModel transactionCostModel, CostModel holdingCostModel,
            Trade... trades) {
        TradeType startingType = validateTrades(trades);
        FuturesContract contract = contractOf(trades);
        BaseTradingRecord initialized = new BaseTradingRecord(recordConfig(startingType, ExecutionMatchPolicy.FIFO,
                transactionCostModel, holdingCostModel, null, null, contract, null, null, List.of()));
        for (Trade trade : trades) {
            initialized.operate(trade);
        }
        return initialized.toRecordConfig();
    }

    private RecordConfig toRecordConfig() {
        return new RecordConfig(startingType, matchPolicy, transactionCostModel, holdingCostModel, positionBook,
                startIndex, endIndex, nextTradeIndex, modificationCount, totalFees, numFactory, nextSequence,
                futuresContract, initialCapital, initialMarginRate, fundingSchedule);
    }

    private static FuturesContract contractOf(Trade... trades) {
        FuturesContract contract = null;
        for (Trade trade : trades) {
            Objects.requireNonNull(trade, "trade");
            FuturesContract tradeContract = trade.getFuturesContract();
            if (tradeContract == null) {
                if (contract != null) {
                    throw new IllegalArgumentException("Cannot mix spot and futures trades in one record");
                }
                continue;
            }
            if (contract == null) {
                contract = tradeContract;
            } else if (!contract.equals(tradeContract)) {
                throw new IllegalArgumentException("All trades must reference the same futures contract");
            }
        }
        return contract;
    }

    @Override
    public TradeType getStartingType() {
        return startingType;
    }

    /**
     * @return lot matching policy
     * @since 0.22.4
     */
    public ExecutionMatchPolicy getMatchPolicy() {
        return matchPolicy;
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "FuturesContract is a final value type whose instances are shared by reference; copying the immutable contract per accessor would allocate on every read")
    @Override
    public FuturesContract getFuturesContract() {
        return futuresContract;
    }

    @Override
    public Num getInitialCapital() {
        return initialCapital;
    }

    @Override
    public Num getInitialMarginRate() {
        return initialMarginRate;
    }

    @Override
    public List<FuturesFunding> getFundingSchedule() {
        return fundingSchedule;
    }

    @Override
    public void recordFunding(FuturesFunding funding) {
        Objects.requireNonNull(funding, "funding");
        requireMutable("recordFunding(FuturesFunding)");
        requireFuturesRecord("recordFunding(FuturesFunding)");
        requireEventContract(funding.contract(), "Funding event");
        lock.writeLock().lock();
        try {
            applyScheduledFunding(funding.time());
            applyFundingInternal(funding);
            advanceHorizonThrough(funding.time());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void recordCashFlow(FuturesCashFlow cashFlow) {
        Objects.requireNonNull(cashFlow, "cashFlow");
        requireMutable("recordCashFlow(FuturesCashFlow)");
        requireFuturesRecord("recordCashFlow(FuturesCashFlow)");
        requireEventContract(cashFlow.contract(), "Cash flow");
        lock.writeLock().lock();
        try {
            FuturesCashFlow recorded = processedEvents.get(cashFlow.eventId());
            if (recorded != null) {
                if (recorded.equals(cashFlow)) {
                    return;
                }
                throw new IllegalArgumentException(
                        "Cash flow " + cashFlow.eventId() + " is already recorded with different values");
            }
            if (eventHorizon == null || cashFlow.time().isAfter(eventHorizon)) {
                applyScheduledFunding(cashFlow.time());
            }
            positionBook.allocateCashFlow(cashFlow);
            processedEvents.put(cashFlow.eventId(), cashFlow);
            cashFlows.add(cashFlow);
            advanceHorizonThrough(cashFlow.time());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<FuturesCashFlow> getCashFlows() {
        lock.readLock().lock();
        try {
            return List.copyOf(cashFlows);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void advanceTo(Instant time) {
        Objects.requireNonNull(time, "time");
        requireMutable("advanceTo(Instant)");
        if (futuresContract == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            if (eventHorizon != null && !time.isAfter(eventHorizon)) {
                return;
            }
            applyScheduledFunding(time);
            advanceHorizonThrough(time);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void recordMarketSnapshot(FuturesMarketSnapshot snapshot) {
        requireMutable("recordMarketSnapshot(FuturesMarketSnapshot)");
        Objects.requireNonNull(snapshot, "snapshot");
        requireFuturesRecord("recordMarketSnapshot(FuturesMarketSnapshot)");
        requireEventContract(snapshot.contract(), "Market snapshot");
        lock.writeLock().lock();
        try {
            marketSnapshots.add(snapshot);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<FuturesMarketSnapshot> getMarketSnapshots() {
        lock.readLock().lock();
        try {
            return List.copyOf(marketSnapshots);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void recordPositionSnapshot(FuturesPositionSnapshot snapshot) {
        requireMutable("recordPositionSnapshot(FuturesPositionSnapshot)");
        Objects.requireNonNull(snapshot, "snapshot");
        requireFuturesRecord("recordPositionSnapshot(FuturesPositionSnapshot)");
        requireEventContract(snapshot.contract(), "Position snapshot");
        lock.writeLock().lock();
        try {
            positionSnapshots.add(snapshot);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<FuturesPositionSnapshot> getPositionSnapshots() {
        lock.readLock().lock();
        try {
            return List.copyOf(positionSnapshots);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets record name.
     *
     * @param name name
     * @since 0.22.4
     */
    public void setName(String name) {
        requireMutable("setName(String)");
        this.name = name;
    }

    /**
     * Records one trade using an auto-incremented index.
     *
     * <p>
     * Use {@link #operate(Trade)} in new code.
     * </p>
     *
     * @param trade trade to record
     * @since 0.22.4
     */
    @Deprecated(since = "0.22.4", forRemoval = true)
    public void recordFill(Trade trade) {
        operate(tradeWithAssignedIndex(trade, nextIndex()));
    }

    /**
     * Records one trade using an explicit index.
     *
     * <p>
     * Use {@link #operate(Trade)} in new code.
     * </p>
     *
     * @param index trade index
     * @param trade trade to record
     * @since 0.22.4
     */
    @Deprecated(since = "0.22.4", forRemoval = true)
    public void recordFill(int index, Trade trade) {
        operate(tradeWithAssignedIndex(trade, index));
    }

    /**
     * Records one execution fill.
     *
     * <p>
     * Use {@link #operate(TradeFill)} in new code.
     * </p>
     *
     * @param fill execution fill
     * @since 0.22.4
     */
    @Deprecated(since = "0.22.4", forRemoval = true)
    public void recordExecutionFill(TradeFill fill) {
        operate(fill);
    }

    @Override
    public void operate(TradeFill fill) {
        Objects.requireNonNull(fill, "fill");
        if (fill.futuresContract() == null) {
            operate(Trade.fromFill(fill));
            return;
        }
        operate(Trade.fromFill(fill, getTransactionCostModel()));
    }

    @Override
    public void operate(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        requireMutable("operate(Trade)");
        Objects.requireNonNull(trade.getType(), "trade.type");
        lock.writeLock().lock();
        try {
            List<TradeFill> fills = Trade.executionFillsOf(trade);
            List<PlannedTradeFill> plannedTradeFills = planTradeFills(trade, fills);
            validatePlannedFillTimes(plannedTradeFills);
            for (PlannedTradeFill plannedTradeFill : plannedTradeFills) {
                applyTradeInternal(plannedTradeFill.index(), plannedTradeFill.trade(), -1L);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        requireMutable("operate(int, Num, Num)");
        requireSpotRecord("operate(int, Num, Num)");
        lock.writeLock().lock();
        try {
            TradeType tradeType = positionBook.hasOpenLots() ? startingType.complementType() : startingType;
            applySyntheticInternal(index, tradeType, price, amount, transactionCostModel);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        requireMutable("enter(int, Num, Num)");
        requireSpotRecord("enter(int, Num, Num)");
        lock.writeLock().lock();
        try {
            if (positionBook.hasOpenLots()) {
                return false;
            }
            applySyntheticInternal(index, startingType, price, amount, transactionCostModel);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean exit(int index, Num price, Num amount) {
        requireMutable("exit(int, Num, Num)");
        requireSpotRecord("exit(int, Num, Num)");
        lock.writeLock().lock();
        try {
            if (!positionBook.hasOpenLots()) {
                return false;
            }
            applySyntheticInternal(index, startingType.complementType(), price, amount, transactionCostModel);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public CostModel getTransactionCostModel() {
        return transactionCostModel;
    }

    @Override
    public CostModel getHoldingCostModel() {
        return holdingCostModel;
    }

    @Override
    public List<Position> getPositions() {
        return closedPositionsSnapshot();
    }

    @Override
    public Position getCurrentPosition() {
        return currentPositionView();
    }

    @Override
    public List<Trade> getTrades() {
        return tradesSnapshot();
    }

    @Override
    public Trade getLastEntry() {
        return lastRecordedEntrySnapshot();
    }

    @Override
    public Trade getLastExit() {
        return lastRecordedExitSnapshot();
    }

    @Override
    public Integer getStartIndex() {
        return startIndex;
    }

    @Override
    public Integer getEndIndex() {
        return endIndex;
    }

    /**
     * Returns open positions.
     *
     * @return open positions
     * @since 0.22.4
     */
    @Override
    public List<Position> getOpenPositions() {
        return openPositionsSnapshot();
    }

    /**
     * Returns the aggregated net open position.
     *
     * @return net open position, or {@code null} when no lots are open
     * @since 0.22.4
     */
    @Override
    @Deprecated(since = "0.22.4")
    public Position getNetOpenPosition() {
        return netOpenPositionSnapshot();
    }

    /**
     * @return summed execution costs/fees across all recorded trades
     * @since 0.22.4
     */
    public Num getTotalFees() {
        return totalFeesSnapshot();
    }

    @Override
    public Num getRecordedTotalFees() {
        return getTotalFees();
    }

    DebugSnapshot debugSnapshot() {
        return new DebugSnapshot(startingType, tradesSnapshot(), closedPositionsSnapshot(), currentPositionView(),
                openPositionsSnapshot(), netOpenPositionSnapshot(), totalFeesSnapshot());
    }

    private void requireSpotRecord(String operation) {
        if (futuresContract != null) {
            throw new IllegalStateException(operation
                    + " cannot be used with a futures record; record fills that carry the futures contract and fees");
        }
    }

    private void requireFuturesRecord(String operation) {
        if (futuresContract == null) {
            throw new UnsupportedOperationException(operation + " requires a native futures record");
        }
    }

    private void requireMutable(String operation) {
        if (readOnly) {
            throw new UnsupportedOperationException(operation + " is not supported by a projected trading record");
        }
    }

    private void requireEventContract(FuturesContract contract, String label) {
        Objects.requireNonNull(contract, label + " contract");
        if (!futuresContract.equals(contract)) {
            throw new IllegalArgumentException(label + " contract " + contract.symbol()
                    + " does not match the record contract " + futuresContract.symbol());
        }
    }

    /**
     * Applies every scheduled funding event due up to and including the supplied
     * time, in schedule order.
     *
     * @param through inclusive upper bound of the accounting horizon
     */
    private void applyScheduledFunding(Instant through) {
        while (fundingCursor < fundingSchedule.size()) {
            FuturesFunding funding = fundingSchedule.get(fundingCursor);
            if (funding.time().isAfter(through)) {
                return;
            }
            fundingCursor++;
            applyFundingInternal(funding);
        }
    }

    private void applyFundingInternal(FuturesFunding funding) {
        Num signedContracts = positionBook.signedContractsAt(funding.time());
        Num amount = futuresContract.fundingCashFlow(signedContracts, funding.referencePrice(), funding.rate());
        recordProcessedCashFlow(FuturesCashFlow.builder()
                .contract(futuresContract)
                .type(FuturesCashFlow.Type.FUNDING)
                .eventId(funding.eventId())
                .index(funding.index())
                .time(funding.time())
                .amount(amount)
                .currency(futuresContract.settlementCurrency())
                .rate(funding.rate())
                .referencePrice(funding.referencePrice())
                .source(funding.source())
                .build());
    }

    /**
     * Allocates and journals one already-resolved cash flow. The accounting horizon
     * is owned by the caller.
     *
     * @param cashFlow resolved cash flow
     */
    private void recordProcessedCashFlow(FuturesCashFlow cashFlow) {
        FuturesCashFlow recorded = processedEvents.get(cashFlow.eventId());
        if (recorded != null) {
            if (recorded.equals(cashFlow)) {
                return;
            }
            throw new IllegalArgumentException(
                    "Cash flow " + cashFlow.eventId() + " is already recorded with different values");
        }
        positionBook.allocateCashFlow(cashFlow);
        processedEvents.put(cashFlow.eventId(), cashFlow);
        cashFlows.add(cashFlow);
    }

    private void advanceHorizonThrough(Instant time) {
        if (eventHorizon == null || time.isAfter(eventHorizon)) {
            eventHorizon = time;
        }
    }

    /**
     * Validates the timestamps of a planned native fill batch before mutation.
     *
     * @param plannedTradeFills planned fills to validate
     */
    private void validatePlannedFillTimes(List<PlannedTradeFill> plannedTradeFills) {
        Instant previousTime = null;
        for (PlannedTradeFill plannedTradeFill : plannedTradeFills) {
            Trade plannedTrade = plannedTradeFill.trade();
            if (plannedTrade.getFuturesContract() == null) {
                continue;
            }
            Instant fillTime = plannedTrade.getTime();
            if (eventHorizon != null && fillTime.isBefore(eventHorizon)) {
                throw new IllegalArgumentException(
                        "Fill at " + fillTime + " precedes the processed event horizon " + eventHorizon);
            }
            if (previousTime != null && fillTime.isBefore(previousTime)) {
                throw new IllegalArgumentException("Futures fills must be chronological");
            }
            previousTime = fillTime;
        }
    }

    private void advanceForFill(Trade trade) {
        if (futuresContract == null) {
            return;
        }
        Instant fillTime = trade.getTime();
        if (fillTime == null) {
            return;
        }
        if (eventHorizon != null && fillTime.isBefore(eventHorizon)) {
            throw new IllegalArgumentException(
                    "Fill at " + fillTime + " precedes the processed event horizon " + eventHorizon);
        }
        applyScheduledFunding(fillTime);
        advanceHorizonThrough(fillTime);
    }

    private int nextIndex() {
        lock.writeLock().lock();
        try {
            return nextTradeIndex++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private List<PlannedTradeFill> planTradeFills(Trade trade, List<TradeFill> fills) {
        if (fills.isEmpty()) {
            throw new IllegalArgumentException("trade must expose at least one fill");
        }
        TradeType tradeType = trade.getType();
        ExecutionSide tradeSide = sideOf(tradeType);
        ExecutionSide openSide = currentOpenSide();
        Position netOpenPosition = positionBook.netOpenPosition();
        int plannedNextIndex = nextTradeIndex;
        Num totalAmount = fills.getFirst().price().getNumFactory().zero();
        List<PlannedTradeFill> plannedTradeFills = new ArrayList<>(fills.size());
        for (TradeFill fill : fills) {
            PlannedTradeFill plannedTradeFill = planTradeFill(tradeType, tradeSide, fill, trade.getOrderId(),
                    trade.getCorrelationId(), trade.getTime(), plannedNextIndex);
            plannedTradeFills.add(plannedTradeFill);
            plannedNextIndex = Math.max(plannedNextIndex, plannedTradeFill.index() + 1);
            totalAmount = totalAmount.plus(plannedTradeFill.trade().getAmount());
        }
        if (openSide != null && tradeSide != openSide && netOpenPosition != null
                && totalAmount.isGreaterThan(netOpenPosition.getEntry().getAmount())) {
            throw new IllegalArgumentException("Exit amount " + totalAmount + " exceeds open position amount "
                    + netOpenPosition.getEntry().getAmount());
        }
        return List.copyOf(plannedTradeFills);
    }

    private PlannedTradeFill planTradeFill(TradeType tradeType, ExecutionSide tradeSide, TradeFill fill,
            String tradeOrderId, String tradeCorrelationId, Instant tradeTime, int plannedNextIndex) {
        if (fill.side() != null && fill.side() != tradeSide) {
            throw new IllegalArgumentException("Fill side " + fill.side() + " does not match trade type " + tradeType);
        }
        requireFillContract(fill);
        if (fill.price() == null || fill.price().isNaN()) {
            throw new IllegalArgumentException("Fill price must be set");
        }
        int resolvedIndex = fill.index() >= 0 ? fill.index() : plannedNextIndex;
        Instant executionTime = resolveExecutionTime(fill.time(), tradeTime);
        String orderId = chooseValue(fill.orderId(), tradeOrderId);
        String correlationId = chooseValue(fill.correlationId(), tradeCorrelationId);
        Num normalizedAmount = normalizeRecordedAmount(fill.amount(), fill.price());
        Num normalizedFee = normalizeFee(fill.fee(), fill.price());
        Trade plannedTrade;
        if (fill.futuresContract() != null) {
            TradeFill normalizedFill = fill.toBuilder()
                    .index(resolvedIndex)
                    .time(executionTime)
                    .amount(normalizedAmount)
                    .side(tradeSide)
                    .orderId(orderId)
                    .correlationId(correlationId)
                    .build();
            plannedTrade = Trade.fromFill(normalizedFill, getTransactionCostModel());
        } else {
            plannedTrade = recordedTrade(resolvedIndex, executionTime, fill.price(), normalizedAmount, normalizedFee,
                    tradeSide, orderId, correlationId);
        }
        validateFill(plannedTrade);
        return new PlannedTradeFill(resolvedIndex, plannedTrade);
    }

    private void requireFillContract(TradeFill fill) {
        if (futuresContract == null) {
            if (fill.futuresContract() != null) {
                throw new IllegalArgumentException(
                        "A spot record cannot record fills of futures contract " + fill.futuresContract().symbol());
            }
            return;
        }
        if (fill.futuresContract() == null) {
            throw new IllegalArgumentException(
                    "A futures record requires fills that reference the futures contract " + futuresContract.symbol());
        }
        if (!futuresContract.equals(fill.futuresContract())) {
            throw new IllegalArgumentException("Fill contract " + fill.futuresContract().symbol()
                    + " does not match the record contract " + futuresContract.symbol());
        }
    }

    private Trade tradeWithAssignedIndex(Trade trade, int index) {
        Objects.requireNonNull(trade, "trade");
        List<TradeFill> fills = Trade.executionFillsOf(trade);
        if (fills.isEmpty()) {
            throw new IllegalArgumentException("trade must expose at least one fill");
        }
        int firstIndex = fills.getFirst().index() >= 0 ? fills.getFirst().index() : 0;
        List<TradeFill> indexedFills = new ArrayList<>(fills.size());
        for (int i = 0; i < fills.size(); i++) {
            TradeFill fill = fills.get(i);
            int assignedIndex = fill.index() >= 0 ? index + (fill.index() - firstIndex) : index + i;
            indexedFills.add(fill.toBuilder()
                    .index(assignedIndex)
                    .orderId(chooseValue(fill.orderId(), trade.getOrderId()))
                    .correlationId(chooseValue(fill.correlationId(), trade.getCorrelationId()))
                    .build());
        }
        if (indexedFills.size() == 1) {
            TradeFill fill = indexedFills.getFirst();
            if (fill.price() == null || fill.price().isNaN()) {
                throw new IllegalArgumentException("Fill price must be set");
            }
            if (fill.futuresContract() != null) {
                // A futures trade carries contract and fee metadata that the scalar
                // recorded-trade path cannot represent.
                return Trade.fromFills(trade.getType(), indexedFills, trade.getCostModel());
            }
            return recordedTrade(fill.index(), resolveExecutionTime(fill.time(), trade.getTime()), fill.price(),
                    fill.amount(), normalizeFee(fill.fee(), fill.price()),
                    fill.side() == null ? sideOf(trade.getType()) : fill.side(), fill.orderId(), fill.correlationId());
        }
        return Trade.fromFills(trade.getType(), indexedFills, trade.getCostModel());
    }

    private void applyTradeInternal(int index, Trade trade, long sequence) {
        Objects.requireNonNull(trade, "trade");
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        validateFill(trade);
        Num fee = feeOf(trade);
        Num price = trade.getPricePerAsset();
        lock.writeLock().lock();
        try {
            advanceForFill(trade);
            nextTradeIndex = Math.max(nextTradeIndex, index + 1);
            long appliedSequence = sequence >= 0 ? sequence : nextSequence++;
            if (appliedSequence >= nextSequence) {
                nextSequence = appliedSequence + 1;
            }

            ExecutionSide tradeSide = sideOf(trade.getType());
            ExecutionSide openSide = currentOpenSide();
            if (openSide == null || tradeSide == openSide) {
                positionBook.recordEntry(index, trade, appliedSequence);
            } else {
                positionBook.recordExit(index, trade, appliedSequence);
            }

            if ((numFactory == null || numFactory.one().isNaN()) && price != null && !price.isNaN()) {
                numFactory = price.getNumFactory();
            }
            if (totalFees == null) {
                totalFees = defaultNumFactory().zero();
            }
            totalFees = totalFees.plus(fee);
            modificationCount++;
            tradesCache = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void applySyntheticInternal(int index, TradeType type, Num price, Num amount,
            CostModel transactionCostModel) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        Num normalizedAmount = normalizeSyntheticAmount(amount);
        applyTradeInternal(index, new BaseTrade(index, type, price, normalizedAmount, transactionCostModel), -1L);
    }

    private ExecutionSide currentOpenSide() {
        Position net = positionBook.netOpenPosition();
        if (net == null || !net.isOpened()) {
            return null;
        }
        return sideOf(net.getEntry().getType());
    }

    private List<Position> openPositionsSnapshot() {
        lock.readLock().lock();
        try {
            return List.copyOf(positionBook.openPositions());
        } finally {
            lock.readLock().unlock();
        }
    }

    private Position netOpenPositionSnapshot() {
        lock.readLock().lock();
        try {
            return positionBook.netOpenPosition();
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<Position> closedPositionsSnapshot() {
        lock.readLock().lock();
        try {
            return List.copyOf(positionBook.closedPositions());
        } finally {
            lock.readLock().unlock();
        }
    }

    private Position currentPositionView() {
        lock.readLock().lock();
        try {
            Position net = positionBook.netOpenPosition();
            if (net == null || !net.isOpened()) {
                return new Position(startingType, transactionCostModel, holdingCostModel);
            }
            return net;
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<Trade> tradesSnapshot() {
        lock.readLock().lock();
        try {
            if (tradesCache != null && tradesCacheVersion == modificationCount) {
                return tradesCache;
            }
        } finally {
            lock.readLock().unlock();
        }
        lock.writeLock().lock();
        try {
            if (tradesCache != null && tradesCacheVersion == modificationCount) {
                return tradesCache;
            }
            tradesCache = List.copyOf(buildTrades());
            tradesCacheVersion = modificationCount;
            return tradesCache;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private List<Trade> buildTrades() {
        List<SequencedTrade> trades = new ArrayList<>();
        for (PositionBook.ClosedPosition closed : positionBook.closedPositionsWithSequence()) {
            trades.add(new SequencedTrade(closed.position().getEntry(), closed.entrySequence()));
            trades.add(new SequencedTrade(closed.position().getExit(), closed.exitSequence()));
        }
        for (SequencedTrade openEntry : positionBook.openEntryTradesWithSequence()) {
            trades.add(openEntry);
        }
        trades.sort(Comparator.comparingInt((SequencedTrade trade) -> trade.trade().getIndex())
                .thenComparingLong(SequencedTrade::sequence));
        return trades.stream().map(SequencedTrade::trade).toList();
    }

    private Trade lastRecordedEntrySnapshot() {
        lock.readLock().lock();
        try {
            SequencedTrade candidate = null;
            for (PositionBook.ClosedPosition closed : positionBook.closedPositionsWithSequence()) {
                candidate = newerTrade(candidate, closed.position().getEntry(), closed.entrySequence());
            }
            for (SequencedTrade openEntry : positionBook.openEntryTradesWithSequence()) {
                candidate = newerTrade(candidate, openEntry.trade(), openEntry.sequence());
            }
            return candidate == null ? null : candidate.trade();
        } finally {
            lock.readLock().unlock();
        }
    }

    private Trade lastRecordedExitSnapshot() {
        lock.readLock().lock();
        try {
            SequencedTrade candidate = null;
            for (PositionBook.ClosedPosition closed : positionBook.closedPositionsWithSequence()) {
                candidate = newerTrade(candidate, closed.position().getExit(), closed.exitSequence());
            }
            return candidate == null ? null : candidate.trade();
        } finally {
            lock.readLock().unlock();
        }
    }

    private static SequencedTrade newerTrade(SequencedTrade current, Trade trade, long sequence) {
        if (trade == null) {
            return current;
        }
        if (current == null || sequence > current.sequence()) {
            return new SequencedTrade(trade, sequence);
        }
        return current;
    }

    private Num totalFeesSnapshot() {
        lock.readLock().lock();
        try {
            if (totalFees == null) {
                NumFactory factory = numFactory;
                if (factory == null && initialCapital != null) {
                    factory = initialCapital.getNumFactory();
                }
                if (factory == null && futuresContract != null) {
                    factory = futuresContract.contractSize().getNumFactory();
                }
                return (factory == null ? DoubleNumFactory.getInstance() : factory).zero();
            }
            return totalFees;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("startingType", startingType.name());
        json.addProperty("matchPolicy", matchPolicy.name());
        json.addProperty("startIndex", startIndex);
        json.addProperty("endIndex", endIndex);
        json.addProperty("nextTradeIndex", nextTradeIndex);
        json.addProperty("openPositionCount", openPositionsSnapshot().size());
        json.addProperty("closedPositionCount", closedPositionsSnapshot().size());
        json.addProperty("totalFees", getTotalFees().toString());

        List<Trade> trades = tradesSnapshot();
        json.addProperty("tradeCount", trades.size());
        JsonArray tradesJson = new JsonArray();
        for (Trade trade : trades) {
            try {
                tradesJson.add(JsonParser.parseString(trade.toString()));
            } catch (RuntimeException parseFailure) {
                tradesJson.add(trade.toString());
            }
        }
        json.add("trades", tradesJson);
        return GSON.toJson(json);
    }

    @Serial
    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        lock = new ReentrantReadWriteLock();
        transactionCostModel = resolveTransactionCostModel(transactionCostModel, futuresContract);
        holdingCostModel = defaultCostModel(holdingCostModel);
        positionBook.rehydrateCostModels(transactionCostModel, holdingCostModel);
        tradesCache = null;
        tradesCacheVersion = -1L;
        modificationCount = 0L;
        numFactory = null;
        if (cashFlows == null) {
            cashFlows = new ArrayList<>();
        }
        if (processedEvents == null) {
            processedEvents = new LinkedHashMap<>();
        }
        if (marketSnapshots == null) {
            marketSnapshots = new ArrayList<>();
        }
        if (fundingSchedule == null) {
            fundingSchedule = List.of();
        }
        if (positionSnapshots == null) {
            positionSnapshots = new ArrayList<>();
        }
        if (fundingCursor < 0 || fundingCursor > fundingSchedule.size()) {
            fundingCursor = fundingSchedule.size();
        }
    }

    /**
     * Rehydrates transient cost models after deserialization.
     *
     * @param holdingCostModel holding cost model, null defaults to
     *                         {@link ZeroCostModel}
     * @since 0.22.4
     */
    public void rehydrate(CostModel holdingCostModel) {
        rehydrate(transactionCostModel, holdingCostModel);
    }

    /**
     * Rehydrates transient cost models after deserialization.
     *
     * @param transactionCostModel transaction cost model, null defaults to
     *                             {@link ZeroCostModel}
     * @param holdingCostModel     holding cost model, null defaults to
     *                             {@link ZeroCostModel}
     * @since 0.22.4
     */
    public void rehydrate(CostModel transactionCostModel, CostModel holdingCostModel) {
        requireMutable("rehydrate(CostModel, CostModel)");
        CostModel resolvedTransaction = defaultCostModel(transactionCostModel);
        CostModel resolvedHolding = defaultCostModel(holdingCostModel);
        this.transactionCostModel = resolvedTransaction;
        this.holdingCostModel = resolvedHolding;
        positionBook.rehydrateCostModels(resolvedTransaction, resolvedHolding);
    }

    private static Instant resolveExecutionTime(Instant fillTime, Instant fallbackTime) {
        if (fillTime != null) {
            return fillTime;
        }
        return fallbackTime;
    }

    private static List<TradeFill> fillsAtPrice(List<TradeFill> fills, Num price) {
        return fills.stream().map(fill -> fill.toBuilder().price(price).build()).toList();
    }

    private static Trade recordedTrade(int index, Instant time, Num pricePerAsset, Num amount, Num fee,
            ExecutionSide side, String orderId, String correlationId) {
        return recordedTrade(index, time, pricePerAsset, amount, fee, side, orderId, correlationId, null, null);
    }

    private static Trade recordedTrade(int index, Instant time, Num pricePerAsset, Num amount, Num fee,
            ExecutionSide side, String orderId, String correlationId, FuturesContract futuresContract,
            List<TradeFee> feeComponents) {
        return recordedTrade(index, time, pricePerAsset, amount, fee, side, orderId, correlationId, futuresContract,
                feeComponents, null);
    }

    private static Trade recordedTrade(int index, Instant time, Num pricePerAsset, Num amount, Num fee,
            ExecutionSide side, String orderId, String correlationId, FuturesContract futuresContract,
            List<TradeFee> feeComponents, List<TradeFill> fillSlices) {
        if (futuresContract != null && fillSlices != null && !fillSlices.isEmpty()) {
            return Trade.fromFills(side.toTradeType(), fillSlices, RecordedTradeCostModel.INSTANCE);
        }
        if (futuresContract != null) {
            TradeFill fill = TradeFill.builder()
                    .index(index)
                    .time(time)
                    .price(pricePerAsset)
                    .amount(amount)
                    .side(side)
                    .orderId(orderId)
                    .correlationId(correlationId)
                    .futuresContract(futuresContract)
                    .fees(feeComponents == null ? List.of() : feeComponents)
                    .build();
            return Trade.fromFill(fill, RecordedTradeCostModel.INSTANCE);
        }
        Num normalizedFee = fee == null ? pricePerAsset.getNumFactory().zero() : fee;
        if (time != null) {
            return new BaseTrade(index, time, pricePerAsset, amount, normalizedFee, side, orderId, correlationId);
        }
        if (pricePerAsset.isNaN()) {
            return new BaseTrade(index, side.toTradeType(), pricePerAsset, amount, RecordedTradeCostModel.INSTANCE);
        }
        return Trade.fromFills(side.toTradeType(),
                List.of(new TradeFill(index, null, pricePerAsset, amount, normalizedFee, side, orderId, correlationId)),
                RecordedTradeCostModel.INSTANCE);
    }

    private static String chooseValue(String preferred, String fallback) {
        if (preferred != null) {
            return preferred;
        }
        return fallback;
    }

    private static void validateFill(Trade trade) {
        Num amount = trade.getAmount();
        if (amount == null || amount.isNaN() || amount.isZero() || amount.isNegative()) {
            throw new IllegalArgumentException("Fill amount must be positive");
        }
        Num price = trade.getPricePerAsset();
        if (price == null) {
            throw new IllegalArgumentException("Fill price must be set");
        }
        if (trade.getType() == null) {
            throw new IllegalArgumentException("Fill type must be set");
        }
    }

    private Num normalizeRecordedAmount(Num amount, Num reference) {
        if (amount != null && !amount.isNaN()) {
            if (amount.isNegative()) {
                throw new IllegalArgumentException("amount must be positive");
            }
            return amount;
        }
        return reference.getNumFactory().one();
    }

    private Num normalizeSyntheticAmount(Num amount) {
        if (amount != null && !amount.isNaN()) {
            // Synthetic enter/exit APIs carry direction in the trade type, so signed
            // quantities are normalized to their absolute magnitudes.
            return amount.isNegative() ? amount.abs() : amount;
        }
        return defaultNumFactory().one();
    }

    private Num normalizeFee(Num fee, Num reference) {
        if (fee != null && !fee.isNaN()) {
            return fee;
        }
        return reference.getNumFactory().zero();
    }

    private NumFactory defaultNumFactory() {
        if (numFactory != null) {
            return numFactory;
        }
        return DoubleNumFactory.getInstance();
    }

    private static Num feeOf(Trade trade) {
        Num fee = trade.getCost();
        if (fee != null && !fee.isNaN()) {
            return fee;
        }
        return trade.getPricePerAsset().getNumFactory().zero();
    }

    private static ExecutionSide sideOf(TradeType tradeType) {
        if (tradeType == TradeType.BUY) {
            return ExecutionSide.BUY;
        }
        return ExecutionSide.SELL;
    }

    private static CostModel defaultCostModel(CostModel costModel) {
        return costModel == null ? new ZeroCostModel() : costModel;
    }

    private static TradeType validateTrades(Trade... trades) {
        if (trades == null || trades.length == 0) {
            throw new IllegalArgumentException("At least one trade is required");
        }
        Objects.requireNonNull(trades[0], "trade[0]");
        Objects.requireNonNull(trades[0].getType(), "trade[0].type");
        return trades[0].getType();
    }

    private static Stream<Trade> tradesOf(Position position) {
        Objects.requireNonNull(position, "position must not be null");

        Trade entry = position.getEntry();
        if (entry == null) {
            throw new IllegalArgumentException("Position entry must not be null");
        }

        Trade exit = position.getExit();
        if (exit == null) {
            return Stream.of(entry);
        }
        return Stream.of(entry, exit);
    }

    private static Trade[] positionToTrades(Position position) {
        return tradesOf(position).toArray(Trade[]::new);
    }

    private static Trade[] positionsToTrades(List<Position> positions) {
        Objects.requireNonNull(positions, "positions must not be null");
        return positions.stream().flatMap(BaseTradingRecord::tradesOf).toArray(Trade[]::new);
    }

    private record RecordConfig(TradeType startingType, ExecutionMatchPolicy matchPolicy,
            CostModel transactionCostModel, CostModel holdingCostModel, PositionBook positionBook, Integer startIndex,
            Integer endIndex, int nextTradeIndex, long modificationCount, Num totalFees, NumFactory numFactory,
            long nextSequence, FuturesContract futuresContract, Num initialCapital, Num initialMarginRate,
            List<FuturesFunding> fundingSchedule) {
    }

    /**
     * Internal lot-matching ledger for fill-aware trading records.
     */
    private static final class PositionBook implements Serializable {

        @Serial
        private static final long serialVersionUID = -6897162194206253952L;

        private final TradeType startingType;
        private final ExecutionMatchPolicy matchPolicy;
        private transient CostModel transactionCostModel;
        private transient CostModel holdingCostModel;
        private final FuturesContract futuresContract;
        private final Deque<PositionLot> openLots;
        private final List<ClosedPosition> closedPositions;

        private PositionBook(TradeType startingType, ExecutionMatchPolicy matchPolicy, CostModel transactionCostModel,
                CostModel holdingCostModel, FuturesContract futuresContract) {
            Objects.requireNonNull(startingType, "startingType");
            Objects.requireNonNull(matchPolicy, "matchPolicy");
            Objects.requireNonNull(transactionCostModel, "transactionCostModel");
            Objects.requireNonNull(holdingCostModel, "holdingCostModel");
            this.startingType = startingType;
            this.matchPolicy = matchPolicy;
            this.transactionCostModel = transactionCostModel;
            this.holdingCostModel = holdingCostModel;
            this.futuresContract = futuresContract;
            this.openLots = new ArrayDeque<>();
            this.closedPositions = new ArrayList<>();
        }

        private void adopt(Position position, long entrySequence, long exitSequence) {
            Objects.requireNonNull(position, "position must not be null");
            FuturesContract positionContract = position.getFuturesContract();
            if (positionContract == null) {
                throw new IllegalArgumentException("A futures record only accepts futures positions");
            }
            if (!positionContract.equals(futuresContract)) {
                throw new IllegalArgumentException("Position contract " + positionContract.symbol()
                        + " does not match the record contract " + futuresContract.symbol());
            }
            if (position.isClosed()) {
                closedPositions.add(new ClosedPosition(position, entrySequence, exitSequence));
                return;
            }
            openLots.addLast(PositionLot.of(position, entrySequence));
        }

        private void recordEntry(int index, Trade trade, long sequence) {
            if (trade == null) {
                throw new IllegalArgumentException("trade must not be null");
            }
            if (trade.getAmount() == null || !trade.getAmount().isPositive()) {
                throw new IllegalArgumentException("trade amount must be positive");
            }
            if (trade.getPricePerAsset() == null) {
                throw new IllegalArgumentException("trade price must be set");
            }
            if (trade.getType() == null) {
                throw new IllegalArgumentException("trade type must be set");
            }
            if (matchPolicy == ExecutionMatchPolicy.AVG_COST) {
                normalizeAvgCostLots();
            }
            PositionLot lot = PositionLot.of(index, timeOf(trade), trade.getPricePerAsset(), sideOf(trade.getType()),
                    trade.getAmount(), feeOf(trade), trade.getOrderId(), trade.getCorrelationId(), sequence,
                    futuresContract, feesOf(trade), Trade.executionFillsOf(trade));
            if (matchPolicy == ExecutionMatchPolicy.AVG_COST && !openLots.isEmpty()) {
                PositionLot merged = openLots.removeFirst().merge(lot);
                openLots.addFirst(merged);
                return;
            }
            openLots.addLast(lot);
        }

        private List<Position> recordExit(int index, Trade trade, long sequence) {
            if (trade == null) {
                throw new IllegalArgumentException("trade must not be null");
            }
            if (trade.getAmount() == null || !trade.getAmount().isPositive()) {
                throw new IllegalArgumentException("trade amount must be positive");
            }
            if (trade.getPricePerAsset() == null) {
                throw new IllegalArgumentException("trade price must be set");
            }
            if (trade.getType() == null) {
                throw new IllegalArgumentException("trade type must be set");
            }
            if (matchPolicy == ExecutionMatchPolicy.AVG_COST) {
                normalizeAvgCostLots();
            }
            Num remaining = trade.getAmount();
            Num remainingFee = feeOf(trade);
            List<TradeFee> remainingExitComponents = trade.getFuturesContract() == null ? null
                    : List.copyOf(trade.getFees());
            List<Position> closed = new ArrayList<>();
            while (remaining.isPositive()) {
                PositionLot lot = nextLot(trade);
                if (lot == null) {
                    throw new IllegalStateException("No open lots to close");
                }
                Num lotAmount = lot.amount();
                if (matchPolicy == ExecutionMatchPolicy.SPECIFIC_ID && remaining.isGreaterThan(lotAmount)) {
                    throw new IllegalStateException("Exit amount exceeds matched lot amount");
                }
                Num closeAmount = remaining.isGreaterThan(lotAmount) ? lotAmount : remaining;
                Num exitFeePortion = remainingFee.isZero() ? remainingFee
                        : remainingFee.multipliedBy(closeAmount).dividedBy(remaining);
                FeeAllocation exitFeeComponents = allocateFeeComponents(remainingExitComponents, closeAmount,
                        remaining);
                TradeFill exitFill = Trade.executionFillsOf(trade).getFirst();
                ClosedPosition closedPosition = closeLot(lot, trade, index, closeAmount, exitFeePortion,
                        exitFeeComponents.allocated(), exitFill, sequence);
                remainingExitComponents = exitFeeComponents.remaining();
                closed.add(closedPosition.position());
                closedPositions.add(closedPosition);
                remaining = remaining.minus(closeAmount);
                remainingFee = remainingFee.minus(exitFeePortion);
            }
            return List.copyOf(closed);
        }

        private boolean hasOpenLots() {
            return !openLots.isEmpty();
        }

        private List<SequencedTrade> openEntryTradesWithSequence() {
            List<SequencedTrade> trades = new ArrayList<>(openLots.size());
            for (PositionLot lot : openLots) {
                Trade entry = recordedTrade(lot.entryIndex(), lot.entryTime(), lot.entryPrice(), lot.amount(),
                        lot.fee(), lot.side(), lot.orderId(), lot.correlationId(), lot.futuresContract(),
                        lot.feeComponents(), lot.fillsAtPrice(lot.entryPrice()));
                trades.add(new SequencedTrade(entry, lot.entrySequence()));
            }
            return List.copyOf(trades);
        }

        private List<Position> closedPositions() {
            return closedPositions.stream().map(ClosedPosition::position).toList();
        }

        private List<Position> openPositions() {
            List<Position> positions = new ArrayList<>();
            for (PositionLot lot : openLots) {
                Trade entry = recordedTrade(lot.entryIndex(), lot.entryTime(), lot.entryPrice(), lot.amount(),
                        lot.fee(), lot.side(), lot.orderId(), lot.correlationId(), lot.futuresContract(),
                        lot.feeComponents(), lot.fillsAtPrice(lot.entryPrice()));
                positions.add(new Position(entry, RecordedTradeCostModel.INSTANCE, holdingCostModel, lot.cashFlows()));
            }
            return positions;
        }

        /**
         * Nets the signed contracts held immediately before the supplied time.
         *
         * <p>
         * A slice entered at the boundary does not pay; a slice exited at the boundary
         * still pays, because it was still held immediately before the event timestamp.
         * </p>
         *
         * @param eventTime cash-flow event time
         * @return signed contracts held immediately before {@code eventTime}
         */
        private Num signedContractsAt(Instant eventTime) {
            NumFactory factory = futuresContract.contractSize().getNumFactory();
            Num total = factory.zero();
            for (CashFlowSlice slice : eligibleSlices(eventTime)) {
                Num quantity = factory.numOf(slice.quantity().getDelegate());
                total = slice.buy() ? total.plus(quantity) : total.minus(quantity);
            }
            return total;
        }

        /**
         * Allocates one observed cash flow across the slices held immediately before
         * its own timestamp, proportionally by contract quantity and assigning the
         * final residue exactly.
         *
         * @param cashFlow resolved cash flow to allocate
         */
        private void allocateCashFlow(FuturesCashFlow cashFlow) {
            List<CashFlowSlice> slices = eligibleSlices(cashFlow.time());
            Num amount = cashFlow.amount();
            Num settlement = cashFlow.settlementAmount();
            boolean zeroFlow = amount.isZero() && settlement.isZero();
            NumFactory factory = amount.getNumFactory();
            Num totalQuantity = factory.zero();
            for (CashFlowSlice slice : slices) {
                totalQuantity = totalQuantity.plus(factory.numOf(slice.quantity().getDelegate()));
            }
            if (totalQuantity.isZero()) {
                if (zeroFlow) {
                    return;
                }
                throw new IllegalArgumentException(
                        "No eligible futures exposure at " + cashFlow.time() + " for cash flow " + cashFlow.eventId());
            }
            if (zeroFlow) {
                return;
            }
            Num eventAmount = factory.numOf(amount.getDelegate());
            Num eventSettlement = factory.numOf(settlement.getDelegate());
            Num allocatedAmount = factory.zero();
            Num allocatedSettlement = factory.zero();
            for (int i = 0; i < slices.size(); i++) {
                CashFlowSlice slice = slices.get(i);
                Num quantity = factory.numOf(slice.quantity().getDelegate());
                boolean last = i == slices.size() - 1;
                Num portionAmount = last ? eventAmount.minus(allocatedAmount)
                        : proportional(eventAmount, quantity, totalQuantity);
                Num portionSettlement = last ? eventSettlement.minus(allocatedSettlement)
                        : proportional(eventSettlement, quantity, totalQuantity);
                allocatedAmount = allocatedAmount.plus(portionAmount);
                allocatedSettlement = allocatedSettlement.plus(portionSettlement);
                FuturesCashFlow portion = cashFlow.toBuilder()
                        .amount(portionAmount)
                        .settlementAmount(portionSettlement)
                        .build();
                if (slice.lot() != null) {
                    slice.lot().addCashFlow(portion);
                } else {
                    appendCashFlowToClosedPosition(slice.closedIndex(), portion);
                }
            }
        }

        private List<CashFlowSlice> eligibleSlices(Instant eventTime) {
            List<CashFlowSlice> slices = new ArrayList<>();
            for (PositionLot lot : openLots) {
                List<TradeFill> entryFills = lot.fills();
                if (entryFills.isEmpty()) {
                    Instant entryTime = lot.entryTime();
                    if (entryTime == null) {
                        throw new IllegalStateException("Futures cash flows require entry timestamps");
                    }
                    if (entryTime.isBefore(eventTime)) {
                        slices.add(new CashFlowSlice(lot.entrySequence(), lot.amount(), lot.side() == ExecutionSide.BUY,
                                lot, -1));
                    }
                    continue;
                }
                for (TradeFill fill : entryFills) {
                    Instant entryTime = fill.time();
                    if (entryTime == null) {
                        throw new IllegalStateException("Futures cash flows require entry timestamps");
                    }
                    if (entryTime.isBefore(eventTime)) {
                        slices.add(new CashFlowSlice(lot.entrySequence(), fill.amount(),
                                lot.side() == ExecutionSide.BUY, lot, -1));
                    }
                }
            }
            for (int i = 0; i < closedPositions.size(); i++) {
                slices.addAll(eligibleClosedPositionSlices(closedPositions.get(i), eventTime, i));
            }
            slices.sort(Comparator.comparingLong(CashFlowSlice::entrySequence));
            return slices;
        }

        private List<CashFlowSlice> eligibleClosedPositionSlices(ClosedPosition closed, Instant eventTime,
                int closedIndex) {
            Position position = closed.position();
            Trade entry = position.getEntry();
            if (entry == null) {
                return List.of();
            }
            List<TradeFill> entryFills = Trade.executionFillsOf(entry);
            NumFactory factory = entry.getAmount().getNumFactory();
            List<Num> remaining = new ArrayList<>(entryFills.size());
            for (TradeFill fill : entryFills) {
                if (fill.time() == null) {
                    throw new IllegalStateException("Futures cash flows require entry timestamps");
                }
                remaining.add(factory.numOf(fill.amount().getDelegate()));
            }
            Trade exit = position.getExit();
            if (exit != null) {
                for (TradeFill exitFill : Trade.executionFillsOf(exit)) {
                    Instant exitTime = exitFill.time();
                    if (exitTime == null) {
                        throw new IllegalStateException("Futures cash flows require exit timestamps");
                    }
                    if (!exitTime.isBefore(eventTime)) {
                        continue;
                    }
                    Num exitAmount = factory.numOf(exitFill.amount().getDelegate());
                    for (int i = 0; i < entryFills.size() && exitAmount.isPositive(); i++) {
                        if (!entryFills.get(i).time().isBefore(eventTime) || !remaining.get(i).isPositive()) {
                            continue;
                        }
                        Num matched = remaining.get(i).isLessThanOrEqual(exitAmount) ? remaining.get(i) : exitAmount;
                        remaining.set(i, remaining.get(i).minus(matched));
                        exitAmount = exitAmount.minus(matched);
                    }
                }
            }
            List<CashFlowSlice> slices = new ArrayList<>();
            for (int i = 0; i < entryFills.size(); i++) {
                if (entryFills.get(i).time().isBefore(eventTime) && remaining.get(i).isPositive()) {
                    slices.add(new CashFlowSlice(closed.entrySequence(), remaining.get(i),
                            entry.getType() == TradeType.BUY, null, closedIndex));
                }
            }
            return slices;
        }

        private void appendCashFlowToClosedPosition(int closedIndex, FuturesCashFlow cashFlow) {
            ClosedPosition closed = closedPositions.get(closedIndex);
            Position position = closed.position();
            List<FuturesCashFlow> updated = new ArrayList<>(position.getCashFlows());
            updated.add(cashFlow);
            Position rebuilt = new Position(position.getEntry(), position.getExit(), position.getTransactionCostModel(),
                    position.getHoldingCostModel(), List.copyOf(updated));
            closedPositions.set(closedIndex,
                    new ClosedPosition(rebuilt, closed.entrySequence(), closed.exitSequence()));
        }

        private record CashFlowSlice(long entrySequence, Num quantity, boolean buy, PositionLot lot, int closedIndex) {
        }

        private Position netOpenPosition() {
            if (openLots.isEmpty()) {
                return null;
            }
            Num totalAmount = null;
            Num totalCost = null;
            Num totalFees = null;
            Num inverseNotional = null;
            List<TradeFee> mergedComponents = new ArrayList<>();
            List<FuturesCashFlow> mergedCashFlows = new ArrayList<>();
            Instant earliest = null;
            boolean hasUnknownEntryTime = false;
            ExecutionSide side = null;
            int entryIndex = Integer.MAX_VALUE;
            for (PositionLot lot : openLots) {
                Num lotCost = lot.entryPrice().multipliedBy(lot.amount());
                totalAmount = totalAmount == null ? lot.amount() : totalAmount.plus(lot.amount());
                totalCost = totalCost == null ? lotCost : totalCost.plus(lotCost);
                totalFees = totalFees == null ? lot.fee() : totalFees.plus(lot.fee());
                if (lot.futuresContract() != null) {
                    Num lotInverseNotional = lot.amount().dividedBy(lot.entryPrice());
                    inverseNotional = inverseNotional == null ? lotInverseNotional
                            : inverseNotional.plus(lotInverseNotional);
                    mergedComponents.addAll(lot.feeComponents());
                    mergedCashFlows.addAll(lot.cashFlows());
                }
                if (lot.entryTime() == null) {
                    hasUnknownEntryTime = true;
                } else if (!hasUnknownEntryTime && (earliest == null || lot.entryTime().isBefore(earliest))) {
                    earliest = lot.entryTime();
                }
                entryIndex = Math.min(entryIndex, lot.entryIndex());
                if (side == null) {
                    side = lot.side();
                } else if (side != lot.side()) {
                    throw new IllegalStateException("Open lots contain mixed entry sides");
                }
            }
            if (inverseNotional != null && hasUnknownEntryTime) {
                throw new IllegalStateException("Futures positions require an entry time");
            }
            Num fee = totalFees == null ? totalCost.getNumFactory().zero() : totalFees;
            Num average = aggregateEntryPrice(totalAmount, totalCost, inverseNotional);
            List<TradeFill> mergedFills = new ArrayList<>();
            for (PositionLot lot : openLots) {
                mergedFills.addAll(lot.fillsAtPrice(average));
            }
            Trade entry = recordedTrade(entryIndex == Integer.MAX_VALUE ? 0 : entryIndex,
                    hasUnknownEntryTime ? null : earliest, average, totalAmount, fee, side, null, null,
                    inverseNotional == null ? null : futuresContract, mergedComponents, List.copyOf(mergedFills));
            return new Position(entry, RecordedTradeCostModel.INSTANCE, holdingCostModel, mergedCashFlows);
        }

        private Num aggregateEntryPrice(Num totalAmount, Num totalCost, Num inverseNotional) {
            if (inverseNotional != null) {
                if (!(futuresContract.settlementType() == FuturesContract.SettlementType.INVERSE)) {
                    return totalAmount == null || totalAmount.isZero() ? totalCost : totalCost.dividedBy(totalAmount);
                }
                return inverseNotional.isZero() || totalAmount == null || totalAmount.isZero() ? totalCost
                        : totalAmount.dividedBy(inverseNotional);
            }
            return totalAmount == null || totalAmount.isZero() ? totalCost : totalCost.dividedBy(totalAmount);
        }

        private PositionLot nextLot(Trade trade) {
            if (openLots.isEmpty()) {
                return null;
            }
            if (matchPolicy == ExecutionMatchPolicy.LIFO) {
                return openLots.peekLast();
            }
            if (matchPolicy == ExecutionMatchPolicy.SPECIFIC_ID) {
                return matchSpecificLot(trade);
            }
            return openLots.peekFirst();
        }

        private PositionLot matchSpecificLot(Trade trade) {
            String correlationId = trade.getCorrelationId();
            String key = correlationId != null && !correlationId.isBlank() ? correlationId : trade.getOrderId();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("Specific-id matching requires correlationId or orderId");
            }
            for (PositionLot lot : openLots) {
                if (key.equals(lot.correlationId()) || key.equals(lot.orderId())) {
                    return lot;
                }
            }
            throw new IllegalStateException("No open lot matches " + key);
        }

        private void normalizeAvgCostLots() {
            if (openLots.size() <= 1) {
                return;
            }
            PositionLot merged = null;
            while (!openLots.isEmpty()) {
                PositionLot lot = openLots.removeFirst();
                merged = merged == null ? lot : merged.merge(lot);
            }
            if (merged != null) {
                openLots.addFirst(merged);
            }
        }

        private ClosedPosition closeLot(PositionLot lot, Trade trade, int index, Num closeAmount, Num exitFeePortion,
                List<TradeFee> exitComponents, TradeFill exitFill, long exitSequence) {
            Num lotAmount = lot.amount();
            Num entryFeePortion = lot.fee().isZero() ? lot.fee()
                    : lot.fee().multipliedBy(closeAmount).dividedBy(lotAmount);
            List<TradeFill> entryFills = lot.allocateFills(closeAmount);
            List<TradeFee> entryComponents = lot.allocateFeeComponents(closeAmount);
            List<FuturesCashFlow> sliceCashFlows = lot.allocateCashFlows(closeAmount);
            if (closeAmount.isEqual(lotAmount)) {
                openLots.remove(lot);
            } else {
                lot.reduce(closeAmount, entryFeePortion);
            }
            Trade entry = recordedTrade(lot.entryIndex(), lot.entryTime(), lot.entryPrice(), closeAmount,
                    entryFeePortion, lot.side(), lot.orderId(), lot.correlationId(), lot.futuresContract(),
                    entryComponents, fillsAtPrice(entryFills, lot.entryPrice()));
            TradeFill recordedExitFill = exitFill.toBuilder()
                    .index(index)
                    .time(timeOf(trade))
                    .price(trade.getPricePerAsset())
                    .amount(closeAmount)
                    .side(sideOf(trade.getType()))
                    .orderId(trade.getOrderId())
                    .correlationId(trade.getCorrelationId())
                    .build();
            Trade exit = recordedTrade(index, timeOf(trade), trade.getPricePerAsset(), closeAmount, exitFeePortion,
                    sideOf(trade.getType()), trade.getOrderId(), trade.getCorrelationId(), trade.getFuturesContract(),
                    exitComponents, List.of(recordedExitFill));
            return new ClosedPosition(
                    new Position(entry, exit, RecordedTradeCostModel.INSTANCE, holdingCostModel, sliceCashFlows),
                    lot.entrySequence(), exitSequence);
        }

        private static FeeAllocation allocateFeeComponents(List<TradeFee> components, Num portion, Num total) {
            if (components == null) {
                return new FeeAllocation(null, null);
            }
            if (portion.isEqual(total)) {
                return new FeeAllocation(List.copyOf(components), List.of());
            }
            List<TradeFee> allocated = scaleFeeComponents(components, portion, total);
            List<TradeFee> remaining = new ArrayList<>(components.size());
            for (int i = 0; i < components.size(); i++) {
                TradeFee component = components.get(i);
                TradeFee allocatedComponent = allocated.get(i);
                Num settlement = component.settlementAmount();
                Num allocatedSettlement = allocatedComponent.settlementAmount();
                remaining.add(component.toBuilder()
                        .amount(component.amount().minus(allocatedComponent.amount()))
                        .settlementAmount(settlement == null || allocatedSettlement == null ? null
                                : settlement.minus(allocatedSettlement))
                        .build());
            }
            return new FeeAllocation(allocated, List.copyOf(remaining));
        }

        private static List<TradeFee> scaleFeeComponents(List<TradeFee> components, Num portion, Num total) {
            if (components == null || components.isEmpty()) {
                return List.of();
            }
            if (portion.isEqual(total)) {
                return List.copyOf(components);
            }
            List<TradeFee> scaled = new ArrayList<>(components.size());
            for (TradeFee component : components) {
                scaled.add(scale(component, portion, total));
            }
            return List.copyOf(scaled);
        }

        private static List<FuturesCashFlow> scaleCashFlows(List<FuturesCashFlow> cashFlows, Num portion, Num total) {
            if (cashFlows == null || cashFlows.isEmpty()) {
                return List.of();
            }
            if (portion.isEqual(total)) {
                return List.copyOf(cashFlows);
            }
            List<FuturesCashFlow> scaled = new ArrayList<>(cashFlows.size());
            for (FuturesCashFlow cashFlow : cashFlows) {
                scaled.add(scale(cashFlow, portion, total));
            }
            return List.copyOf(scaled);
        }

        private static TradeFee scale(TradeFee component, Num portion, Num total) {
            Num settlement = component.settlementAmount() == null ? component.amount() : component.settlementAmount();
            return component.toBuilder()
                    .amount(proportional(component.amount(), portion, total))
                    .settlementAmount(proportional(settlement, portion, total))
                    .build();
        }

        private static FuturesCashFlow scale(FuturesCashFlow cashFlow, Num portion, Num total) {
            return cashFlow.toBuilder()
                    .amount(proportional(cashFlow.amount(), portion, total))
                    .settlementAmount(proportional(cashFlow.settlementAmount(), portion, total))
                    .build();
        }

        private record FeeAllocation(List<TradeFee> allocated, List<TradeFee> remaining) {
        }

        private static Num proportional(Num value, Num portion, Num total) {
            return value.multipliedBy(portion).dividedBy(total);
        }

        private static List<TradeFee> feesOf(Trade trade) {
            if (trade.getFuturesContract() == null) {
                return null;
            }
            return List.copyOf(trade.getFees());
        }

        private static Num feeOf(Trade trade) {
            Num cost = trade.getCost();
            if (cost != null && !cost.isNaN()) {
                return cost;
            }
            Num price = trade.getPricePerAsset();
            if (price != null && !price.isNaN()) {
                return price.getNumFactory().zero();
            }
            Num amount = trade.getAmount();
            if (amount != null && !amount.isNaN()) {
                return amount.getNumFactory().zero();
            }
            return DoubleNumFactory.getInstance().zero();
        }

        private static Instant timeOf(Trade trade) {
            return trade.getTime();
        }

        private static ExecutionSide sideOf(TradeType tradeType) {
            Objects.requireNonNull(tradeType, "tradeType");
            if (tradeType == TradeType.BUY) {
                return ExecutionSide.BUY;
            }
            if (tradeType == TradeType.SELL) {
                return ExecutionSide.SELL;
            }
            throw new IllegalArgumentException("Unsupported trade type: " + tradeType);
        }

        private List<ClosedPosition> closedPositionsWithSequence() {
            return List.copyOf(closedPositions);
        }

        private void rehydrateCostModels(CostModel transactionCostModel, CostModel holdingCostModel) {
            this.transactionCostModel = transactionCostModel == null ? RecordedTradeCostModel.INSTANCE
                    : transactionCostModel;
            this.holdingCostModel = holdingCostModel == null ? new ZeroCostModel() : holdingCostModel;
            for (int i = 0; i < closedPositions.size(); i++) {
                ClosedPosition closed = closedPositions.get(i);
                Position position = closed.position();
                Position rehydrated = rehydratePosition(position, this.transactionCostModel, this.holdingCostModel);
                closedPositions.set(i, new ClosedPosition(rehydrated, closed.entrySequence(), closed.exitSequence()));
            }
        }

        private record ClosedPosition(Position position, long entrySequence,
                long exitSequence) implements Serializable {
        }

        private static final class PositionLot implements Serializable {

            @Serial
            private static final long serialVersionUID = -2333496329345071348L;

            private final int entryIndex;
            private final long entrySequence;
            private final Instant entryTime;
            private final Num entryPrice;
            private final ExecutionSide side;
            private final FuturesContract futuresContract;
            private Num amount;
            private Num fee;
            private final String orderId;
            private final String correlationId;
            private List<TradeFee> feeComponents;
            private List<FuturesCashFlow> cashFlows;
            private List<TradeFill> fills;

            private PositionLot(int entryIndex, Instant entryTime, Num entryPrice, ExecutionSide side, Num amount,
                    Num fee, String orderId, String correlationId, long entrySequence, FuturesContract futuresContract,
                    List<TradeFee> feeComponents, List<FuturesCashFlow> cashFlows, List<TradeFill> fills) {
                Objects.requireNonNull(entryPrice, "entryPrice");
                Objects.requireNonNull(side, "side");
                Objects.requireNonNull(amount, "amount");
                Objects.requireNonNull(fee, "fee");
                this.entryIndex = entryIndex;
                this.entrySequence = entrySequence;
                this.entryTime = entryTime;
                this.entryPrice = entryPrice;
                this.side = side;
                this.amount = amount;
                this.fee = fee;
                this.orderId = orderId;
                this.correlationId = correlationId;
                this.futuresContract = futuresContract;
                this.feeComponents = feeComponents;
                this.cashFlows = cashFlows == null ? List.of() : cashFlows;
                this.fills = fills == null ? List.of() : List.copyOf(fills);
            }

            private static PositionLot of(int entryIndex, Instant entryTime, Num entryPrice, ExecutionSide side,
                    Num amount, Num fee, String orderId, String correlationId, long entrySequence,
                    FuturesContract futuresContract, List<TradeFee> feeComponents, List<TradeFill> fills) {
                if (futuresContract != null && feeComponents == null) {
                    throw new IllegalStateException(
                            "A futures position lot requires the recorded fee components of its entry fill");
                }
                return new PositionLot(entryIndex, entryTime, entryPrice, side, amount, fee, orderId, correlationId,
                        entrySequence, futuresContract, feeComponents, List.of(), fills);
            }

            private static PositionLot of(Position position, long entrySequence) {
                Trade entry = Objects.requireNonNull(position.getEntry(), "position.entry");
                return new PositionLot(entry.getIndex(), entry.getTime(), entry.getPricePerAsset(),
                        sideOf(entry.getType()), entry.getAmount(), feeOf(entry), entry.getOrderId(),
                        entry.getCorrelationId(), entrySequence, position.getFuturesContract(),
                        List.copyOf(entry.getFees()), position.getCashFlows(), Trade.executionFillsOf(entry));
            }

            private int entryIndex() {
                return entryIndex;
            }

            private long entrySequence() {
                return entrySequence;
            }

            private Instant entryTime() {
                return entryTime;
            }

            private Num entryPrice() {
                return entryPrice;
            }

            private ExecutionSide side() {
                return side;
            }

            private Num amount() {
                return amount;
            }

            private Num fee() {
                return fee;
            }

            private FuturesContract futuresContract() {
                return futuresContract;
            }

            private List<TradeFee> feeComponents() {
                return feeComponents == null ? List.of() : feeComponents;
            }

            private List<FuturesCashFlow> cashFlows() {
                return cashFlows;
            }

            private List<TradeFill> fills() {
                return fills;
            }

            private List<TradeFill> fillsAtPrice(Num price) {
                if (fills.isEmpty()) {
                    return List.of();
                }
                return fills.stream().map(fill -> fill.toBuilder().price(price).build()).toList();
            }

            private void addCashFlow(FuturesCashFlow cashFlow) {
                List<FuturesCashFlow> updated = new ArrayList<>(cashFlows);
                updated.add(cashFlow);
                cashFlows = List.copyOf(updated);
            }

            private String orderId() {
                return orderId;
            }

            private String correlationId() {
                return correlationId;
            }

            /**
             * Allocates the closed portion of the recorded fee components and retains the
             * remainder on the open lot.
             *
             * @param portion closed amount
             * @return fee components allocated to the closed portion
             */
            private List<TradeFill> allocateFills(Num portion) {
                if (fills.isEmpty()) {
                    return List.of();
                }
                Num remaining = portion;
                List<TradeFill> allocated = new ArrayList<>();
                List<TradeFill> retained = new ArrayList<>();
                for (TradeFill fill : fills) {
                    if (!remaining.isPositive()) {
                        retained.add(fill);
                        continue;
                    }
                    Num closeAmount = remaining.isLessThan(fill.amount()) ? remaining : fill.amount();
                    TradeFill.Builder builder = fill.toBuilder().amount(closeAmount);
                    if (futuresContract == null) {
                        builder.fee(proportional(fill.fee(), closeAmount, fill.amount()));
                    } else {
                        builder.fees(scaleFeeComponents(fill.fees(), closeAmount, fill.amount()));
                    }
                    allocated.add(builder.build());
                    remaining = remaining.minus(closeAmount);
                    Num retainedAmount = fill.amount().minus(closeAmount);
                    if (retainedAmount.isPositive()) {
                        TradeFill.Builder retainedBuilder = fill.toBuilder().amount(retainedAmount);
                        if (futuresContract == null) {
                            retainedBuilder.fee(proportional(fill.fee(), retainedAmount, fill.amount()));
                        } else {
                            retainedBuilder.fees(scaleFeeComponents(fill.fees(), retainedAmount, fill.amount()));
                        }
                        retained.add(retainedBuilder.build());
                    }
                }
                fills = List.copyOf(retained);
                return List.copyOf(allocated);
            }

            private List<TradeFee> allocateFeeComponents(Num portion) {
                if (futuresContract == null) {
                    return null;
                }
                List<TradeFee> allocated = scaleFeeComponents(feeComponents, portion, amount);
                if (!portion.isEqual(amount)) {
                    feeComponents = scaleFeeComponents(feeComponents, amount.minus(portion), amount);
                }
                return allocated;
            }

            /**
             * Allocates the closed portion of the recorded cash flows and retains the
             * remainder on the open lot.
             *
             * @param portion closed amount
             * @return cash flows allocated to the closed portion
             */
            private List<FuturesCashFlow> allocateCashFlows(Num portion) {
                List<FuturesCashFlow> allocated = scaleCashFlows(cashFlows, portion, amount);
                if (!portion.isEqual(amount)) {
                    cashFlows = scaleCashFlows(cashFlows, amount.minus(portion), amount);
                }
                return allocated;
            }

            private PositionLot reduce(Num reduceAmount, Num reduceFee) {
                amount = amount.minus(reduceAmount);
                fee = fee.minus(reduceFee);
                return this;
            }

            private PositionLot merge(PositionLot other) {
                if (side != other.side) {
                    throw new IllegalArgumentException("cannot merge lots with different sides");
                }
                if (!Objects.equals(futuresContract, other.futuresContract)) {
                    throw new IllegalArgumentException("cannot merge lots with different futures contracts");
                }
                Num totalAmount = amount.plus(other.amount);
                Num mergedPrice = mergedEntryPrice(totalAmount, other);
                Num mergedFee = fee.plus(other.fee);
                int mergedIndex = Math.min(entryIndex, other.entryIndex);
                Instant mergedTime;
                if (entryTime == null || other.entryTime == null) {
                    mergedTime = null;
                } else {
                    mergedTime = entryTime.isBefore(other.entryTime) ? entryTime : other.entryTime;
                }
                long mergedSequence = Math.min(entrySequence, other.entrySequence);
                List<TradeFee> mergedComponents = feeComponents == null ? null : mergeComponents(other);
                return new PositionLot(mergedIndex, mergedTime, mergedPrice, side, totalAmount, mergedFee, null, null,
                        mergedSequence, futuresContract, mergedComponents, mergeCashFlows(other), mergeFills(other));
            }

            private Num mergedEntryPrice(Num totalAmount, PositionLot other) {
                if (futuresContract != null
                        && futuresContract.settlementType() == FuturesContract.SettlementType.INVERSE) {
                    Num inverseNotional = amount.dividedBy(entryPrice).plus(other.amount.dividedBy(other.entryPrice));
                    return inverseNotional.isZero() ? totalAmount.getNumFactory().zero()
                            : totalAmount.dividedBy(inverseNotional);
                }
                return entryPrice.multipliedBy(amount)
                        .plus(other.entryPrice.multipliedBy(other.amount))
                        .dividedBy(totalAmount);
            }

            private List<TradeFee> mergeComponents(PositionLot other) {
                List<TradeFee> merged = new ArrayList<>(feeComponents);
                merged.addAll(other.feeComponents());
                return List.copyOf(merged);
            }

            private List<TradeFill> mergeFills(PositionLot other) {
                if (fills.isEmpty() && other.fills().isEmpty()) {
                    return List.of();
                }
                List<TradeFill> merged = new ArrayList<>(fills);
                merged.addAll(other.fills());
                return List.copyOf(merged);
            }

            private List<FuturesCashFlow> mergeCashFlows(PositionLot other) {
                if (cashFlows.isEmpty() && other.cashFlows().isEmpty()) {
                    return List.of();
                }
                List<FuturesCashFlow> merged = new ArrayList<>(cashFlows);
                merged.addAll(other.cashFlows());
                return List.copyOf(merged);
            }

            @Serial
            private Object readResolve() throws InvalidObjectException {
                if (side == null) {
                    throw new InvalidObjectException("PositionLot.side is required");
                }
                if (entryPrice == null) {
                    throw new InvalidObjectException("PositionLot.entryPrice is required");
                }
                if (amount == null) {
                    throw new InvalidObjectException("PositionLot.amount is required");
                }
                if (fee == null) {
                    throw new InvalidObjectException("PositionLot.fee is required");
                }
                if (futuresContract == null) {
                    feeComponents = null;
                } else if (feeComponents == null) {
                    throw new InvalidObjectException("PositionLot.feeComponents is required for futures lots");
                }
                if (cashFlows == null) {
                    cashFlows = List.of();
                }
                if (fills == null) {
                    fills = List.of();
                }
                return this;
            }

            @Override
            public String toString() {
                JsonObject json = new JsonObject();
                json.addProperty("entryIndex", entryIndex);
                json.addProperty("entrySequence", entrySequence);
                json.addProperty("entryTime", entryTime == null ? null : entryTime.toString());
                json.addProperty("entryPrice", entryPrice.toString());
                json.addProperty("side", side.name());
                json.addProperty("amount", amount.toString());
                json.addProperty("fee", fee.toString());
                json.addProperty("orderId", orderId);
                json.addProperty("correlationId", correlationId);
                return GSON.toJson(json);
            }
        }

        @Override
        public String toString() {
            JsonObject json = new JsonObject();
            json.addProperty("startingType", startingType == null ? null : startingType.name());
            json.addProperty("matchPolicy", matchPolicy == null ? null : matchPolicy.name());
            json.addProperty("openLotCount", openLots.size());
            json.addProperty("closedPositionCount", closedPositions.size());

            JsonArray openLotsJson = new JsonArray();
            for (PositionLot lot : openLots) {
                openLotsJson.add(JsonParser.parseString(lot.toString()));
            }
            json.add("openLots", openLotsJson);
            return GSON.toJson(json);
        }

        @Serial
        private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
            inputStream.defaultReadObject();
            if (transactionCostModel == null) {
                transactionCostModel = RecordedTradeCostModel.INSTANCE;
            }
            if (holdingCostModel == null) {
                holdingCostModel = new ZeroCostModel();
            }
        }

        private static Position rehydratePosition(Position position, CostModel transactionCostModel,
                CostModel holdingCostModel) {
            if (position == null || position.getEntry() == null) {
                return position;
            }
            if (position.getExit() == null) {
                return new Position(position.getEntry(), transactionCostModel, holdingCostModel,
                        position.getCashFlows());
            }
            return new Position(position.getEntry(), position.getExit(), transactionCostModel, holdingCostModel,
                    position.getCashFlows());
        }
    }

    private record SequencedTrade(Trade trade, long sequence) {
    }

    private record PlannedTradeFill(int index, Trade trade) {
    }

    static record DebugSnapshot(TradeType startingType, List<Trade> trades, List<Position> closedPositions,
            Position currentPosition, List<Position> openPositions, Position netOpenPosition, Num totalFees) {

        DebugSnapshot {
            Objects.requireNonNull(startingType, "startingType");
            Objects.requireNonNull(totalFees, "totalFees");
            trades = trades == null ? List.of() : List.copyOf(trades);
            closedPositions = closedPositions == null ? List.of() : List.copyOf(closedPositions);
            openPositions = openPositions == null ? List.of() : List.copyOf(openPositions);
        }
    }
}
