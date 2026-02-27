/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.num.Num;

/**
 * An analysis criterion. It can be used to:
 *
 * <ul>
 * <li>analyze the performance of a {@link Strategy strategy}
 * <li>compare several {@link Strategy strategies} together
 * </ul>
 */
public interface AnalysisCriterion {

    /** Filter to differentiate between winning or losing positions. */
    enum PositionFilter {
        /** Consider only winning positions. */
        PROFIT,
        /** Consider only losing positions. */
        LOSS;
    }

    /**
     * @param series   the bar series, not null
     * @param position the position, not null
     * @return the criterion value for the position
     */
    Num calculate(BarSeries series, Position position);

    /**
     * @param series        the bar series, not null
     * @param tradingRecord the trading record, not null
     * @return the criterion value for the positions
     */
    Num calculate(BarSeries series, TradingRecord tradingRecord);

    /**
     * Calculates this criterion over a specific analysis window using
     * {@link AnalysisContext#defaults() default context options}.
     *
     * <p>
     * Examples:
     * </p>
     * <ul>
     * <li>Past 7 days:
     * {@code criterion.calculate(series, record, AnalysisWindow.lookbackDuration(Duration.ofDays(7)))}</li>
     * <li>Past 30 days:
     * {@code criterion.calculate(series, record, AnalysisWindow.lookbackDuration(Duration.ofDays(30)))}</li>
     * <li>Explicit date range:
     * {@code criterion.calculate(series, record, AnalysisWindow.timeRange(Instant.parse("2026-02-10T00:00:00Z"), Instant.parse("2026-02-14T00:00:00Z")))}</li>
     * </ul>
     *
     * @param series        the bar series, not null
     * @param tradingRecord the trading record, not null
     * @param window        the requested analysis window, not null
     * @return the criterion value for the window
     * @since 0.22.3
     */
    default Num calculate(BarSeries series, TradingRecord tradingRecord, AnalysisWindow window) {
        return calculate(series, tradingRecord, window, AnalysisContext.defaults());
    }

    /**
     * Calculates this criterion over a specific analysis window.
     *
     * <p>
     * Window boundaries follow:
     * </p>
     * <ul>
     * <li>bar indices: start inclusive, end inclusive</li>
     * <li>time windows: start inclusive, end exclusive (bar membership is based on
     * bar end time)</li>
     * </ul>
     *
     * <p>
     * On constrained or moving series (for example when
     * {@link BarSeries#setMaximumBarCount(int)} removed historical bars), missing
     * history is handled according to
     * {@link AnalysisContext#missingHistoryPolicy()}:
     * </p>
     * <ul>
     * <li>{@link AnalysisContext.MissingHistoryPolicy#STRICT}: fails when requested
     * history is unavailable</li>
     * <li>{@link AnalysisContext.MissingHistoryPolicy#CLAMP}: intersects requested
     * range with available logical indices</li>
     * </ul>
     *
     * @param series        the bar series, not null
     * @param tradingRecord the trading record, not null
     * @param window        the requested analysis window, not null
     * @param context       window resolution and projection options, not null
     * @return the criterion value for the window
     * @since 0.22.3
     */
    default Num calculate(BarSeries series, TradingRecord tradingRecord, AnalysisWindow window,
            AnalysisContext context) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(tradingRecord, "tradingRecord");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(context, "context");

        if (series.isEmpty()) {
            return calculate(series, tradingRecord);
        }

        AnalysisWindowing.ResolvedWindow resolved = AnalysisWindowing.resolve(series, window, context);
        TradingRecord projectedRecord = AnalysisWindowing.projectTradingRecord(series, tradingRecord, resolved,
                context);
        return calculate(series, projectedRecord);
    }

    /**
     * @param manager    the bar series manager with entry type of BUY
     * @param strategies a list of strategies
     * @return the best strategy (among the provided ones) according to the
     *         criterion
     */
    default Strategy chooseBest(BarSeriesManager manager, List<Strategy> strategies) {
        return chooseBest(manager, TradeType.BUY, strategies);
    }

    /**
     * @param manager    the bar series manager
     * @param tradeType  the entry type (BUY or SELL) of the first trade in the
     *                   trading session
     * @param strategies a list of strategies
     * @return the best strategy (among the provided ones) according to the
     *         criterion
     */
    default Strategy chooseBest(BarSeriesManager manager, TradeType tradeType, List<Strategy> strategies) {
        Strategy bestStrategy = strategies.getFirst();
        Num bestCriterionValue = calculate(manager.getBarSeries(), manager.run(bestStrategy));

        for (int i = 1; i < strategies.size(); i++) {
            Strategy currentStrategy = strategies.get(i);
            Num currentCriterionValue = calculate(manager.getBarSeries(), manager.run(currentStrategy, tradeType));

            if (betterThan(currentCriterionValue, bestCriterionValue)) {
                bestStrategy = currentStrategy;
                bestCriterionValue = currentCriterionValue;
            }
        }

        return bestStrategy;
    }

    /**
     * @param criterionValue1 the first value
     * @param criterionValue2 the second value
     * @return true if the first value is better than (according to the criterion)
     *         the second one, false otherwise
     */
    boolean betterThan(Num criterionValue1, Num criterionValue2);
}
