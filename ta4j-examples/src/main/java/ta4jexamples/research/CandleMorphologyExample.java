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
import org.ta4j.core.indicators.candles.CandleBodyIndicator;
import org.ta4j.core.indicators.candles.CandleRangeIndicator;
import org.ta4j.core.indicators.candles.LowerShadowIndicator;
import org.ta4j.core.indicators.candles.UpperShadowIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

/**
 * Demonstrates a copyable "build your own candle morphology" rule.
 *
 * <p>
 * The synthetic series ends with a strong body and small shadows. The custom
 * morphology rule is intentionally local to this example; it demonstrates
 * direct composition with the geometry primitives rather than defining a new
 * named pattern.
 * </p>
 * <p>
 * This class and its {@code main} method are public because ta4j-examples is a
 * runnable demo catalog: examples are executed directly as JVM entry points
 * ({@code java ta4jexamples.research.CandleMorphologyExample}), which requires
 * a public class exposing a public static {@code main}. This is an intentional
 * exception to the package-private default for library code and is scoped to
 * the examples module.
 * </p>
 *
 * @since 0.24.2
 */
public class CandleMorphologyExample {

    private static final Logger LOG = LogManager.getLogger(CandleMorphologyExample.class);
    private static final int CONTEXT_PERIOD = 20;

    /**
     * Runs the deterministic candle-morphology composition example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        BarSeries series = buildSeries();
        Rule customMorphology = customMorphology(series);
        int index = series.getEndIndex();

        LOG.info("index={} customMorphology={}", index, customMorphology.isSatisfied(index));
    }

    static BarSeries buildSeries() {

        BarSeries series = new BaseBarSeriesBuilder().withName("Candle morphology example").build();
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

    static Rule customMorphology(BarSeries series) {
        Indicator<Num> body = new CandleBodyIndicator(series);
        Indicator<Num> range = new CandleRangeIndicator(series);
        Indicator<Num> upperShadow = new UpperShadowIndicator(series);
        Indicator<Num> lowerShadow = new LowerShadowIndicator(series);
        Indicator<Num> halfRange = BinaryOperationIndicator.product(range, 0.5);
        Indicator<Num> tenthRange = BinaryOperationIndicator.product(range, 0.1);
        return new OverIndicatorRule(body, halfRange).and(new UnderIndicatorRule(upperShadow, tenthRange))
                .and(new UnderIndicatorRule(lowerShadow, tenthRange));
    }
}
