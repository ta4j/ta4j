package ta4jexamples.num;

import org.ta4j.core.*;
import org.ta4j.core.Num.BigDecimalNum;
import org.ta4j.core.Num.DoubleNum;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.trading.rules.IsEqualRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import java.time.ZonedDateTime;
import java.util.Random;

public class DoubleNumVsBigDecimalNum {

    public static void main(String args[]) {
        BaseTimeSeries.SeriesBuilder timeSeriesBuilder = new BaseTimeSeries.SeriesBuilder();
        TimeSeries seriesD = timeSeriesBuilder.withName("Sample Series Double    ").withNumTypeOf(DoubleNum::valueOf).build();
        TimeSeries seriesB = timeSeriesBuilder.withName("Sample Series BigDecimal").withNumTypeOf(BigDecimalNum::valueOf).build();

        int[] randoms = new Random().ints(1000000, 80, 100).toArray();
        for (int i = 0; i < randoms.length; i++) {
            ZonedDateTime date = ZonedDateTime.now().minusSeconds(100000 - i);
            seriesD.addBar(date, randoms[i], randoms[i] + 21, randoms[i] - 21, randoms[i] - 5);
            seriesB.addBar(date, randoms[i], randoms[i] + 21, randoms[i] - 21, randoms[i] - 5);
        }
        test(seriesB);
        test(seriesD);
    }

    public static void test(TimeSeries series){
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePriceIndicator,100);
        MACDIndicator macdIndicator = new MACDIndicator(rsi);
        EMAIndicator ema = new EMAIndicator(rsi,12);
        EMAIndicator emaLong = new EMAIndicator(rsi,26);
        DifferenceIndicator macdIndicator2 = new DifferenceIndicator(ema,emaLong);

        Rule entry = new IsEqualRule(macdIndicator,macdIndicator2);
        Rule exit = new UnderIndicatorRule(new MinPriceIndicator(series), new MaxPriceIndicator(series));
        Strategy strategy1 = new BaseStrategy(entry, exit); // enter/exit every tick

        long start = System.currentTimeMillis();
        TimeSeriesManager manager = new TimeSeriesManager(series);
        TradingRecord record1 = manager.run(strategy1);
        TotalProfitCriterion profit1 = new TotalProfitCriterion();
        double profitResult1 = profit1.calculate(series, record1);
        long end = System.currentTimeMillis();

        System.out.printf("[%s]\n" +
                "    -Time:   %s ms.\n" +
                "    -Profit: %s \n" +
                "    -Bars:   %s\n \n",series.getName(),(end-start),profitResult1, series.getBarCount());
    }
}
