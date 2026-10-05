/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.drawdown.Drawdown;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.BarSeriesUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes the Calmar ratio.
 *
 * <p>
 * <b>Definition.</b> The Calmar ratio is defined as:
 *
 * <pre>
 * Calmar = annualizedReturn / maximumDrawdown
 * </pre>
 *
 * where annualized return is calculated as CAGR over the evaluated time range.
 *
 * <p>
 * <b>Implementation details.</b> This criterion reuses existing analysis
 * utilities:
 * <ul>
 * <li>{@link CashFlow} to reuse the existing compounded equity curve and derive
 * CAGR from the equity entering the evaluated window
 * ({@link CashFlow#getBaselineValue()}) to its end equity, annualized over the
 * time between the window's first and last bar closes, or from its first begin
 * time when a futures curve includes an initial-capital return.</li>
 * <li>{@link Drawdown} for denominator calculation on that same curve.</li>
 * </ul>
 *
 * <p>
 * <b>Open positions:</b> When using {@link EquityCurveMode#MARK_TO_MARKET}, the
 * {@link OpenPositionHandling} setting controls whether open positions
 * contribute to both return and drawdown. {@link EquityCurveMode#REALIZED}
 * always ignores open positions regardless of the requested handling.
 *
 * <p>
 * If maximum drawdown is zero, this implementation returns annualized return
 * directly to avoid division by zero.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/c/calmarratio.asp">https://www.investopedia.com/terms/c/calmarratio.asp</a>
 * @since 0.22.5
 */
public class CalmarRatioCriterion extends AbstractEquityCurveSettingsCriterion {

    /**
     * Analyses attempted before giving up on a series that keeps evicting the
     * window.
     */
    private static final int MAX_ATTEMPTS = 8;

    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor using {@link EquityCurveMode#MARK_TO_MARKET} by default.
     *
     * @since 0.22.5
     */
    public CalmarRatioCriterion() {
        this(ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor with explicit ratio return representation.
     *
     * @param returnRepresentation the return representation for the final criterion
     *                             value
     * @since 0.22.5
     */
    public CalmarRatioCriterion(ReturnRepresentation returnRepresentation) {
        this(returnRepresentation, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor using a specific equity curve calculation mode.
     *
     * @param equityCurveMode the equity curve mode to use
     * @since 0.22.5
     */
    public CalmarRatioCriterion(EquityCurveMode equityCurveMode) {
        this(ReturnRepresentation.DECIMAL, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor using the provided open position handling.
     *
     * @param openPositionHandling how to handle open positions
     * @since 0.22.5
     */
    public CalmarRatioCriterion(OpenPositionHandling openPositionHandling) {
        this(ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET, openPositionHandling);
    }

    /**
     * Constructor with explicit ratio return representation.
     *
     * @param returnRepresentation the return representation for the final criterion
     *                             value
     * @param equityCurveMode      the equity curve mode to use
     * @since 0.22.5
     */
    public CalmarRatioCriterion(ReturnRepresentation returnRepresentation, EquityCurveMode equityCurveMode) {
        this(returnRepresentation, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor using specific equity curve and open position handling.
     *
     * @param equityCurveMode      the equity curve mode to use
     * @param openPositionHandling how to handle open positions
     * @since 0.22.5
     */
    public CalmarRatioCriterion(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(ReturnRepresentation.DECIMAL, equityCurveMode, openPositionHandling);
    }

    /**
     * Constructor using explicit ratio return representation and position settings.
     *
     * @param returnRepresentation the return representation for the final criterion
     *                             value
     * @param equityCurveMode      the equity curve mode to use
     * @param openPositionHandling how to handle open positions
     * @since 0.22.5
     */
    public CalmarRatioCriterion(ReturnRepresentation returnRepresentation, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        super(equityCurveMode, openPositionHandling);
        this.returnRepresentation = Objects.requireNonNull(returnRepresentation, "returnRepresentation");
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return calculate(series, position, new ClosePriceIndicator(series));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return calculate(series, tradingRecord, new ClosePriceIndicator(series));
    }

    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(returnRepresentation);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Returns the ratio, or {@code null} when a bar at either end of the cash flow
     * was evicted or given a different time after the analysis started.
     */
    private Num calculateTradingRecord(BarSeries series, TradingRecord tradingRecord, Position position,
            Indicator<Num> markPriceIndicator) {
        NumFactory numFactory = series.numFactory();
        Num zero = numFactory.zero();
        // Bar times are captured before the curve so they can be verified against
        // it afterwards: annualizing reads nothing else from the series.
        EndTimes endTimes = series.withReadLock(() -> EndTimes.capture(series));
        CashFlow cashFlow = position != null && position.getFuturesContract() != null
                ? new CashFlow(series, position, markPriceIndicator,
                        openPositionHandling == OpenPositionHandling.IGNORE ? EquityCurveMode.REALIZED
                                : equityCurveMode)
                : new CashFlow(series, tradingRecord, markPriceIndicator, tradingRecord.getEndIndex(series),
                        equityCurveMode, openPositionHandling);
        Integer explicitStartIndex = tradingRecord.getStartIndex();
        int beginIndex = explicitStartIndex == null ? cashFlow.getBeginIndex()
                : Math.max(explicitStartIndex, cashFlow.getBeginIndex());
        int endIndex = cashFlow.getEndIndex();
        if (endIndex <= beginIndex) {
            return zero;
        }

        Num annualizedReturn = annualizedReturn(series, cashFlow, endTimes, beginIndex, endIndex);
        if (annualizedReturn == null) {
            return null;
        }
        Num maximumDrawdown = Drawdown.amount(series, tradingRecord, cashFlow);
        if (maximumDrawdown.isZero()) {
            return toRepresentation(annualizedReturn);
        }
        return toRepresentation(annualizedReturn.dividedBy(maximumDrawdown));
    }

    private Num annualizedReturn(BarSeries series, CashFlow cashFlow, EndTimes endTimes, int beginIndex, int endIndex) {
        Num one = series.numFactory().one();
        // The captured times are used only if the series still holds them at both
        // ends of the curve; a bar replaced, or evicted, since they were captured
        // may disagree with the curve, so the analysis runs again.
        Num years = series
                .withReadLock(() -> endTimes.isCurrentAt(series, beginIndex) && endTimes.isCurrentAt(series, endIndex)
                        ? BarSeriesUtils.deltaYears(
                                cashFlow.hasInitialReturn() ? endTimes.begins()[beginIndex - endTimes.beginIndex()]
                                        : endTimes.at(beginIndex),
                                endTimes.at(endIndex), series.numFactory())
                        : null);
        if (years == null) {
            return null;
        }
        if (years.isZero()) {
            return series.numFactory().zero();
        }
        // The CAGR starts from the equity entering the window: a result realized on
        // its first bar (a pre-window position exiting there) belongs to the window.
        Num startValue = beginIndex == cashFlow.getBeginIndex() ? cashFlow.getBaselineValue()
                : cashFlow.getValue(beginIndex - 1);
        if (startValue.isNaN() || startValue.isZero()) {
            return NaN.NaN;
        }

        Num endValue = cashFlow.getValue(endIndex);
        if (endValue.isNaN()) {
            return NaN.NaN;
        }

        Num totalReturn = endValue.dividedBy(startValue);
        return totalReturn.pow(one.dividedBy(years)).minus(one);
    }

    private Num toRepresentation(Num value) {
        if (value.isNaN()) {
            return NaN.NaN;
        }
        return returnRepresentation.toRepresentationFromRateOfReturn(value);
    }

    /**
     * End times of the bars retained when an analysis started.
     *
     * @param beginIndex the index of the first captured time
     * @param times      the captured end times
     */
    private record EndTimes(int beginIndex, Instant[] times, Instant[] begins) {

        /** Captures the retained bars' end times; runs inside the series read scope. */
        static EndTimes capture(BarSeries series) {
            if (series.isEmpty()) {
                return new EndTimes(0, new Instant[0], new Instant[0]);
            }
            int beginIndex = series.getBeginIndex();
            Instant[] times = new Instant[series.getEndIndex() - beginIndex + 1];
            Instant[] begins = new Instant[times.length];
            for (int offset = 0; offset < times.length; offset++) {
                begins[offset] = series.getBar(beginIndex + offset).getBeginTime();
                times[offset] = series.getBar(beginIndex + offset).getEndTime();
            }
            return new EndTimes(beginIndex, times, begins);
        }

        Instant at(int index) {
            return times[index - beginIndex];
        }

        /**
         * @return whether {@code index} was captured and the series still retains it
         *         with the same end time; runs inside the series read scope
         */
        boolean isCurrentAt(BarSeries series, int index) {
            return index >= beginIndex && index - beginIndex < times.length && index >= series.getBeginIndex()
                    && index <= series.getEndIndex() && at(index).equals(series.getBar(index).getEndTime())
                    && begins[index - beginIndex].equals(series.getBar(index).getBeginTime());
        }
    }

    /**
     * Calculates the Calmar ratio for a position using an explicit mark price.
     *
     * @param series             the bar series
     * @param position           the position to evaluate
     * @param markPriceIndicator mark price indicator on the same series
     * @return the Calmar ratio
     * @since 0.25.1
     */
    public Num calculate(BarSeries series, Position position, Indicator<Num> markPriceIndicator) {
        if (position == null || position.getEntry() == null)
            return series.numFactory().zero();
        return calculateCaptured(series, new BaseTradingRecord(position), position, markPriceIndicator);
    }

    /**
     * Calculates the Calmar ratio for a trading record using an explicit mark
     * price.
     *
     * @param series             the bar series
     * @param tradingRecord      the trading record to evaluate
     * @param markPriceIndicator mark price indicator on the same series
     * @return the Calmar ratio
     * @since 0.25.1
     */
    public Num calculate(BarSeries series, TradingRecord tradingRecord, Indicator<Num> markPriceIndicator) {
        if (tradingRecord == null)
            return series.numFactory().zero();
        return calculateCaptured(series, tradingRecord, null, markPriceIndicator);
    }

    private Num calculateCaptured(BarSeries series, TradingRecord record, Position position,
            Indicator<Num> markPriceIndicator) {
        Objects.requireNonNull(markPriceIndicator, "markPriceIndicator");
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Num value = calculateTradingRecord(series, record, position, markPriceIndicator);
            if (value != null)
                return value;
        }
        throw new IllegalStateException("Bar series '" + series.getName()
                + "' evicted or changed the analysis window during each of " + MAX_ATTEMPTS + " attempts");
    }
}
