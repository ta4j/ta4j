/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package eu.verdelhan.ta4j.indicators.statistics;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.indicators.CachedIndicator;


/**
 * Periodical Growth Rate indicator.
 * 
 * In general the 'Growth Rate' is useful for comparing the average returns of 
 * investments in stocks or funds and can be used to compare the performance 
 * e.g. comparing the historical returns of stocks with bonds.
 * 
 * This indicator has the following characteristics:
 *  - the calculation is timeframe dependendant. The timeframe corresponds to the 
 *    number of trading events in a period, e. g. the timeframe for a US trading 
 *    year for end of day ticks would be '251' trading days
 *  - the result is a step function with a constant value within a timeframe
 *  - NaN values while index is smaller than timeframe, e.g. timeframe is year, 
 *    than no values are calculated before a full year is reached
 *  - NaN values for incomplete timeframes, e.g. timeframe is a year and your 
 *    timeseries contains data for 11,3 years, than no values are calculated for 
 *    the remaining 0,3 years
 *  - the method 'getTotalReturn' calculates the total return over all returns 
 *    of the coresponding timeframes
 * 
 * 
 * Further readings:
 * Good sumary on 'Rate of Return': https://en.wikipedia.org/wiki/Rate_of_return 
 * Annual return / CAGR: http://www.investopedia.com/terms/a/annual-return.asp
 * Annualized Total Return: http://www.investopedia.com/terms/a/annualized-total-return.asp
 * Annualized Return vs. Cumulative Return: 
 * http://www.fool.com/knowledge-center/2015/11/03/annualized-return-vs-cumulative-return.aspx
 * 
 */
public class PeriodicalGrowthRateIndicator extends CachedIndicator<Decimal> {
      
    private final Indicator<Decimal> indicator;

    private final int timeFrame;
    
    /**
     * Constructor.
     * Example: use timeFrame = 251 and "end of day"-ticks for annual behaviour
     * in the US (http://tradingsim.com/blog/trading-days-in-a-year/).
     * @param indicator
     * @param timeFrame 
     */
    public PeriodicalGrowthRateIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.indicator = indicator;
        this.timeFrame = timeFrame; 
    }
    
    /**
     * Gets the TotalReturn from the calculated results of the method 'calculate'.
     * For a timeFrame = number of trading days within a year (e. g. 251 days in the US) 
     * and "end of day"-ticks you will get the 'Annualized Total Return'.
     * Only complete timeFrames are taken into the calculation.
     * @return the total return from the calculated results of the method 'calculate'
     */
    public double getTotalReturn() {

        Decimal totalProduct = Decimal.ONE;
        int completeTimeframes = (getTimeSeries().getTickCount() / timeFrame);

        for (int i = 1; i <= completeTimeframes; i++) {
            int index = i * timeFrame;
            Decimal currentReturn = getValue(index);

            // Skip NaN at the end of a series
            if (currentReturn != Decimal.NaN) {
                currentReturn = currentReturn.plus(Decimal.ONE);
                totalProduct = totalProduct.multipliedBy(currentReturn);
            }
        }

        return (Math.pow(totalProduct.toDouble(), (1.0 / completeTimeframes)));
    }
    
    @Override
    protected Decimal calculate(int index) {

        Decimal currentValue = indicator.getValue(index);

        int helpPartialTimeframe = index % timeFrame;
        double helpFullTimeframes = Math.floor((double) indicator.getTimeSeries().getTickCount() / (double) timeFrame);
        double helpIndexTimeframes = (double) index / (double) timeFrame;

        double helpPartialTimeframeHeld = (double) helpPartialTimeframe / (double) timeFrame;
        double partialTimeframeHeld = (helpPartialTimeframeHeld == 0) ? 1.0 : helpPartialTimeframeHeld;

        // Avoid calculations of returns:
        // a.) if index number is below timeframe
        // e.g. timeframe = 365, index = 5 => no calculation
        // b.) if at the end of a series incomplete timeframes would remain
        Decimal timeframedReturn = Decimal.NaN;
        if ((index >= timeFrame) /*(a)*/ && (helpIndexTimeframes < helpFullTimeframes) /*(b)*/) {
            Decimal movingValue = indicator.getValue(index - timeFrame);
            Decimal movingSimpleReturn = (currentValue.minus(movingValue)).dividedBy(movingValue);

            double timeframedReturn_double = Math.pow((1 + movingSimpleReturn.toDouble()), (1 / partialTimeframeHeld)) - 1;
            timeframedReturn = Decimal.valueOf(timeframedReturn_double);
        }

        return timeframedReturn;

    }
}

