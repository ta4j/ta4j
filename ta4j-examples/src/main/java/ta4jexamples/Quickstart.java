package ta4jexamples;

import org.ta4j.core.*;
import org.ta4j.core.analysis.criteria.AverageProfitableTradesCriterion;
import org.ta4j.core.analysis.criteria.RewardRiskRatioCriterion;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;
import org.ta4j.core.analysis.criteria.VersusBuyAndHoldCriterion;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.StopGainRule;
import org.ta4j.core.trading.rules.StopLossRule;
import ta4jexamples.loaders.CsvTradesLoader;

/**
 * Quickstart for ta4j.
 * </p>
 * Global example.
 */
public class Quickstart {

    public static void main(String[] args) {

        // Getting a time series (from any provider: CSV, web service, etc.)
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();


        // Getting the close price of the bars
        Num firstClosePrice = series.getBar(0).getClosePrice();
        System.out.println("First close price: " + firstClosePrice.doubleValue());
        // Or within an indicator:
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        // Here is the same close price:
        System.out.println(firstClosePrice.isEqual(closePrice.getValue(0))); // equal to firstClosePrice

        // Getting the simple moving average (SMA) of the close price over the last 5 bars
        SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
        // Here is the 5-bars-SMA value at the 42nd index
        System.out.println("5-bars-SMA value at the 42nd index: " + shortSma.getValue(42).doubleValue());

        // Getting a longer SMA (e.g. over the 30 last bars)
        SMAIndicator longSma = new SMAIndicator(closePrice, 30);


        // Ok, now let's building our trading rules!

        // Buying rules
        // We want to buy:
        //  - if the 5-bars SMA crosses over 30-bars SMA
        //  - or if the price goes below a defined price (e.g $800.00)
        Rule buyingRule = new CrossedUpIndicatorRule(shortSma, longSma)
                .or(new CrossedDownIndicatorRule(closePrice, 800));

        // Selling rules
        // We want to sell:
        //  - if the 5-bars SMA crosses under 30-bars SMA
        //  - or if the price loses more than 3%
        //  - or if the price earns more than 2%
        Rule sellingRule = new CrossedDownIndicatorRule(shortSma, longSma)
                .or(new StopLossRule(closePrice, series.numOf(3)))
                .or(new StopGainRule(closePrice, series.numOf(2)));

        // Running our juicy trading strategy...
        TimeSeriesManager seriesManager = new TimeSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(new BaseStrategy(buyingRule, sellingRule));
        System.out.println("Number of trades for our strategy: " + tradingRecord.getTradeCount());


        // Analysis

        // Getting the profitable trades ratio
        AnalysisCriterion profitTradesRatio = new AverageProfitableTradesCriterion();
        System.out.println("Profitable trades ratio: " + profitTradesRatio.calculate(series, tradingRecord));
        // Getting the reward-risk ratio
        AnalysisCriterion rewardRiskRatio = new RewardRiskRatioCriterion();
        System.out.println("Reward-risk ratio: " + rewardRiskRatio.calculate(series, tradingRecord));

        // Total profit of our strategy
        // vs total profit of a buy-and-hold strategy
        AnalysisCriterion vsBuyAndHold = new VersusBuyAndHoldCriterion(new TotalProfitCriterion());
        System.out.println("Our profit vs buy-and-hold profit: " + vsBuyAndHold.calculate(series, tradingRecord));

        // Your turn!
    }
}
