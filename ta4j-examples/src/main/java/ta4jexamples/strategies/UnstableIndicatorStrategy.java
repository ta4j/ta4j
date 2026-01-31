/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.UnstableIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

public class UnstableIndicatorStrategy {

    private static final Logger LOG = LogManager.getLogger(UnstableIndicatorStrategy.class);

    public static final Duration MINUTE = Duration.ofMinutes(1);

    public static final Instant TIME = Instant.parse("2020-01-01T00:00:00Z");

    public static Strategy buildStrategy(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int smaPeriod = 3;
        Indicator<Num> sma = new UnstableIndicator(new SMAIndicator(close, smaPeriod), smaPeriod - 1);

        Rule entryRule = new CrossedUpIndicatorRule(close, sma);
        Rule exitRule = new CrossedDownIndicatorRule(close, sma);

        BaseStrategy strategy = new BaseStrategy(entryRule, exitRule);
        strategy.setUnstableBars(3);
        return strategy;
    }

    public static void main(String[] args) {
        inappropriateTrade();
        appropriateTrade();
    }

    public static void inappropriateTrade() {
        // Should not trade
        test("Inappropriate trade", Stream.of(10d, 2d, 6d, 16d, 8d));
    }

    public static void appropriateTrade() {
        // Should trade
        test("Appropriate trade", Stream.of(10d, 8d, 6d, 16d, 8d));
    }

    public static void test(String name, Stream<Double> closePrices) {
        // Getting the bar series
        BarSeries series = new BaseBarSeriesBuilder().build();

        Instant[] currentTime = { TIME };
        closePrices.forEach(close -> {
            series.barBuilder()
                    .timePeriod(MINUTE)
                    .endTime(currentTime[0])
                    .openPrice(0)
                    .closePrice(close)
                    .highPrice(0)
                    .lowPrice(0)
                    .add();
            currentTime[0] = currentTime[0].plus(MINUTE);
        });

        // Building the trading strategy
        Strategy strategy = buildStrategy(series);

        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);

        LOG.debug("{} {}", name, tradingRecord.getPositions());
    }

}
