/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseOrderBook;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.OrderBook;
import org.ta4j.core.OrderBookLevel;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.orderbook.OrderBookImbalanceIndicator;
import org.ta4j.core.indicators.orderbook.OrderBookIndicator;
import org.ta4j.core.indicators.orderbook.OrderBookSpreadIndicator;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.rules.orderbook.OrderBookReadyRule;

/**
 * Offline bar-close signals from causally available L2 snapshots and a price
 * filter. Fills use the next bar's open with zero transaction/holding costs,
 * not the captured best quote. No book walking, queueing, partial fills or
 * latency is simulated. Venue-specific feeds must first normalize prices,
 * quantity units, availability timestamps and synchronization state into
 * immutable books.
 *
 * @since 0.28.1
 */
public final class OrderBookBacktest {
    private static final Logger LOG = LogManager.getLogger(OrderBookBacktest.class);

    private OrderBookBacktest() {
    }

    /**
     * Runs a deterministic example without exchange credentials or network access.
     *
     * @param args unused
     * @since 0.28.1
     */
    public static void main(String[] args) {
        BarSeries series = new BaseBarSeriesBuilder().withName("synthetic L2 market").build();
        NumFactory factory = series.numFactory();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<OrderBook> history = new ArrayList<>();
        int[] closes = { 100, 102, 104, 103, 99, 98 };
        for (int index = 0; index < closes.length; index++) {
            Instant closeTime = start.plusSeconds((index + 1) * 60L);
            int price = closes[index];
            series.barBuilder()
                    .timePeriod(Duration.ofMinutes(1))
                    .endTime(closeTime)
                    .openPrice(price - 1)
                    .highPrice(price + 1)
                    .lowPrice(price - 2)
                    .closePrice(price)
                    .volume(100)
                    .add();
            // Arrival precedes the decision. Exchange time alone never grants eligibility.
            history.add(new BaseOrderBook(factory,
                    new OrderBook.Metadata("synthetic", "ASSET/QUOTE", "base units", closeTime.minusSeconds(2),
                            closeTime.minusSeconds(1), null,
                            index == 3 ? OrderBook.State.UNSYNCHRONIZED : OrderBook.State.SYNCHRONIZED, true, true),
                    List.of(new OrderBookLevel(factory.numOf(price - 1), factory.numOf(8)),
                            new OrderBookLevel(factory.numOf(price - 2), factory.numOf(4))),
                    List.of(new OrderBookLevel(factory.numOf(price + 1), factory.two()),
                            new OrderBookLevel(factory.numOf(price + 2), factory.two()))));
        }
        OrderBookIndicator books = new OrderBookIndicator(series, history, Duration.ofSeconds(5),
                OrderBookIndicator.BarTime.END);
        OrderBookSpreadIndicator spread = new OrderBookSpreadIndicator(books);
        OrderBookImbalanceIndicator imbalance = new OrderBookImbalanceIndicator(books, 2);
        Rule ready = new OrderBookReadyRule(books, 2);
        Rule priceFilter = new OverIndicatorRule(new ClosePriceIndicator(series), factory.numOf(101));
        Rule entry = ready.and(new UnderIndicatorRule(spread, factory.numOf(3)))
                .and(new OverIndicatorRule(imbalance, factory.numOf("0.25")))
                .and(priceFilter);
        // Keep readiness outside negation: unavailable comparisons are ordinary false
        // leaves.
        Rule exit = new UnderIndicatorRule(new ClosePriceIndicator(series), factory.numOf(101));
        Strategy strategy = new BaseStrategy(entry, exit);
        TradingRecord record = new BarSeriesManager(series, new TradeOnNextOpenModel()).run(strategy);
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            LOG.info("bar={} ready={} spread={} imbalance={} entry={}", index, ready.isSatisfied(index),
                    spread.getValue(index), imbalance.getValue(index), entry.isSatisfied(index));
        }
        LOG.info("Next-open, zero-cost bar simulation: {}", record);
        // Standalone inspection needs no bars; this immutable capture is not a live
        // adapter.
        OrderBook captured = history.getFirst();
        LOG.info("Captured quote midpoint={} depth-2 bid={} ask={}", captured.getMidPrice(),
                captured.getDepth(OrderBook.Side.BID, 2), captured.getDepth(OrderBook.Side.ASK, 2));
    }
}
