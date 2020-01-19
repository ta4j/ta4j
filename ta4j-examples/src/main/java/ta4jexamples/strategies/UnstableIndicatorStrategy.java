package ta4jexamples.strategies;

import org.ta4j.core.*;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.UnstableIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnstableIndicatorStrategy {

    public static final Duration MINUTE = Duration.ofMinutes(1);
    public static final ZonedDateTime TIME = ZonedDateTime.of(2020, 1, 1,
            0, 0, 0, 0, ZoneId.systemDefault());

    public static Strategy buildStrategy(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int smaPeriod = 3;
        Indicator<Num> sma = new UnstableIndicator(new SMAIndicator(close, smaPeriod), smaPeriod - 1);

        Rule entryRule = new CrossedUpIndicatorRule(close, sma);
        Rule exitRule = new CrossedDownIndicatorRule(close, sma);

        BaseStrategy strategy = new BaseStrategy(entryRule, exitRule);
        strategy.setUnstablePeriod(3);
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
        BarSeries series = new BaseBarSeriesBuilder().withBars(closePrices
                .map(close -> new BaseBar(MINUTE, TIME, 0, 0, 0, close, 0))
                .collect(Collectors.toList())
        ).build();

        // Building the trading strategy
        Strategy strategy = buildStrategy(series);

        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);

        System.out.println(name + " " + tradingRecord.getTrades());
    }

}
