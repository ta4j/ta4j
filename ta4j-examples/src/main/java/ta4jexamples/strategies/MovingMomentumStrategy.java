package ta4jexamples.strategies;

import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.Runner;
import eu.verdelhan.ta4j.analysis.criteria.TotalProfitCriterion;
import eu.verdelhan.ta4j.indicators.oscillators.StochasticOscillatorDIndicator;
import eu.verdelhan.ta4j.indicators.oscillators.StochasticOscillatorKIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.EMAIndicator;
import eu.verdelhan.ta4j.indicators.trackers.MACDIndicator;
import eu.verdelhan.ta4j.strategies.AlwaysOperateStrategy;
import eu.verdelhan.ta4j.strategies.CombinedBuyAndSellStrategy;
import eu.verdelhan.ta4j.strategies.IndicatorOverIndicatorStrategy;
import eu.verdelhan.ta4j.strategies.ResistanceStrategy;
import eu.verdelhan.ta4j.strategies.SupportStrategy;
import java.util.List;
import ta4jexamples.loaders.CsvTradesLoader;

/**
 * Moving momentum strategy.
 * <p>
 * @see http://stockcharts.com/help/doku.php?id=chart_school:trading_strategies:moving_momentum
 */
public class MovingMomentumStrategy {

    /**
     * @param series a time series
     * @return a moving momentum strategy
     */
    public static Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator shortEma = new EMAIndicator(closePrice, 9);
        EMAIndicator longEma = new EMAIndicator(closePrice, 26);

        // The bias is bullish when the shorter-moving average moves above the longer moving average.
        // The bias is bearish when the shorter-moving average moves below the longer moving average.
        IndicatorOverIndicatorStrategy shortEmaAboveLongEma = new IndicatorOverIndicatorStrategy(longEma, shortEma);

        StochasticOscillatorKIndicator stochasticOscillK = new StochasticOscillatorKIndicator(series, 14);
        StochasticOscillatorDIndicator stochasticOscillD = new StochasticOscillatorDIndicator(stochasticOscillK);

        SupportStrategy support20 = new SupportStrategy(stochasticOscillK, new AlwaysOperateStrategy().opposite(), 20);
        ResistanceStrategy resist80 = new ResistanceStrategy(stochasticOscillK, new AlwaysOperateStrategy().opposite(), 80);

        MACDIndicator macd = new MACDIndicator(closePrice, 9, 26);
        EMAIndicator emaMacd = new EMAIndicator(macd, 18);

        IndicatorOverIndicatorStrategy macdAboveSignaLine = new IndicatorOverIndicatorStrategy(emaMacd, macd);

        return shortEmaAboveLongEma
                .and(new CombinedBuyAndSellStrategy(support20, resist80))
                .and(macdAboveSignaLine);
    }

    public static void main(String[] args) {

        // Getting the time series
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();

        // Building the trading strategy
        Strategy strategy = MovingMomentumStrategy.buildStrategy(series);

        // Running the strategy
        Runner runner = new Runner(series, strategy);
        List<Trade> trades = runner.run();
        System.out.println("Number of trades for the strategy: " + trades.size());

        // Analysis
        System.out.println("Total profit for the strategy: " + new TotalProfitCriterion().calculate(series, trades));
    }
}
