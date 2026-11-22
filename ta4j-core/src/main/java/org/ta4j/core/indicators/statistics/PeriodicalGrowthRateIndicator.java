/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.num.NaN.NaN;

import java.math.MathContext;
import java.math.RoundingMode;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

/**
 * Periodical Growth Rate indicator.
 *
 * <p>
 * In general the 'Growth Rate' is useful for comparing the average returns of
 * investments in stocks or funds and can be used to compare the performance
 * e.g. comparing the historical returns of stocks with bonds.
 *
 * <p>
 * This indicator has the following characteristics: - the calculation is
 * timeframe dependendant. The timeframe corresponds to the number of trading
 * events in a period, e. g. the timeframe for a US trading year for end of day
 * bars would be '251' trading days - the result is a step function with a
 * constant value within a timeframe - NaN values while index is smaller than
 * timeframe, e.g. timeframe is year, than no values are calculated before a
 * full year is reached - NaN values for incomplete timeframes, e.g. timeframe
 * is a year and your timeseries contains data for 11,3 years, than no values
 * are calculated for the remaining 0,3 years - the method 'getTotalReturn'
 * calculates the total return over all returns of the corresponding timeframes.
 * Incomplete timeframe values are recomputed as bars are appended, including
 * when this indicator is used by another cached indicator.
 *
 * <p>
 * Non-finite source values and results that cannot be represented under the
 * configured rounding policy yield {@code NaN}. With unlimited precision that
 * permits rounding, repeating quotients use the default decimal precision.
 *
 * <p>
 * Further readings:
 *
 * <ul>
 * <li>Good summary on 'Rate of Return':
 * https://en.wikipedia.org/wiki/Rate_of_return Annual return
 * <li>CAGR: http://www.investopedia.com/terms/a/annual-return.asp
 * <li>Annualized Total Return:
 * http://www.investopedia.com/terms/a/annualized-total-return.asp
 * <li>Annualized Return vs. Cumulative Return:
 * http://www.fool.com/knowledge-center/2015/11/03/annualized-return-vs-cumulative-return.aspx
 * </ul>
 */
public class PeriodicalGrowthRateIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Num one;

    /**
     * Constructor. Example: use barCount = 251 and "end of day"-bars for annual
     * behaviour in the US (http://tradingsim.com/blog/trading-days-in-a-year/).
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @throws IllegalArgumentException if {@code barCount < 1}
     */
    public PeriodicalGrowthRateIndicator(Indicator<Num> indicator, int barCount) {
        super(validatedSource(indicator, barCount));
        this.indicator = indicator;
        this.barCount = barCount;
        this.one = getBarSeries().numFactory().one();
    }

    private static Indicator<Num> validatedSource(Indicator<Num> indicator, int barCount) {
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be >= 1");
        }
        return indicator;
    }

    /**
     * Gets the TotalReturn from the calculated results of the method 'calculate'.
     * For a barCount = number of trading days within a year (e. g. 251 days in the
     * US) and "end of day"-bars you will get the 'Annualized Total Return'. Only
     * complete barCounts are taken into the calculation.
     *
     * @return the annualized total return, or {@code NaN} if a complete period is
     *         undefined or the aggregate cannot be represented
     */
    public Num getTotalReturn() {

        Num totalProduct = one;
        int completeTimeFrames = (getBarSeries().getBarCount() / barCount);

        try {
            for (int i = 1; i < completeTimeFrames; i++) {
                Num currentReturn = getValue(i * barCount);
                if (!Num.isFinite(currentReturn)) {
                    return NaN;
                }
                totalProduct = totalProduct.multipliedBy(currentReturn.plus(one));
                if (!Num.isFinite(totalProduct)) {
                    return NaN;
                }
            }
            Num result = totalProduct.pow(divide(one, getBarSeries().numFactory().numOf(completeTimeFrames)));
            return Num.isFinite(result) ? result : NaN;
        } catch (ArithmeticException ex) {
            return NaN;
        }
    }

    @Override
    protected Num calculate(int index) {
        // Keep the source progressing even when this index has no growth value.
        Num currentValue = indicator.getValue(index);
        int completeTimeframes = indicator.getBarSeries().getBarCount() / barCount;
        // Both quantities describe discrete bars and must not be rounded as Num values.
        if (index < barCount) {
            return NaN;
        }
        if (index / barCount >= completeTimeframes) {
            // Appending bars can complete this timeframe, including for downstream caches.
            markResultTransient();
            return NaN;
        }

        Num movingValue = indicator.getValue(index - barCount);
        if (!Num.isFinite(currentValue) || !Num.isFinite(movingValue)) {
            return NaN;
        }
        try {
            Num movingSimpleReturn = divide(currentValue.minus(movingValue), movingValue);
            int partialTimeframe = index % barCount;
            Num exponent = partialTimeframe == 0 ? one
                    : divide(getBarSeries().numFactory().numOf(barCount),
                            getBarSeries().numFactory().numOf(partialTimeframe));
            Num result = one.plus(movingSimpleReturn).pow(exponent).minus(one);
            return Num.isFinite(result) ? result : NaN;
        } catch (ArithmeticException ex) {
            return NaN;
        }
    }

    private static Num divide(Num numerator, Num denominator) {
        try {
            return numerator.dividedBy(denominator);
        } catch (ArithmeticException ex) {
            if (numerator instanceof DecimalNum dividend && denominator instanceof DecimalNum divisor
                    && dividend.getMathContext().getPrecision() == 0 && divisor.getMathContext().getPrecision() == 0
                    && dividend.getMathContext().getRoundingMode() != RoundingMode.UNNECESSARY
                    && divisor.getMathContext().getRoundingMode() != RoundingMode.UNNECESSARY) {
                // Unlimited precision cannot represent repeating quotients. Like DecimalNum's
                // other transcendental operations, use the default precision when necessary.
                MathContext context = new MathContext(DecimalNum.getDefaultMathContext().getPrecision(),
                        dividend.getMathContext().getRoundingMode());
                return DecimalNum.valueOf(dividend.getDelegate(), context)
                        .dividedBy(DecimalNum.valueOf(divisor.getDelegate(), context));
            }
            throw ex;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount;
    }
}
