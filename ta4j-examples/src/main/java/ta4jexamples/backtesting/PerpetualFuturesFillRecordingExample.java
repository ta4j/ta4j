/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.commissions.TotalFeesCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Records venue-confirmed linear perpetual fills and funding settlements, in
 * base-asset amounts and quote-currency prices, fees and funding.
 *
 * @since 0.29.1
 */
public class PerpetualFuturesFillRecordingExample {

    private static final Logger LOG = LogManager.getLogger(PerpetualFuturesFillRecordingExample.class);
    static final BarSeries ANALYSIS_SERIES = new BaseBarSeriesBuilder().withName("perpetual-fill-analysis").build();

    /**
     * Prints the reconciled components of the completed offline sequence.
     *
     * @param args unused
     * @since 0.29.1
     */
    public static void main(String[] args) {
        BaseTradingRecord record = buildRecord();
        Num gross = new GrossProfitLossCriterion().calculate(ANALYSIS_SERIES, record);
        Num fees = new TotalFeesCriterion().calculate(ANALYSIS_SERIES, record);
        Num holding = record.getPositions()
                .stream()
                .map(Position::getHoldingCost)
                .reduce(ANALYSIS_SERIES.numFactory().zero(), Num::plus);
        Num net = new NetProfitLossCriterion().calculate(ANALYSIS_SERIES, record);
        LOG.info("Gross profit: {}", gross);
        LOG.info("Recorded fees: {}", fees);
        LOG.info("Net recorded holding costs: {}", holding);
        LOG.info("Net profit: {}", net);
    }

    static BaseTradingRecord buildRecord() {
        NumFactory nums = ANALYSIS_SERIES.numFactory();
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                RecordedTradeCostModel.INSTANCE, new ZeroCostModel(), null, null);

        record.operate(new TradeFill(0, null, nums.numOf(100), nums.numOf(2), nums.numOf(2), ExecutionSide.BUY,
                "entry-1", "lot-1"));
        record.operate(
                new TradeFill(1, null, nums.numOf(105), nums.one(), nums.one(), ExecutionSide.BUY, "entry-2", "lot-2"));
        record.recordHoldingCost(3, nums.numOf(6)); // paid, allocated 4 and 2

        record.operate(
                new TradeFill(4, null, nums.numOf(110), nums.one(), nums.one(), ExecutionSide.SELL, "exit-1", null));
        record.recordHoldingCost(5, nums.numOf(-2)); // received, allocated to the remaining exposure
        record.operate(new TradeFill(6, null, nums.numOf(110), nums.numOf(2), nums.numOf(2), ExecutionSide.SELL,
                "exit-2", null));
        return record;
    }
}
