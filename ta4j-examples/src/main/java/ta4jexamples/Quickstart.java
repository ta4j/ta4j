package ta4jexamples;

import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.CashFlow;
import eu.verdelhan.ta4j.analysis.Runner;
import eu.verdelhan.ta4j.analysis.criteria.TotalProfitCriterion;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.strategies.IndicatorCrossedIndicatorStrategy;
import eu.verdelhan.ta4j.strategies.JustBuyOnceStrategy;
import eu.verdelhan.ta4j.strategies.StopGainStrategy;
import eu.verdelhan.ta4j.strategies.StopLossStrategy;
import eu.verdelhan.ta4j.strategies.SupportStrategy;
import java.util.List;
import ta4jexamples.loaders.CsvTradesLoader;

/**
 * Quickstart for ta4j.
 * <p>
 * Global example.
 */
public class Quickstart {

    public static void main(String[] args) {

        // Getting a time series (from any provider: CSV, web service, etc.)
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();


        // Getting the close price of the ticks
        double firstClosePrice = series.getTick(0).getClosePrice();
        System.out.println("First close price: " + firstClosePrice);
        // Or within an indicator:
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        // Here is the same close price:
        System.out.println(firstClosePrice == closePrice.getValue(0)); // equal to firstClosePrice

        // Getting the simple moving average (SMA) of the close price over the last 5 ticks
        SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
        // Here is the 5-ticks-SMA value at the 42nd index
        System.out.println("5-ticks-SMA value at the 42nd index: " + shortSma.getValue(42));

        // Getting a longer SMA (e.g. over the 30 last ticks)
        SMAIndicator longSma = new SMAIndicator(closePrice, 30);


        // Ok, now let's building our trading strategy!

        // Initial strategy:
        //  - Buy when 5-ticks SMA crosses over 30-ticks SMA
        //  - Sell when 5-ticks SMA crosses under 30-ticks SMA
        Strategy ourStrategy = new IndicatorCrossedIndicatorStrategy(shortSma, longSma);

        // Cutomizing our strategy...
        // We want to buy if the price goes below a defined price (e.g $800.00)
        ourStrategy = new SupportStrategy(closePrice, ourStrategy, 800d);
        // And we want to sell if the price looses more than 3%
        ourStrategy = new StopLossStrategy(closePrice, ourStrategy, 3);
        // Or if the price earns more than 2%
        ourStrategy = new StopGainStrategy(closePrice, ourStrategy, 2);


        // Running our juicy trading strategy...
        Runner ourRunner = new Runner(series, ourStrategy);
        List<Trade> trades = ourRunner.run();
        System.out.println("Number of trades for our strategy: " + trades.size());


        // Analysis

        // Getting the cash flow of the resulting trades
        CashFlow cashFlow = new CashFlow(series, trades);

        // Running a reference strategy (for comparison) in which we buy just once
        Runner referenceRunner = new Runner(series, new JustBuyOnceStrategy());
        List<Trade> referenceTrades = referenceRunner.run();
        System.out.println("Number of trades for reference strategy: " + referenceTrades.size());

        // Comparing our strategy to the just-buy-once strategy according to a criterion
        TotalProfitCriterion criterion = new TotalProfitCriterion();

        // Our strategy is better than a just-buy-once one
        System.out.println("Total profit for our strategy: " + criterion.calculate(series, trades));
        System.out.println("Total profit for reference strategy: " + criterion.calculate(series, referenceTrades));
    }
}
