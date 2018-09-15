package ta4jexamples.backtesting;

import org.ta4j.core.*;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;
import ta4jexamples.loaders.CsvBarsLoader;

public class SimpleMovingAverageRangeBacktest {

    public static void main(String[] args) throws InterruptedException {
        TimeSeries series = CsvBarsLoader.loadAppleIncSeries();

        Backtest backtest = new Backtest(series);

        int start = 3;
        int stop = 50;
        int step = 5;

        for(int i=start; i<=stop; i += step) {
            Strategy strategy = new BaseStrategy(
                    "Sma("+ i +")",
                    createEntryRule(series, i),
                    createExitRule(series, i)
            );
            backtest.addStrategy(strategy);
        }
        backtest.calculate(Order.OrderType.BUY, PrecisionNum.valueOf(50));
    }

    private static Rule createEntryRule(TimeSeries series, int barCount) {
        Indicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, barCount);
        return new UnderIndicatorRule(sma, closePrice);
    }

    private static Rule createExitRule(TimeSeries series, int barCount) {
        Indicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, barCount);
        return new OverIndicatorRule(sma, closePrice);
    }
}
