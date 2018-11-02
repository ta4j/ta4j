package ta4jexamples.backtesting;

import org.ta4j.core.BacktestExecutor;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Order;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;
import ta4jexamples.loaders.CsvBarsLoader;

import java.util.ArrayList;
import java.util.List;

public class SimpleMovingAverageRangeBacktest {

    public static void main(String[] args) {
        TimeSeries series = CsvBarsLoader.loadAppleIncSeries();


        int start = 3;
        int stop = 50;
        int step = 5;

        final List<Strategy> strategies = new ArrayList<>();
        for (int i = start; i <= stop; i += step) {
            Strategy strategy = new BaseStrategy("Sma(" + i + ")", createEntryRule(series, i), createExitRule(series, i));
            strategies.add(strategy);
        }
        BacktestExecutor backtestExecutor = new BacktestExecutor(series);
        backtestExecutor.execute(strategies, PrecisionNum.valueOf(50), Order.OrderType.BUY);
    }

    private static Rule createEntryRule(TimeSeries series, int barCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, barCount);
        return new UnderIndicatorRule(sma, closePrice);
    }

    private static Rule createExitRule(TimeSeries series, int barCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, barCount);
        return new OverIndicatorRule(sma, closePrice);
    }
}
