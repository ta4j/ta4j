/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Bastian Engelmann
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
package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * This class implenets a basic trailing stop loss indicator.
 * 
 * Basic idea: 
 * Your stop order limit is automatically adjusted while price is rising. 
 * On falling prices the initial StopLossDistance is reduced. 
 * Sell signal: When StopLossDistance becomes '0'
 * 
 * Usage:
 *  
 * // Buying rule
 * Rule buyingRule = new BooleanRule(true); // No real buying rule
 *
 * // Selling rule
 * Rule sellingRule = new CrossedDownIndicatorRule(ClosePrice_Indicator,TrailingStopLoss_Indicator) .and(new JustOnceRule());
 *
 * // Strategy
 * Strategy strategy = new Strategy(buyingRule, sellingRule);
 * 
 * Hints:
 * There are two constructors for two use cases: 
 * Constructor 1: No InitialStopLimit is needed. It is taken from the first inicator value
 * Constructor 2: You can set an InitialStopLimit 
 * It may influence the trade signals of the strategy depending which constructor you choose.  
 * 
 * @author Bastian Engelmann
 */
public class TrailingStopLossIndicator extends CachedIndicator<Decimal> {
    
    private final Indicator<Decimal> indicator;

    private Decimal StopLossLimit;
    private final Decimal StopLossDistance;
    

    public TrailingStopLossIndicator(Indicator<Decimal> indicator,Decimal StopLossDistance) {
        super(indicator);
        this.indicator = indicator;
        this.StopLossLimit = indicator.getValue(0).minus(StopLossDistance);
        this.StopLossDistance = StopLossDistance;
    }
    
    public TrailingStopLossIndicator(Indicator<Decimal> indicator,Decimal StopLossDistance,Decimal InitialStopLossLimit) {
        super(indicator);
        this.indicator = indicator;
        this.StopLossLimit = InitialStopLossLimit;
        this.StopLossDistance = StopLossDistance;
    }
    

    /**
     * Simple implementation of the trailing stop loss concept.
     * Logic:
     * IF CurrentPrice - StopLossDistance > StopLossLimit THEN StopLossLimit = CurrentPrice - StopLossDistance
     * @param index
     * @return Decimal
     */
    @Override
    protected Decimal calculate(int index) {
        Decimal CurrentPrice = indicator.getValue(index);
             
        Decimal ComparisonPrice = StopLossLimit.plus(StopLossDistance);
        int Comparator = CurrentPrice.compareTo(ComparisonPrice);
        
        if (Comparator > 0)
        {
            StopLossLimit = CurrentPrice.minus(StopLossDistance);

        }

        return StopLossLimit;
        
    }
    
}
