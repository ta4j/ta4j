/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.num;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.IsEqualRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

public class CompareNumTypes {

    private static final Logger LOG = LogManager.getLogger(CompareNumTypes.class);
    private static final int NUMBARS = 10000;

    public static void main(String[] args) {
        BaseBarSeriesBuilder barSeriesBuilder = new BaseBarSeriesBuilder();
        BarSeries seriesD = barSeriesBuilder.withName("Sample Series Double    ")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        BarSeries seriesP = barSeriesBuilder.withName("Sample Series DecimalNum 32")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();
        BarSeries seriesPH = barSeriesBuilder.withName("Sample Series DecimalNum 256")
                .withNumFactory(DecimalNumFactory.getInstance(256))
                .build();

        var now = Instant.now();
        int[] randoms = new Random().ints(NUMBARS, 80, 100).toArray();
        for (int i = 0; i < randoms.length; i++) {
            Instant date = now.minusSeconds(NUMBARS - i);
            seriesD.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(date)
                    .openPrice(randoms[i])
                    .closePrice(randoms[i] + 21)
                    .highPrice(randoms[i] - 21)
                    .lowPrice(randoms[i] - 5)
                    .add();
            seriesP.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(date)
                    .openPrice(randoms[i])
                    .closePrice(randoms[i] + 21)
                    .highPrice(randoms[i] - 21)
                    .lowPrice(randoms[i] - 5)
                    .add();
            seriesPH.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(date)
                    .openPrice(randoms[i])
                    .closePrice(randoms[i] + 21)
                    .highPrice(randoms[i] - 21)
                    .lowPrice(randoms[i] - 5)
                    .add();
        }
        Num D = DecimalNum.valueOf(test(seriesD).toString(), new MathContext(256));
        Num P = DecimalNum.valueOf(test(seriesP).toString(), new MathContext(256));
        Num standard = DecimalNum.valueOf(test(seriesPH).toString(), new MathContext(256));
        LOG.debug("{} error: {}", seriesD.getName(),
                D.minus(standard).dividedBy(standard).multipliedBy(DecimalNum.valueOf(100)));
        LOG.debug("{} error: {}", seriesP.getName(),
                P.minus(standard).dividedBy(standard).multipliedBy(DecimalNum.valueOf(100)));
    }

    public static Num test(BarSeries series) {
        final var closePriceIndicator = new ClosePriceIndicator(series);
        final var rsi = new RSIIndicator(closePriceIndicator, 100);
        final var macdIndicator = new MACDIndicator(rsi);
        final var ema = new EMAIndicator(rsi, 12);
        final var emaLong = new EMAIndicator(rsi, 26);
        final var macdIndicator2 = BinaryOperationIndicator.difference(ema, emaLong);

        final var entry = new IsEqualRule(macdIndicator, macdIndicator2);
        final var exit = new UnderIndicatorRule(new LowPriceIndicator(series), new HighPriceIndicator(series));
        final var strategy1 = new BaseStrategy(entry, exit); // enter/exit every tick

        final var start = System.currentTimeMillis();
        final var manager = new BarSeriesManager(series);
        final var record1 = manager.run(strategy1);
        final var totalReturn1 = new GrossReturnCriterion();
        final var returnResult1 = totalReturn1.calculate(series, record1);
        final var end = System.currentTimeMillis();

        LOG.debug("""
                [{}]
                    -Time:   {} ms.
                    -Profit: {}\s
                    -Bars:   {}
                \s
                """, series.getName(), (end - start), returnResult1, series.getBarCount());
        return returnResult1;
    }
}
