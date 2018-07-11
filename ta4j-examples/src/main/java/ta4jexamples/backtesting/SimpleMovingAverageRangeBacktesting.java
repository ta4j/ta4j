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
        ProfitLossCriterion criterion = new ProfitLossCriterion();

        Backtesting backtesting = new Backtesting(series, criterion);

        int start = 3;
        int stop = 50;
        int step = 5;

        for(int i=start; i<=stop; i += step) {
            Strategy strategy = new BaseStrategy(
                    createEntryRule(series, i),
                    createExitRule(series, i)
            );
            backtesting.addStrategy(strategy);
        }
        backtesting.calculate(PrecisionNum.valueOf(50));
        backtesting.printBacktestingResults();
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
