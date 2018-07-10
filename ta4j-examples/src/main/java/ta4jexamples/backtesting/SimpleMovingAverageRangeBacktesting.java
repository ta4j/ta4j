package ta4jexamples.backtesting;

import org.ta4j.core.*;
import org.ta4j.core.analysis.criteria.*;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;
import ta4jexamples.loaders.CsvBarsLoader;

public class SimpleMovingAverageRangeBacktesting {

    public static void main(String[] args) throws InterruptedException {
        TimeSeries series = CsvBarsLoader.loadAppleIncSeries();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        TimeSeriesManager seriesManager = new TimeSeriesManager(series);

        for(int i=3; i<=50; i++) {

            SMAIndicator sma = new SMAIndicator(closePrice, i);
            Strategy strategy = new BaseStrategy(
                    new UnderIndicatorRule(sma, closePrice),
                    new OverIndicatorRule(sma, closePrice)
            );
            TradingRecord tradingRecord = seriesManager.run(strategy, Order.OrderType.BUY, PrecisionNum.valueOf(50));
            ProfitLossCriterion criterion = new ProfitLossCriterion();
            Num calculate = criterion.calculate(series, tradingRecord);

            System.out.println("----- " + i +" -----");
            createTradingRecordReport(tradingRecord);

            System.out.println(calculate);
            System.out.println();
        }
    }

    private static void createTradingRecordReport(TradingRecord tradingRecord) {
        //tradingRecord.getTrades().stream().forEach(trade -> createTradeReport(trade));

        System.out.println("Total trades: " + tradingRecord.getTradeCount());
        System.out.println("Total profit: " + tradingRecord.getTotalProfit() + " Trade count: " + tradingRecord.getProfitTradeCount());
        System.out.println("Total loss: " + tradingRecord.getTotalLoss() + " Trade count: " + tradingRecord.getLossTradeCount());
        System.out.println("Break event trade count: " + tradingRecord.getBreakEvenTradeCount());
    }

    private static void createTradeReport(Trade trade) {
        System.out.println("Profit: " + trade.getProfit());
    }
}
