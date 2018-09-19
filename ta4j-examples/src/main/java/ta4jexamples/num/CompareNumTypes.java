/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package ta4jexamples.num;

import org.ta4j.core.*;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.trading.rules.IsEqualRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

import java.time.ZonedDateTime;
import java.util.Random;

public class CompareNumTypes {

    private static final int NUMBARS = 10000;

    public static void main(String args[]) {
        BaseTimeSeries.SeriesBuilder timeSeriesBuilder = new BaseTimeSeries.SeriesBuilder();
        TimeSeries seriesD = timeSeriesBuilder.withName("Sample Series Double    ").withNumTypeOf(DoubleNum::valueOf).build();
        TimeSeries seriesP = timeSeriesBuilder.withName("Sample Series PrecisionNum 32").withNumTypeOf(PrecisionNum::valueOf).build();
        TimeSeries seriesPH = timeSeriesBuilder.withName("Sample Series PrecisionNum 256").withNumTypeOf(number -> PrecisionNum.valueOf(number.toString(), 256)).build();

        int[] randoms = new Random().ints(NUMBARS, 80, 100).toArray();
        for (int i = 0; i < randoms.length; i++) {
            ZonedDateTime date = ZonedDateTime.now().minusSeconds(NUMBARS - i);
            seriesD.addBar(date, randoms[i], randoms[i] + 21, randoms[i] - 21, randoms[i] - 5);
            seriesP.addBar(date, randoms[i], randoms[i] + 21, randoms[i] - 21, randoms[i] - 5);
            seriesPH.addBar(date, randoms[i], randoms[i] + 21, randoms[i] - 21, randoms[i] - 5);
        }
        Num D = PrecisionNum.valueOf(test(seriesD).toString(), 256);
        Num P = PrecisionNum.valueOf(test(seriesP).toString(), 256);
        Num standard = PrecisionNum.valueOf(test(seriesPH).toString(), 256);
        System.out.println(seriesD.getName() + " error: " + D.minus(standard).dividedBy(standard).multipliedBy(PrecisionNum.valueOf(100)));
        System.out.println(seriesP.getName() + " error: " + P.minus(standard).dividedBy(standard).multipliedBy(PrecisionNum.valueOf(100)));
    }

    public static Num test(TimeSeries series) {
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePriceIndicator,100);
        MACDIndicator macdIndicator = new MACDIndicator(rsi);
        EMAIndicator ema = new EMAIndicator(rsi,12);
        EMAIndicator emaLong = new EMAIndicator(rsi,26);
        DifferenceIndicator macdIndicator2 = new DifferenceIndicator(ema,emaLong);

        Rule entry = new IsEqualRule(macdIndicator,macdIndicator2);
        Rule exit = new UnderIndicatorRule(new LowPriceIndicator(series), new HighPriceIndicator(series));
        Strategy strategy1 = new BaseStrategy(entry, exit); // enter/exit every tick

        long start = System.currentTimeMillis();
        TimeSeriesManager manager = new TimeSeriesManager(series);
        TradingRecord record1 = manager.run(strategy1);
        TotalProfitCriterion profit1 = new TotalProfitCriterion();
        Num profitResult1 = profit1.calculate(series, record1);
        long end = System.currentTimeMillis();

        System.out.printf("[%s]\n" +
                "    -Time:   %s ms.\n" +
                "    -Profit: %s \n" +
                "    -Bars:   %s\n \n",series.getName(),(end-start),profitResult1, series.getBarCount());
        return profitResult1;
    }
}
