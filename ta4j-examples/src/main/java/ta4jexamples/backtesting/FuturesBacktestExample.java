/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.FuturesTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.PositionSizer;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.commissions.TotalFeesCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

/**
 * Small native futures backtest showing contract, account, fee and sizing
 * setup.
 *
 * <p>
 * The funded run opens one linear perpetual position and the underfunded run
 * skips the entry because {@link PositionSizer#balance(Number)} returns zero.
 * </p>
 *
 * @since 0.25.1
 */
public final class FuturesBacktestExample {

    private static final Logger LOG = LogManager.getLogger(FuturesBacktestExample.class);
    private static final int ENTRY_INDEX = 1;
    private static final int EXIT_INDEX = 2;

    private FuturesBacktestExample() {
    }

    public static void main(String[] args) {
        BarSeries series = createBarSeries();
        NumFactory numFactory = series.numFactory();

        FuturesContract contract = FuturesContract.builder()
                .venue("example")
                .symbol("BTC-USD-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.one())
                .quantityIncrement(numFactory.one())
                .minimumQuantity(numFactory.one())
                .maximumQuantity(numFactory.numOf(10))
                .build();

        FuturesTransactionCostModel feeModel = FuturesTransactionCostModel.builder()
                .makerRate(numFactory.zero())
                .takerRate(numFactory.numOf(0.001))
                .build();
        Strategy strategy = new BaseStrategy(new FixedRule(ENTRY_INDEX), new FixedRule(EXIT_INDEX));
        BarSeriesManager manager = new BarSeriesManager(series, feeModel, new ZeroCostModel(),
                new TradeOnCurrentCloseModel());

        TradingRecord fundedRecord = run(manager, strategy, contract, feeModel, numFactory, 100);
        TradingRecord noEntryRecord = run(manager, strategy, contract, feeModel, numFactory, 10);

        logResults("funded account", series, fundedRecord);
        logResults("underfunded account (no entry)", series, noEntryRecord);
    }

    private static TradingRecord run(BarSeriesManager manager, Strategy strategy, FuturesContract contract,
            FuturesTransactionCostModel feeModel, NumFactory numFactory, int initialCapital) {
        Num capital = numFactory.numOf(initialCapital);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .startingType(TradeType.BUY)
                .futuresContract(contract)
                .initialCapital(capital)
                .initialMarginRate(numFactory.numOf(0.5))
                .transactionCostModel(feeModel)
                .holdingCostModel(new ZeroCostModel())
                .build();
        return manager.run(strategy, record, PositionSizer.balance(initialCapital));
    }

    private static void logResults(String label, BarSeries series, TradingRecord record) {
        Num profit = new NetProfitCriterion().calculate(series, record);
        Num accountReturn = new NetReturnCriterion(ReturnRepresentation.PERCENTAGE).calculate(series, record);
        Num fees = new TotalFeesCriterion().calculate(series, record);
        LOG.info("{}: positions={}, profit={}, accountReturn={}%, fees={}", label, record.getPositionCount(), profit,
                accountReturn, fees);
    }

    private static BarSeries createBarSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("native-futures-example").build();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        double[] closes = { 100, 100, 120, 120 };
        for (int i = 0; i < closes.length; i++) {
            double close = closes[i];
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(i + 1L, ChronoUnit.DAYS))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .add();
        }
        return series;
    }
}
