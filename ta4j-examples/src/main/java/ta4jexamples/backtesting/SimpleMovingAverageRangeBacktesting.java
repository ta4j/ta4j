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

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        // createParameter -> start stop step
        for(int i=3; i<=50; i++) {
            SMAIndicator sma = new SMAIndicator(closePrice, i);
            Rule entryRule = new UnderIndicatorRule(sma, closePrice);
            Rule exitRule = new OverIndicatorRule(sma, closePrice);
            Num calculate = backtesting.calculate(entryRule, exitRule, PrecisionNum.valueOf(50));

            System.out.println("----- " + i +" -----");
            backtesting.createTradingRecordReport();

            System.out.println(calculate);
            System.out.println();
        }
    }

    private static void createTradeReport(Trade trade) {
        System.out.println("Profit: " + trade.getProfit());
    }
}
