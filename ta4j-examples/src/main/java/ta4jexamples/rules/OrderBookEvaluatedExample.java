/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseOrderBook;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.OrderBook;
import org.ta4j.core.OrderBookLevel;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.orderbook.OrderBookIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.AbstractEvaluatedRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

/**
 * Captures one immutable L2 observation for inspection and boolean coercion.
 * Separate strategy invocations perform separate evaluations. The offline
 * backtest uses zero-cost next-open bar fills, not captured best quotes: no
 * book walking, queue position, partial fills or latency is simulated.
 *
 * @since 0.28.1
 */
public final class OrderBookEvaluatedExample {
    private static final Logger LOG = LogManager.getLogger(OrderBookEvaluatedExample.class);

    private OrderBookEvaluatedExample() {
    }

    /** Immutable observations and inclusive decision thresholds, not confidence. */
    record LiquidityEvaluation(int index, Optional<OrderBook> book, boolean usable, Num spread, Num imbalance,
            Num maximumSpread, Num minimumImbalance) {
    }

    /**
     * Example-local policy: missing, stale, unsynchronized, crossed, incomplete or
     * numerically unavailable observations project to false. Custom sources must
     * enforce availability and freshness. Configuration serialization cannot bind
     * external book histories; unsupported rule copies retain the original rule.
     */
    static final class OrderBookLiquidityRule extends AbstractEvaluatedRule<LiquidityEvaluation> {
        private final Indicator<Optional<OrderBook>> source;
        private final int levels;
        private final Num maximumSpread;
        private final Num minimumImbalance;

        OrderBookLiquidityRule(Indicator<Optional<OrderBook>> source, int levels, Num maximumSpread,
                Num minimumImbalance) {
            this(levels, maximumSpread, minimumImbalance, validate(source, levels, maximumSpread, minimumImbalance));
        }

        private OrderBookLiquidityRule(int levels, Num maximumSpread, Num minimumImbalance,
                Indicator<Optional<OrderBook>> source) {
            this.source = source;
            this.levels = levels;
            this.maximumSpread = maximumSpread;
            this.minimumImbalance = minimumImbalance;
        }

        private static Indicator<Optional<OrderBook>> validate(Indicator<Optional<OrderBook>> source, int levels,
                Num maximumSpread, Num minimumImbalance) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(maximumSpread, "maximumSpread");
            Objects.requireNonNull(minimumImbalance, "minimumImbalance");
            NumFactory factory = source.getBarSeries().numFactory();
            if (levels <= 0 || !Num.isFinite(maximumSpread) || !Num.isFinite(minimumImbalance)
                    || !factory.produces(maximumSpread) || !factory.produces(minimumImbalance)
                    || maximumSpread.isNegative() || minimumImbalance.isLessThan(factory.minusOne())
                    || minimumImbalance.isGreaterThan(factory.one())) {
                throw new IllegalArgumentException(
                        "positive depth and finite compatible liquidity thresholds required");
            }
            return source;
        }

        /**
         * Captures the selected book once; the trading record is not a liquidity input.
         *
         * @since 0.28.1
         */
        @Override
        public LiquidityEvaluation evaluate(int index, TradingRecord tradingRecord) {
            BarSeries series = source.getBarSeries();
            boolean warmingUp = index >= series.getBeginIndex() && index <= series.getEndIndex()
                    && index - series.getBeginIndex() < source.getCountOfUnstableBars();
            Optional<OrderBook> observation = warmingUp ? Optional.empty()
                    : Objects.requireNonNull(source.getValue(index), "source result");
            Optional<OrderBook> captured = observation.map(BaseOrderBook::copyOf);
            Num spread = captured.isPresent() ? captured.get().getSpread() : NaN.NaN;
            Num imbalance = captured.isPresent() ? captured.get().getImbalance(levels) : NaN.NaN;
            return new LiquidityEvaluation(index, captured, Num.isFinite(spread) && Num.isFinite(imbalance), spread,
                    imbalance, maximumSpread, minimumImbalance);
        }

        /**
         * Projects only the captured result; never rereads source or trading state.
         *
         * @since 0.28.1
         */
        @Override
        public boolean toBoolean(LiquidityEvaluation result) {
            return result.usable() && result.spread().isLessThanOrEqual(result.maximumSpread())
                    && result.imbalance().isGreaterThanOrEqual(result.minimumImbalance());
        }
    }

    /**
     * Runs an offline captured-result inspection and ordinary strategy backtest.
     *
     * @param args unused
     * @since 0.28.1
     */
    public static void main(String[] args) {
        BarSeries series = new BaseBarSeriesBuilder().withName("captured L2 decisions").build();
        NumFactory factory = series.numFactory();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<OrderBook> history = new ArrayList<>();
        int[] closes = { 100, 102, 98, 97 };
        for (int index = 0; index < closes.length; index++) {
            Instant end = start.plusSeconds((index + 1) * 60L);
            int price = closes[index];
            series.barBuilder()
                    .timePeriod(Duration.ofMinutes(1))
                    .endTime(end)
                    .openPrice(price - 1)
                    .highPrice(price + 1)
                    .lowPrice(price - 2)
                    .closePrice(price)
                    .volume(100)
                    .add();
            history.add(new BaseOrderBook(factory,
                    new OrderBook.Metadata("synthetic", "ASSET/QUOTE", "base units", end.minusSeconds(2),
                            end.minusSeconds(1), null,
                            index == 2 ? OrderBook.State.UNSYNCHRONIZED : OrderBook.State.SYNCHRONIZED, true, true),
                    List.of(new OrderBookLevel(factory.numOf(price - 1), factory.numOf(8))),
                    List.of(new OrderBookLevel(factory.numOf(price + 1), factory.two()))));
        }
        OrderBookIndicator books = new OrderBookIndicator(series, history, Duration.ofSeconds(5),
                OrderBookIndicator.BarTime.END);
        OrderBookLiquidityRule liquidity = new OrderBookLiquidityRule(books, 1, factory.numOf(3),
                factory.numOf("0.25"));
        liquidity.setName("Captured spread and imbalance");
        LiquidityEvaluation captured = liquidity.evaluate(1, null);
        LOG.info("Captured bar={} usable={} spread={} imbalance={} decision={}", captured.index(), captured.usable(),
                captured.spread(), captured.imbalance(), liquidity.toBoolean(captured));
        Rule ordinary = liquidity;
        Rule price = new OverIndicatorRule(new ClosePriceIndicator(series), factory.numOf(101));
        Rule richFirst = ordinary.and(price);
        Rule legacyFirst = price.and(ordinary);
        LOG.info("Separate evaluations, rich-first={} legacy-first={}", richFirst.isSatisfied(1, null),
                legacyFirst.isSatisfied(1, null));
        Rule exit = new UnderIndicatorRule(new ClosePriceIndicator(series), factory.numOf(101));
        TradingRecord record = new BarSeriesManager(series, new TradeOnNextOpenModel())
                .run(new BaseStrategy(richFirst, exit));
        LOG.info("Gap observation usable={}", liquidity.evaluate(2).usable());
        LOG.info("Next-open, zero-cost bar simulation: {}", record);
    }
}
