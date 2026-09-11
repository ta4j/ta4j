/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesCashFlow;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.FuturesFunding;
import org.ta4j.core.FuturesMarketSnapshot;
import org.ta4j.core.FuturesPositionSnapshot;
import org.ta4j.core.Position;
import org.ta4j.core.RealtimeBar;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.FuturesTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.commissions.TotalFeesCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Example demonstrating partial trade fills and custom trading records.
 *
 * <p>
 * This example shows how to use {@link org.ta4j.core.TradeFill} events directly
 * on a {@link org.ta4j.core.TradingRecord}. This is useful when connecting ta4j
 * to broker APIs that confirm orders asynchronously or execute orders in
 * multiple parts.
 * </p>
 */
public class TradeFillRecordingExample {

    private static final Logger LOG = LogManager.getLogger(TradeFillRecordingExample.class);
    private static final BarSeries ANALYSIS_SERIES = new BaseBarSeriesBuilder()
            .withName("trade-fill-recording-analysis")
            .build();
    private static final NumFactory NUM_FACTORY = ANALYSIS_SERIES.numFactory();
    private static final NetProfitCriterion NET_PROFIT_CRITERION = new NetProfitCriterion();
    private static final NetReturnCriterion NET_RETURN_CRITERION = new NetReturnCriterion();
    private static final GrossReturnCriterion GROSS_RETURN_CRITERION = new GrossReturnCriterion();
    private static final TotalFeesCriterion TOTAL_FEES_CRITERION = new TotalFeesCriterion();
    private static final Instant FUTURES_START = Instant.parse("2026-02-02T00:00:00Z");

    public static void main(String[] args) {
        LOG.info("Step 1: stream partial fills directly into TradingRecord");
        BaseTradingRecord streamingRecord = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), null, null);
        recordStreamingOrder(streamingRecord, "BUY entry order", entryFills());
        logOpenExposure("After BUY entry order", streamingRecord);
        recordStreamingOrder(streamingRecord, "SELL exit order", exitFills());
        logRecordSummary("Streaming fills", streamingRecord);

        LOG.info("Step 2: record the same exchange fills as grouped logical orders");
        BaseTradingRecord groupedTradeRecord = buildGroupedTradeRecord();
        logRecordSummary("Grouped order batches", groupedTradeRecord);

        LOG.info("Step 3: replay one partial exit under each ExecutionMatchPolicy");
        for (ExecutionMatchPolicy matchPolicy : ExecutionMatchPolicy.values()) {
            BaseTradingRecord matchPolicyRecord = buildMatchingPolicyRecord(matchPolicy);
            logMatchingPolicyOutcome(matchPolicy, matchPolicyRecord);
        }

        LOG.info("Step 4: settle native linear perpetual futures fills in the quote currency");
        logLinearPerpetualSettlement();
        logShortPerpetualMirror();

        LOG.info("Step 5: track funding, a partial close and variation margin on one funded record");
        logPartialCloseLifecycle();

        LOG.info("Step 6: settle an inverse perpetual in the base currency and observe a margin snapshot");
        logInversePerpetualSettlement();
        logInverseAverageEntryAgreement();
        logObservedMarginSnapshot();

        LOG.info(
                "The same TradingRecord APIs cover direct fills, grouped trades, lot matching and native perpetual futures.");
    }

    static BaseTradingRecord buildStreamingRecord() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        for (TradeFill fill : entryFills()) {
            record.operate(fill);
        }
        for (TradeFill fill : exitFills()) {
            record.operate(fill);
        }
        return record;
    }

    static BaseTradingRecord buildGroupedTradeRecord() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(Trade.fromFills(TradeType.BUY, entryFills()));
        record.operate(Trade.fromFills(TradeType.SELL, exitFills()));
        return record;
    }

    static Num closedNetProfit(TradingRecord record) {
        return NET_PROFIT_CRITERION.calculate(ANALYSIS_SERIES, record);
    }

    static BaseTradingRecord buildMatchingPolicyRecord(ExecutionMatchPolicy matchPolicy) {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, matchPolicy, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        for (TradeFill fill : matchingPolicyEntryFills()) {
            record.operate(fill);
        }

        String correlationId = matchPolicy == ExecutionMatchPolicy.SPECIFIC_ID ? "lot-b" : null;
        record.operate(new TradeFill(3, Instant.parse("2025-02-01T00:02:00Z"), NUM_FACTORY.numOf(120),
                NUM_FACTORY.one(), NUM_FACTORY.zero(), ExecutionSide.SELL, "policy-exit", correlationId));
        return record;
    }

    private static void recordStreamingOrder(TradingRecord record, String label, List<TradeFill> fills) {
        LOG.info("{} ({})", label, fills.get(0).orderId());
        for (TradeFill fill : fills) {
            record.operate(fill);
            LOG.info("  {} fill {} -> index={}, price={}, amount={}, fee={}, openPositions={}", fill.side(),
                    fill.correlationId(), fill.index(), fill.price(), fill.amount(), fill.fee(),
                    record.getOpenPositions().size());
        }
    }

    private static void logOpenExposure(String label, TradingRecord record) {
        Position currentPosition = record.getCurrentPosition();
        LOG.info("{} -> netOpenAmount={}, netAverageEntry={}, openLots={}, recordedFees={}", label,
                currentPosition.amount(), currentPosition.averageEntryPrice(), record.getOpenPositions().size(),
                record.getRecordedTotalFees());
        for (int i = 0; i < record.getOpenPositions().size(); i++) {
            Position openPosition = record.getOpenPositions().get(i);
            LOG.info("  open[{}] lot={} amount={} avgEntry={}", i, openPosition.getEntry().getCorrelationId(),
                    openPosition.amount(), openPosition.averageEntryPrice());
        }
    }

    private static void logRecordSummary(String label, TradingRecord record) {
        LOG.info("{} -> trades={}, closedPositions={}, openPositions={}, fees={}, closedProfit={}", label,
                record.getTrades().size(), record.getPositionCount(), record.getOpenPositions().size(),
                record.getRecordedTotalFees(), closedNetProfit(record));
        for (int i = 0; i < record.getPositions().size(); i++) {
            Position position = record.getPositions().get(i);
            LOG.info("  position[{}] entry={} @ {} amount={}, exit={} @ {}, profit={}", i,
                    position.getEntry().getIndex(), position.getEntry().getPricePerAsset(),
                    position.getEntry().getAmount(), position.getExit().getIndex(),
                    position.getExit().getPricePerAsset(), position.getProfit());
        }
    }

    private static void logMatchingPolicyOutcome(ExecutionMatchPolicy matchPolicy, TradingRecord record) {
        Position closedPosition = record.getPositions().get(0);
        Position currentPosition = record.getCurrentPosition();
        LOG.info("{} -> closedLot={} entry={} amount={}, remainingOpenLots={}, netOpenAmount={}, netAverageEntry={}",
                matchPolicy, closedPosition.getEntry().getCorrelationId(), closedPosition.getEntry().getPricePerAsset(),
                closedPosition.getEntry().getAmount(), record.getOpenPositions().size(), currentPosition.amount(),
                currentPosition.averageEntryPrice());
        for (int i = 0; i < record.getOpenPositions().size(); i++) {
            Position openPosition = record.getOpenPositions().get(i);
            LOG.info("  remaining[{}] lot={} amount={} avgEntry={}", i, openPosition.getEntry().getCorrelationId(),
                    openPosition.amount(), openPosition.averageEntryPrice());
        }
    }

    private static List<TradeFill> entryFills() {
        return List.of(
                new TradeFill(4, Instant.parse("2025-01-01T00:00:00Z"), NUM_FACTORY.hundred(), NUM_FACTORY.one(),
                        NUM_FACTORY.numOf(0.1), ExecutionSide.BUY, "entry-fill-1", "entry-order"),
                new TradeFill(5, Instant.parse("2025-01-01T00:01:00Z"), NUM_FACTORY.numOf(101), NUM_FACTORY.two(),
                        NUM_FACTORY.numOf(0.2), ExecutionSide.BUY, "entry-fill-2", "entry-order"));
    }

    private static List<TradeFill> exitFills() {
        return List.of(
                new TradeFill(8, Instant.parse("2025-01-01T00:02:00Z"), NUM_FACTORY.numOf(110), NUM_FACTORY.one(),
                        NUM_FACTORY.numOf(0.05), ExecutionSide.SELL, "exit-fill-1", "exit-order"),
                new TradeFill(9, Instant.parse("2025-01-01T00:03:00Z"), NUM_FACTORY.numOf(111), NUM_FACTORY.two(),
                        NUM_FACTORY.numOf(0.06), ExecutionSide.SELL, "exit-fill-2", "exit-order"));
    }

    private static List<TradeFill> matchingPolicyEntryFills() {
        return List.of(
                new TradeFill(1, Instant.parse("2025-02-01T00:00:00Z"), NUM_FACTORY.hundred(), NUM_FACTORY.two(),
                        NUM_FACTORY.zero(), ExecutionSide.BUY, "policy-entry-a", "lot-a"),
                new TradeFill(2, Instant.parse("2025-02-01T00:01:00Z"), NUM_FACTORY.numOf(106), NUM_FACTORY.one(),
                        NUM_FACTORY.zero(), ExecutionSide.BUY, "policy-entry-b", "lot-b"));
    }

    private static void logLinearPerpetualSettlement() {
        FuturesContract contract = linearCdePerpetual();
        BarSeries marks = markSeries("linear-cde-perpetual", 50_000, 51_000, 52_000);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(NUM_FACTORY.numOf(1_000))
                .initialMarginRate(NUM_FACTORY.numOf(0.1))
                .transactionCostModel(cdeCostModel())
                .build();

        // Both fills omit recorded fees, so the configured venue schedule charges
        // them from the settlement notional instead.
        record.operate(fill(contract, 0, barEnd(marks, 0), ExecutionSide.BUY, 3, 50_000, null));
        record.recordFunding(fundingEvent(contract, 1, barEnd(marks, 1), 0.0001, 51_000));
        record.operate(fill(contract, 2, barEnd(marks, 2), ExecutionSide.SELL, 3, 52_000, null));

        Position position = record.getPositions().get(0);
        Num contracts = position.getEntry().getAmount();
        Num entryPrice = position.getEntry().getPricePerAsset();
        Num exitPrice = position.getExit().getPricePerAsset();
        Num baseExposure = contract.baseQuantity(contracts, entryPrice);
        Num entryNotional = contract.settlementNotional(contracts, entryPrice);
        Num grossProfit = contract.profit(TradeType.BUY, contracts, entryPrice, exitPrice);
        Num rateCharge = entryNotional.multipliedBy(NUM_FACTORY.numOf(0.00001));
        Num floorCharge = contracts.multipliedBy(NUM_FACTORY.numOf(0.05));
        Num entryFee = position.getEntry().getCost();
        Num exitFee = position.getExit().getCost();
        Num executedFees = TOTAL_FEES_CRITERION.calculate(marks, record);
        Num funding = record.getCashFlows().get(0).amount();
        Num netProfit = position.getProfit();
        Num initialCapital = record.getInitialCapital();
        Num equity = initialCapital.plus(netProfit);
        Num marginRequirement = contract.marginRequirement(contracts, entryPrice, record.getInitialMarginRate());

        requireValue(0.03, baseExposure, "base exposure", contract.baseCurrency());
        requireValue(1_500, entryNotional, "entry settlement notional", contract.settlementCurrency());
        requireValue(60, grossProfit, "gross profit", contract.settlementCurrency());
        requireValue(0.015, rateCharge, "rate component of the entry fee", contract.settlementCurrency());
        requireValue(0.15, floorCharge, "per-contract minimum of the entry fee", contract.settlementCurrency());
        requireValue(0.15, entryFee, "entry fee charged by the configured model", contract.settlementCurrency());
        requireValue(0.15, exitFee, "exit fee charged by the configured model", contract.settlementCurrency());
        requireValue(0.3, executedFees, "executed settlement fees", contract.settlementCurrency());
        requireValue(-0.153, funding, "funding paid by the long side", contract.settlementCurrency());
        requireValue(59.547, netProfit, "net profit", contract.settlementCurrency());
        requireValue(1_000, initialCapital, "explicit initial capital", contract.settlementCurrency());
        requireValue(1_059.547, equity, "final equity", contract.settlementCurrency());
        requireValue(1.059547, NET_RETURN_CRITERION.calculate(marks, record), "account net return", "x");
        requireValue(1.06, GROSS_RETURN_CRITERION.calculate(marks, record), "account gross return", "x");
        requireValue(150, marginRequirement, "initial margin requirement", contract.settlementCurrency());

        LOG.info("  {} contracts x {} {} per contract = {} {} of base exposure, entry notional = {} {}", contracts,
                contract.contractSize(), contract.baseCurrency(), baseExposure, contract.baseCurrency(), entryNotional,
                contract.settlementCurrency());
        LOG.info("  BUY {} @ {} {} -> SELL {} @ {} {}: rate charge {} {} < per-contract minimum {} {} x {} = {} {}",
                contracts, entryPrice, contract.quoteCurrency(), contracts, exitPrice, contract.quoteCurrency(),
                rateCharge, contract.settlementCurrency(), NUM_FACTORY.numOf(0.05), contract.settlementCurrency(),
                contracts, floorCharge, contract.settlementCurrency());
        LOG.info("  CDE-style floor wins: entry fee {} {} + exit fee {} {} = {} {} of executed fees", entryFee,
                contract.settlementCurrency(), exitFee, contract.settlementCurrency(), executedFees,
                contract.settlementCurrency());
        LOG.info("  gross profit {} {} (linear payoff) + funding {} {} - fees {} {} = net profit {} {}", grossProfit,
                contract.settlementCurrency(), funding, contract.settlementCurrency(), executedFees,
                contract.settlementCurrency(), netProfit, contract.settlementCurrency());
        LOG.info("  capital {} {} -> equity {} {}, net return {}, gross return {} (gross restores fees and funding)",
                initialCapital, contract.settlementCurrency(), equity, contract.settlementCurrency(),
                NET_RETURN_CRITERION.calculate(marks, record), GROSS_RETURN_CRITERION.calculate(marks, record));
        LOG.info("  margin rate {} x entry notional {} {} = {} {} of initial margin", record.getInitialMarginRate(),
                entryNotional, contract.quoteCurrency(), marginRequirement, contract.settlementCurrency());
    }

    private static void logShortPerpetualMirror() {
        FuturesContract contract = linearCdePerpetual();
        BarSeries marks = markSeries("short-cde-perpetual", 52_000, 51_000, 50_000);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .startingType(TradeType.SELL)
                .futuresContract(contract)
                .initialCapital(NUM_FACTORY.numOf(1_000))
                .initialMarginRate(NUM_FACTORY.numOf(0.1))
                .transactionCostModel(cdeCostModel())
                .build();

        record.operate(fill(contract, 0, barEnd(marks, 0), ExecutionSide.SELL, 3, 52_000, null));
        record.recordFunding(fundingEvent(contract, 1, barEnd(marks, 1), 0.0001, 51_000));
        record.operate(fill(contract, 2, barEnd(marks, 2), ExecutionSide.BUY, 3, 50_000, null));

        Position position = record.getPositions().get(0);
        Num contracts = position.getEntry().getAmount();
        Num entryPrice = position.getEntry().getPricePerAsset();
        Num exitPrice = position.getExit().getPricePerAsset();
        Num grossProfit = contract.profit(TradeType.SELL, contracts, entryPrice, exitPrice);
        Num funding = record.getCashFlows().get(0).amount();
        Num fees = record.getTotalFees();
        Num netProfit = position.getProfit();
        Num marginRequirement = contract.marginRequirement(contracts, entryPrice, record.getInitialMarginRate());

        requireValue(60, grossProfit, "short gross profit", contract.settlementCurrency());
        requireValue(0.153, funding, "funding credited to the short side", contract.settlementCurrency());
        requireValue(0.3, fees, "settlement fees", contract.settlementCurrency());
        requireValue(59.853, netProfit, "short net profit", contract.settlementCurrency());
        requireValue(156, marginRequirement, "initial margin of the short entry", contract.settlementCurrency());
        requireTrue(!fees.isNegative(), "settlement fees stay charges on the short side");

        LOG.info(
                "  SELL {} @ {} {} then BUY {} @ {} {} -> gross profit {} {} and funding {} {} (a positive rate pays shorts)",
                contracts, entryPrice, contract.quoteCurrency(), contracts, exitPrice, contract.quoteCurrency(),
                grossProfit, contract.settlementCurrency(), funding, contract.settlementCurrency());
        LOG.info("  the mirrored side reverses price and funding signs only: fees stay charges at {} {} -> net {} {}",
                fees, contract.settlementCurrency(), netProfit, contract.settlementCurrency());
        LOG.info("  short entry notional {} {} -> initial margin {} {}",
                contract.settlementNotional(contracts, entryPrice), contract.settlementCurrency(), marginRequirement,
                contract.settlementCurrency());
    }

    private static void logPartialCloseLifecycle() {
        FuturesContract contract = linearCdePerpetual();
        BarSeries marks = markSeries("partial-close-cde-perpetual", 10_000, 11_000, 11_000, 12_000, 12_000, 12_000);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(NUM_FACTORY.numOf(1_000))
                .fundingSchedule(List.of(fundingEvent(contract, 1, barEnd(marks, 1), 0.001, 11_000),
                        fundingEvent(contract, 3, barEnd(marks, 3), 0.001, 12_000)))
                .build();

        record.operate(fill(contract, 0, barEnd(marks, 0), ExecutionSide.BUY, 4, 10_000, List.of(commission(4))));
        record.operate(fill(contract, 2, barEnd(marks, 2), ExecutionSide.SELL, 1, 11_000, List.of(commission(1))));

        Position closedSlice = record.getPositions().get(0);
        Position openSlice = record.getCurrentPosition();
        requireValue(10, closedSlice.getGrossProfit(), "gross profit of the closed slice",
                contract.settlementCurrency());
        requireValue(7.89, closedSlice.getProfit(), "net profit of the closed slice", contract.settlementCurrency());
        requireValue(-0.44, record.getCashFlows().get(0).amount(), "funding while 4 contracts were open",
                contract.settlementCurrency());
        requireValue(3, openSlice.getEntry().getAmount(), "remaining contracts", "contracts");
        requireValue(-3.33, openSlice.getRealizedProfit(2), "realized profit of the open slice",
                contract.settlementCurrency());
        requireValue(26.67, openSlice.getProfit(2, NUM_FACTORY.numOf(11_000)), "marked profit of the open slice",
                contract.settlementCurrency());

        LOG.info(
                "  BUY 4 @ {} {} with a 4 USD entry fee, then SELL 1 @ {} {} with a 1 USD exit fee: closed gross {} {}",
                closedSlice.getEntry().getPricePerAsset(), contract.quoteCurrency(),
                closedSlice.getExit().getPricePerAsset(), contract.quoteCurrency(), closedSlice.getGrossProfit(),
                contract.settlementCurrency());
        LOG.info("  funding events allocate per contract: closed slice keeps {} {} and the open slice carries {} {}",
                closedSlice.getProfit(), contract.settlementCurrency(), openSlice.getRealizedProfit(2),
                contract.settlementCurrency());
        LOG.info("  3 contracts remain open: marked profit at the current 11,000 USD mark = {} {}",
                openSlice.getProfit(2, NUM_FACTORY.numOf(11_000)), contract.settlementCurrency());

        record.recordCashFlow(variationMargin(contract, 4, barEnd(marks, 4), 20));

        Num fundingTotal = record.getCashFlows()
                .stream()
                .filter(cashFlow -> cashFlow.type() == FuturesCashFlow.Type.FUNDING)
                .map(FuturesCashFlow::amount)
                .reduce(NUM_FACTORY.zero(), Num::plus);
        // Position instances are immutable snapshots: re-read them so the allocation of
        // the
        // later funding and variation-margin events is visible.
        Position closedSliceAfterEvents = record.getPositions().get(0);
        Position openSliceAfterEvents = record.getCurrentPosition();
        Num openRealized = openSliceAfterEvents.getRealizedProfit(4);
        Num unrealized = openSliceAfterEvents.getUnrealizedProfit(NUM_FACTORY.numOf(12_000), 4);
        Num recordRealized = closedSliceAfterEvents.getRealizedProfit(4).plus(openRealized);
        Num totalProfit = closedSliceAfterEvents.getProfit()
                .plus(openSliceAfterEvents.getProfit(4, NUM_FACTORY.numOf(12_000)));

        requireValue(-0.36, record.getCashFlows().get(1).amount(), "funding while 3 contracts were open",
                contract.settlementCurrency());
        requireValue(-0.8, fundingTotal, "funding paid in total", contract.settlementCurrency());
        requireValue(20, record.getCashFlows().get(2).amount(), "variation margin settled",
                contract.settlementCurrency());
        requireValue(5, record.getTotalFees(), "executed fees before the final close", contract.settlementCurrency());
        requireValue(16.31, openRealized, "realized profit of the open slice after variation margin",
                contract.settlementCurrency());
        requireValue(40, unrealized, "remaining unrealized profit", contract.settlementCurrency());
        requireValue(56.31, openSliceAfterEvents.getProfit(4, NUM_FACTORY.numOf(12_000)),
                "marked profit of the open slice after variation margin", contract.settlementCurrency());
        requireValue(24.2, recordRealized, "realized profit of the whole record", contract.settlementCurrency());
        requireValue(64.2, totalProfit, "total profit before the final close", contract.settlementCurrency());

        LOG.info("  after another funding event the open slice realizes {} {} and marks {} {} unrealized", openRealized,
                contract.settlementCurrency(), unrealized, contract.settlementCurrency());
        LOG.info(
                "  total funding {} {} and executed fees {} {} are already paid cash: record realized {} {}, total {} {}",
                fundingTotal, contract.settlementCurrency(), record.getTotalFees(), contract.settlementCurrency(),
                recordRealized, contract.settlementCurrency(), totalProfit, contract.settlementCurrency());

        record.operate(fill(contract, 5, barEnd(marks, 5), ExecutionSide.SELL, 3, 12_000, List.of(commission(3))));

        Num firstProfit = record.getPositions().get(0).getProfit();
        Num secondProfit = record.getPositions().get(1).getProfit();
        Num finalProfit = firstProfit.plus(secondProfit);
        Num accountReturn = NET_RETURN_CRITERION.calculate(marks, record);
        Num compoundedReturn = NUM_FACTORY.one()
                .plus(firstProfit.dividedBy(record.getInitialCapital()))
                .multipliedBy(NUM_FACTORY.one().plus(secondProfit.dividedBy(record.getInitialCapital())));

        requireValue(7.89, firstProfit, "profit of the partial close", contract.settlementCurrency());
        requireValue(53.31, secondProfit, "profit of the final close", contract.settlementCurrency());
        requireValue(61.2, finalProfit, "final net profit", contract.settlementCurrency());
        requireValue(8, record.getTotalFees(), "final executed fees", contract.settlementCurrency());
        requireValue(1.0612, accountReturn, "account net return", "x");
        requireTrue(record.getOpenPositions().isEmpty(), "the partial close scenario must end flat");

        LOG.info("  final close of 3 contracts at the 12,000 USD mark: {} {} + {} {} = {} {}", firstProfit,
                contract.settlementCurrency(), secondProfit, contract.settlementCurrency(), finalProfit,
                contract.settlementCurrency());
        LOG.info("  account net return {} ({} if partial slices were compounded as separate investments)",
                accountReturn, compoundedReturn);
    }

    private static void logInversePerpetualSettlement() {
        FuturesContract contract = inverseCdePerpetual();
        BarSeries marks = markSeries("inverse-cde-perpetual", 20_000, 25_000, 25_000);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .transactionCostModel(cdeInverseCostModel())
                .build();

        // The maker flag on the entry selects the maker rate of the configured
        // schedule; the exit fill stays on the default taker rate.
        record.operate(fill(contract, 0, barEnd(marks, 0), ExecutionSide.BUY, 100, 20_000, null).toBuilder()
                .liquidity(RealtimeBar.Liquidity.MAKER)
                .build());
        record.recordFunding(fundingEvent(contract, 1, barEnd(marks, 1), 0.001, 25_000));
        record.operate(fill(contract, 2, barEnd(marks, 2), ExecutionSide.SELL, 100, 25_000, null));

        Position position = record.getPositions().get(0);
        Num contracts = position.getEntry().getAmount();
        Num entryPrice = position.getEntry().getPricePerAsset();
        Num exitPrice = position.getExit().getPricePerAsset();
        Num entryNotional = contract.settlementNotional(contracts, entryPrice);
        Num exitNotional = contract.settlementNotional(contracts, exitPrice);
        Num grossProfit = contract.profit(TradeType.BUY, contracts, entryPrice, exitPrice);
        Num entryFee = position.getEntry().getCost();
        Num exitFee = position.getExit().getCost();
        Num funding = record.getCashFlows().get(0).amount();
        Num netProfit = position.getProfit();

        requireValue(0.5, entryNotional, "entry settlement notional", contract.settlementCurrency());
        requireValue(0.4, exitNotional, "exit settlement notional", contract.settlementCurrency());
        requireValue(0.1, grossProfit, "gross profit", contract.settlementCurrency());
        requireValue(0.0001, entryFee, "maker entry fee", contract.settlementCurrency());
        requireValue(0.00012, exitFee, "taker exit fee", contract.settlementCurrency());
        requireValue(-0.0004, funding, "funding paid by the long side", contract.settlementCurrency());
        requireValue(0.09938, netProfit, "net profit", contract.settlementCurrency());

        LOG.info("  the contract settles in {} while it is quoted in {}", contract.settlementCurrency(),
                contract.quoteCurrency());
        LOG.info("  BUY {} @ {} {} -> entry notional {} {}, SELL {} @ {} {} -> exit notional {} {}", contracts,
                entryPrice, contract.quoteCurrency(), entryNotional, contract.settlementCurrency(), contracts,
                exitPrice, contract.quoteCurrency(), exitNotional, contract.settlementCurrency());
        LOG.info("  coin-settled payoff: {} {} (inverse profit = notional x the price difference in 1/price)",
                grossProfit, contract.settlementCurrency());
        LOG.info("  maker entry fee {} {} and taker exit fee {} {} are charged in the settlement currency too",
                entryFee, contract.settlementCurrency(), exitFee, contract.settlementCurrency());
        LOG.info("  funding {} {} -> net profit {} {}", funding, contract.settlementCurrency(), netProfit,
                contract.settlementCurrency());
    }

    private static void logInverseAverageEntryAgreement() {
        Num streamedAverage = inverseEntryRecord(true, TradeType.BUY).getCurrentPosition().averageEntryPrice();
        Num groupedAverage = inverseEntryRecord(false, TradeType.BUY).getCurrentPosition().averageEntryPrice();
        requireValue(80000d / 3d, streamedAverage, "harmonic average entry price of streamed fills", "USD");
        requireValue(80000d / 3d, groupedAverage, "harmonic average entry price of one grouped trade", "USD");
        requireNotEqual(30_000, streamedAverage, "arithmetic average entry price", "USD");

        BaseTradingRecord streamedLong = inverseRoundTrip(true, TradeType.BUY);
        BaseTradingRecord groupedLong = inverseRoundTrip(false, TradeType.BUY);
        BaseTradingRecord streamedShort = inverseRoundTrip(true, TradeType.SELL);
        BaseTradingRecord groupedShort = inverseRoundTrip(false, TradeType.SELL);
        Num streamedLongProfit = closedProfit(streamedLong);
        Num streamedShortProfit = closedProfit(streamedShort);
        requireValue(streamedLongProfit.doubleValue(), closedProfit(groupedLong), "grouped long profit", "BTC");
        requireValue(streamedShortProfit.doubleValue(), closedProfit(groupedShort), "grouped short profit", "BTC");

        LOG.info(
                "  two 1-contract fills at 20,000 USD and 40,000 USD -> average entry {} USD, not the arithmetic 30,000 USD",
                streamedAverage);
        LOG.info("  streaming the fills and grouping them into one trade agree: long {} BTC, short {} BTC",
                streamedLongProfit, streamedShortProfit);
    }

    private static Num closedProfit(TradingRecord record) {
        Num profit = NUM_FACTORY.zero();
        for (Position position : record.getPositions()) {
            profit = profit.plus(position.getProfit());
        }
        return profit;
    }

    private static void logObservedMarginSnapshot() {
        FuturesContract contract = inverseCdePerpetual();
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(NUM_FACTORY.numOf(0.05))
                .initialMarginRate(NUM_FACTORY.numOf(0.1))
                .build();

        Instant observationTime = FUTURES_START.plusSeconds(1);
        record.operate(fill(contract, 0, FUTURES_START, ExecutionSide.BUY, 100, 20_000, List.of()));

        Position position = record.getCurrentPosition();
        Num markPrice = NUM_FACTORY.numOf(24_000);
        Num entryNotional = contract.settlementNotional(NUM_FACTORY.numOf(100), NUM_FACTORY.numOf(20_000));
        Num initialMargin = contract.marginRequirement(NUM_FACTORY.numOf(100), NUM_FACTORY.numOf(20_000),
                record.getInitialMarginRate());
        Num maintenanceMargin = entryNotional.multipliedBy(NUM_FACTORY.numOf(0.005));
        Num unrealized = position.getUnrealizedProfit(markPrice, 0);
        Num equity = record.getInitialCapital().plus(unrealized);
        Num marginRatio = maintenanceMargin.dividedBy(equity);
        Num leverage = NUM_FACTORY.one().dividedBy(record.getInitialMarginRate());

        requireValue(1d / 12d, unrealized, "unrealized profit at the observed mark", contract.settlementCurrency());
        requireValue(0.05, initialMargin, "initial margin", contract.settlementCurrency());
        requireValue(0.0025, maintenanceMargin, "maintenance margin", contract.settlementCurrency());
        requireValue(0.01875, marginRatio, "maintenance margin ratio", "ratio");
        requireValue(10, leverage, "leverage", "x");

        record.recordMarketSnapshot(FuturesMarketSnapshot.builder()
                .contract(contract)
                .observedAt(observationTime)
                .source("cde-mark-price")
                .markPrice(markPrice)
                .fundingRate(NUM_FACTORY.numOf(0.001))
                .fundingInterval(Duration.ofHours(8))
                .maintenanceMarginRate(NUM_FACTORY.numOf(0.005))
                .maxLeverage(leverage)
                .build());
        record.recordPositionSnapshot(FuturesPositionSnapshot.builder()
                .contract(contract)
                .observedAt(observationTime)
                .source("cde-position-channel")
                .signedContracts(NUM_FACTORY.numOf(100))
                .marginMode(FuturesPositionSnapshot.MarginMode.CROSS)
                .averageEntryPrice(position.getEntry().getPricePerAsset())
                .collateral(record.getInitialCapital())
                .initialMargin(initialMargin)
                .maintenanceMargin(maintenanceMargin)
                .marginRatio(marginRatio)
                .leverage(leverage)
                .unrealizedPnl(unrealized)
                .build());

        requireTrue(record.getMarketSnapshots().size() == 1, "the observed mark must be recorded once");
        requireTrue(record.getPositionSnapshots().size() == 1, "the observed position must be recorded once");
        requireTrue(position.getExit() == null, "observing a snapshot must not close the position");
        requireTrue(record.getOpenPositions().size() == 1, "observing a snapshot must not change exposure");

        LOG.info("  observed margin only, no liquidation is simulated: mark {} {} -> unrealized {} {}", markPrice,
                contract.quoteCurrency(), unrealized, contract.settlementCurrency());
        LOG.info(
                "  equity {} {} (collateral + unrealized) vs maintenance margin {} {} -> margin ratio {}, leverage {}x",
                equity, contract.settlementCurrency(), maintenanceMargin, contract.settlementCurrency(), marginRatio,
                leverage);
        LOG.info(
                "  snapshots stay venue observations: {} market and {} position snapshot(s), position still open at {} {}",
                record.getMarketSnapshots().size(), record.getPositionSnapshots().size(),
                position.getEntry().getPricePerAsset(), contract.quoteCurrency());
    }

    private static BaseTradingRecord inverseEntryRecord(boolean streamedEntry, TradeType entryType) {
        FuturesContract contract = inverseCdePerpetual();
        BarSeries marks = markSeries("inverse-average-entry", 20_000, 40_000, 30_000);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .startingType(entryType)
                .futuresContract(contract)
                .build();
        ExecutionSide entrySide = entryType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
        List<TradeFill> entryFills = List.of(fill(contract, 0, barEnd(marks, 0), entrySide, 1, 20_000, List.of()),
                fill(contract, 1, barEnd(marks, 1), entrySide, 1, 40_000, List.of()));
        if (streamedEntry) {
            for (TradeFill entryFill : entryFills) {
                record.operate(entryFill);
            }
        } else {
            record.operate(Trade.fromFills(entryType, entryFills));
        }
        return record;
    }

    private static BaseTradingRecord inverseRoundTrip(boolean streamedEntry, TradeType entryType) {
        FuturesContract contract = inverseCdePerpetual();
        BarSeries marks = markSeries("inverse-average-entry", 20_000, 40_000, 30_000);
        BaseTradingRecord record = inverseEntryRecord(streamedEntry, entryType);
        ExecutionSide exitSide = entryType == TradeType.BUY ? ExecutionSide.SELL : ExecutionSide.BUY;
        record.operate(fill(contract, 2, barEnd(marks, 2), exitSide, 2, 30_000, List.of()));
        return record;
    }

    private static FuturesContract linearCdePerpetual() {
        return FuturesContract.builder()
                .symbol("BTC-PERP")
                .venue("CDE")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(NUM_FACTORY.numOf(0.01))
                .priceIncrement(NUM_FACTORY.one())
                .quantityIncrement(NUM_FACTORY.one())
                .minimumQuantity(NUM_FACTORY.one())
                .minimumNotional(NUM_FACTORY.numOf(10))
                .perpetualStyle(true)
                .trading24x7(true)
                .build();
    }

    private static FuturesContract inverseCdePerpetual() {
        return FuturesContract.builder()
                .symbol("BTC-PERP-INVERSE")
                .venue("CDE")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.INVERSE)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("BTC")
                .contractSize(NUM_FACTORY.numOf(100))
                .priceIncrement(NUM_FACTORY.one())
                .quantityIncrement(NUM_FACTORY.one())
                .minimumQuantity(NUM_FACTORY.one())
                .minimumNotional(NUM_FACTORY.numOf(10))
                .perpetualStyle(true)
                .trading24x7(true)
                .build();
    }

    private static FuturesTransactionCostModel cdeCostModel() {
        return FuturesTransactionCostModel.builder()
                .makerRate(NUM_FACTORY.numOf(0.00001))
                .takerRate(NUM_FACTORY.numOf(0.00001))
                .minimumPerContract(NUM_FACTORY.numOf(0.05))
                .source("cde-btc-perp-schedule")
                .build();
    }

    private static FuturesTransactionCostModel cdeInverseCostModel() {
        return FuturesTransactionCostModel.builder()
                .makerRate(NUM_FACTORY.numOf(0.0002))
                .takerRate(NUM_FACTORY.numOf(0.0003))
                .defaultLiquidity(RealtimeBar.Liquidity.TAKER)
                .source("cde-btc-perp-inverse-schedule")
                .build();
    }

    private static BarSeries markSeries(String name, double... closes) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).withNumFactory(NUM_FACTORY).build();
        for (int i = 0; i < closes.length; i++) {
            double closePrice = closes[i];
            series.barBuilder()
                    .timePeriod(Duration.ofHours(1))
                    .endTime(FUTURES_START.plusSeconds(i + 1))
                    .openPrice(closePrice)
                    .highPrice(closePrice)
                    .lowPrice(closePrice)
                    .closePrice(closePrice)
                    .volume(1)
                    .add();
        }
        return series;
    }

    private static Instant barEnd(BarSeries series, int index) {
        return series.getBar(index).getEndTime();
    }

    private static TradeFill fill(FuturesContract contract, int index, Instant time, ExecutionSide side, double amount,
            double price, List<TradeFee> fees) {
        TradeFill.Builder builder = TradeFill.builder()
                .index(index)
                .time(time)
                .price(NUM_FACTORY.numOf(price))
                .amount(NUM_FACTORY.numOf(amount))
                .side(side)
                .futuresContract(contract);
        if (fees != null) {
            builder.fees(fees);
        }
        return builder.build();
    }

    private static FuturesFunding fundingEvent(FuturesContract contract, int index, Instant time, double rate,
            double referencePrice) {
        return FuturesFunding.builder()
                .contract(contract)
                .eventId("funding-" + index)
                .index(index)
                .time(time)
                .rate(NUM_FACTORY.numOf(rate))
                .referencePrice(NUM_FACTORY.numOf(referencePrice))
                .build();
    }

    private static FuturesCashFlow variationMargin(FuturesContract contract, int index, Instant time, double amount) {
        return FuturesCashFlow.builder()
                .contract(contract)
                .type(FuturesCashFlow.Type.VARIATION_MARGIN)
                .eventId("vm-" + index)
                .index(index)
                .time(time)
                .amount(NUM_FACTORY.numOf(amount))
                .currency(contract.settlementCurrency())
                .build();
    }

    private static TradeFee commission(double amount) {
        return TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(NUM_FACTORY.numOf(amount))
                .currency("USD")
                .build();
    }

    private static void requireValue(double expected, Num actual, String label, String unit) {
        double value = actual == null ? Double.NaN : actual.doubleValue();
        if (!Double.isFinite(value) || Math.abs(value - expected) > 1.0e-9) {
            throw new IllegalStateException(
                    label + " must be " + expected + " " + unit + " but was " + value + " " + unit);
        }
    }

    private static void requireNotEqual(double unexpected, Num actual, String label, String unit) {
        if (actual != null && Math.abs(actual.doubleValue() - unexpected) <= 1.0e-9) {
            throw new IllegalStateException(label + " must not be " + unexpected + " " + unit);
        }
    }

    private static void requireTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
