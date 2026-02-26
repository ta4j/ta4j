/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.List;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.Returns;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.BarSeriesUtils;

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
 * <li>{@link Returns} with {@link ReturnRepresentation#LOG} to aggregate total
 * return from per-bar log returns.</li>
 * <li>{@link MaximumDrawdownCriterion} for denominator calculation.</li>
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
 * @since 0.22.2
 */
public class CalmarRatioCriterion extends AbstractEquityCurveSettingsCriterion {

    private final MaximumDrawdownCriterion maximumDrawdownCriterion;

    /**
     * Constructor using {@link EquityCurveMode#MARK_TO_MARKET} by default.
     *
     * @since 0.22.2
     */
    public CalmarRatioCriterion() {
        this(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor using a specific equity curve calculation mode.
     *
     * @param equityCurveMode the equity curve mode to use
     * @since 0.22.2
     */
    public CalmarRatioCriterion(EquityCurveMode equityCurveMode) {
        this(equityCurveMode, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor using the provided open position handling.
     *
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CalmarRatioCriterion(OpenPositionHandling openPositionHandling) {
        this(EquityCurveMode.MARK_TO_MARKET, openPositionHandling);
    }

    /**
     * Constructor using specific equity curve and open position handling.
     *
     * @param equityCurveMode      the equity curve mode to use
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CalmarRatioCriterion(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        super(equityCurveMode, openPositionHandling);
        this.maximumDrawdownCriterion = new MaximumDrawdownCriterion(equityCurveMode, openPositionHandling);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        NumFactory numFactory = series.numFactory();
        if (position == null || position.getEntry() == null) {
            return numFactory.zero();
        }
        return calculate(series, new BaseTradingRecord(position));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory numFactory = series.numFactory();
        Num zero = numFactory.zero();
        if (tradingRecord == null || series.isEmpty()) {
            return zero;
        }

        int beginIndex = tradingRecord.getStartIndex(series);
        int endIndex = tradingRecord.getEndIndex(series);
        if (endIndex <= beginIndex) {
            return zero;
        }

        Returns returns = new Returns(series, tradingRecord, ReturnRepresentation.LOG, equityCurveMode,
                openPositionHandling);
        Num annualizedReturn = annualizedReturn(series, returns.getRawValues(), beginIndex, endIndex);

        Num maximumDrawdown = maximumDrawdownCriterion.calculate(series, tradingRecord);
        if (maximumDrawdown.isZero()) {
            return annualizedReturn;
        }
        return annualizedReturn.dividedBy(maximumDrawdown);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    private Num annualizedReturn(BarSeries series, List<Num> logReturns, int beginIndex, int endIndex) {
        NumFactory numFactory = series.numFactory();
        Num zero = numFactory.zero();
        Num years = BarSeriesUtils.deltaYears(series, beginIndex, endIndex);
        if (years.isZero()) {
            return zero;
        }

        Num totalLogReturn = zero;
        for (int i = beginIndex + 1; i <= endIndex; i++) {
            Num logReturn = logReturns.get(i);
            if (!logReturn.isNaN()) {
                totalLogReturn = totalLogReturn.plus(logReturn);
            }
        }

        return totalLogReturn.dividedBy(years).exp().minus(numFactory.one());
    }
}
