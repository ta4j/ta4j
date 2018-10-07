package ta4jexamples.analysis;

import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TimeSeriesManager;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.*;
import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.MovingMomentumStrategy;

/**
 * This class diplays analysis criterion values after running a trading strategy over a time series.
 */
public class StrategyAnalysis {

    public static void main(String[] args) {

        // Getting the time series
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();
        // Building the trading strategy
        Strategy strategy = MovingMomentumStrategy.buildStrategy(series);
        // Running the strategy
        TimeSeriesManager seriesManager = new TimeSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);

        /*
          Analysis criteria
         */

        // Total profit
        TotalProfitCriterion totalProfit = new TotalProfitCriterion();
        System.out.println("Total profit: " + totalProfit.calculate(series, tradingRecord));
        // Number of bars
        System.out.println("Number of bars: " + new NumberOfBarsCriterion().calculate(series, tradingRecord));
        // Average profit (per bar)
        System.out.println("Average profit (per bar): " + new AverageProfitCriterion().calculate(series, tradingRecord));
        // Number of trades
        System.out.println("Number of trades: " + new NumberOfTradesCriterion().calculate(series, tradingRecord));
        // Profitable trades ratio
        System.out.println("Profitable trades ratio: " + new AverageProfitableTradesCriterion().calculate(series, tradingRecord));
        // Maximum drawdown
        System.out.println("Maximum drawdown: " + new MaximumDrawdownCriterion().calculate(series, tradingRecord));
        // Reward-risk ratio
        System.out.println("Reward-risk ratio: " + new RewardRiskRatioCriterion().calculate(series, tradingRecord));
        // Total transaction cost
        System.out.println("Total transaction cost (from $1000): " + new LinearTransactionCostCriterion(1000, 0.005).calculate(series, tradingRecord));
        // Buy-and-hold
        System.out.println("Buy-and-hold: " + new BuyAndHoldCriterion().calculate(series, tradingRecord));
        // Total profit vs buy-and-hold
        System.out.println("Custom strategy profit vs buy-and-hold strategy profit: " + new VersusBuyAndHoldCriterion(totalProfit).calculate(series, tradingRecord));
    }
}
