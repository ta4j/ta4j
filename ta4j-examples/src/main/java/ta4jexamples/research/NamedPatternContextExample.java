/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.research;

import java.time.Duration;
import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.candles.PiercingLineIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.BooleanIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

/**
 * Demonstrates composing a named pattern with explicit market context.
 *
 * <p>
 * {@link PiercingLineIndicator} reports two-candle morphology only: it does not
 * evaluate whether a downtrend preceded the pattern. This example builds a
 * prior-only trend rule from the close price and composes it with the named
 * pattern, so the combined rule describes the conventional bullish reversal
 * interpretation without hiding any trend model inside the pattern indicator.
 * </p>
 * <p>
 * Rules compare raw indicator values and do not enforce indicator unstable-bar
 * counts, so the composed rule can signal while the shifted context average is
 * still a partial window. {@link #firstReliableIndex(BarSeries)} exposes the
 * combined warm-up boundary (index 21 for this setup); {@code main} applies it
 * by never evaluating the rule below that index.
 * </p>
 * <p>
 * This class and its {@code main} method are public because ta4j-examples is a
 * runnable demo catalog: examples are executed directly as JVM entry points
 * ({@code java ta4jexamples.research.NamedPatternContextExample}), which
 * requires a public class exposing a public static {@code main}. This is an
 * intentional exception to the package-private default for library code and is
 * scoped to the examples module.
 * </p>
 *
 * @since 0.24.2
 */
public class NamedPatternContextExample {

    private static final Logger LOG = LogManager.getLogger(NamedPatternContextExample.class);
    private static final int CONTEXT_PERIOD = 20;
    private static final int PATTERN_WIDTH = 2;

    /**
     * Runs the deterministic named-pattern-versus-context example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        BarSeries series = buildSeries();
        Rule pattern = pattern(series);
        Rule priorDowntrend = priorDowntrend(series);
        Rule reversalCandidate = pattern.and(priorDowntrend);
        int firstReliableIndex = firstReliableIndex(series);
        int index = Math.max(firstReliableIndex, series.getEndIndex());

        LOG.info("firstReliableIndex={} index={} pattern={} priorDowntrend={} reversalCandidate={}", firstReliableIndex,
                index, pattern.isSatisfied(index), priorDowntrend.isSatisfied(index),
                reversalCandidate.isSatisfied(index));
    }

    static BarSeries buildSeries() {

        BarSeries series = new BaseBarSeriesBuilder().withName("Named pattern context example").build();
        Instant firstEndTime = Instant.parse("2024-01-01T00:00:00Z");
        for (int index = 0; index < CONTEXT_PERIOD; index++) {
            double openPrice = 120.0 - index;
            addBar(series, firstEndTime.plus(Duration.ofDays(index)), openPrice, openPrice - 1.0, openPrice + 0.5,
                    openPrice - 1.5);
        }
        addBar(series, firstEndTime.plus(Duration.ofDays(CONTEXT_PERIOD)), 99.0, 93.0, 99.5, 92.5);
        addBar(series, firstEndTime.plus(Duration.ofDays(CONTEXT_PERIOD + 1)), 92.0, 97.0, 97.5, 91.5);
        return series;
    }

    private static void addBar(BarSeries series, Instant endTime, double openPrice, double closePrice, double highPrice,
            double lowPrice) {
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime)
                .openPrice(openPrice)
                .highPrice(highPrice)
                .lowPrice(lowPrice)
                .closePrice(closePrice)
                .add();
    }

    static Rule pattern(BarSeries series) {
        return new BooleanIndicatorRule(new PiercingLineIndicator(series));
    }

    static Rule priorDowntrend(BarSeries series) {
        Indicator<Num> close = new ClosePriceIndicator(series);
        Indicator<Num> closeBeforePattern = new PreviousValueIndicator(close, PATTERN_WIDTH);
        Indicator<Num> averageBeforePattern = averageBeforePattern(close);
        return new UnderIndicatorRule(closeBeforePattern, averageBeforePattern);
    }

    /**
     * The first index whose context average covers the full
     * {@value #CONTEXT_PERIOD}-bar window shifted {@value #PATTERN_WIDTH} bars
     * before the pattern: the later of the pattern's and the shifted average's
     * unstable-bar counts. Signals at lower indexes read a partial context window
     * and must be skipped.
     *
     * @param series the bar series
     * @return the first index at which the composed rule is meaningful
     */
    static int firstReliableIndex(BarSeries series) {
        Indicator<Num> close = new ClosePriceIndicator(series);
        return Math.max(new PiercingLineIndicator(series).getCountOfUnstableBars(),
                averageBeforePattern(close).getCountOfUnstableBars());
    }

    private static Indicator<Num> averageBeforePattern(Indicator<Num> close) {
        return new PreviousValueIndicator(new SMAIndicator(close, CONTEXT_PERIOD), PATTERN_WIDTH);
    }
}
