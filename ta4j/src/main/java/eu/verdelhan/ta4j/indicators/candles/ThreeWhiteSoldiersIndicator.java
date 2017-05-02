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
package eu.verdelhan.ta4j.indicators.candles;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * Three white soldiers indicator.
 * <p>
 * @see http://www.investopedia.com/terms/t/three_white_soldiers.asp
 */
public class ThreeWhiteSoldiersIndicator extends CachedIndicator<Boolean> {

    private final TimeSeries series;
    
    /** Upper shadow */
    private final UpperShadowIndicator upperShadowInd;
    /** Average upper shadow */
    private final SMAIndicator averageUpperShadowInd;
    /** Factor used when checking if a candle has a very short upper shadow */
    private final Decimal factor;
    
    private int blackCandleIndex = -1;
    
    /**
     * Constructor.
     * @param series a time series
     * @param timeFrame the number of ticks used to calculate the average upper shadow
     * @param factor the factor used when checking if a candle has a very short upper shadow
     */
    public ThreeWhiteSoldiersIndicator(TimeSeries series, int timeFrame, Decimal factor) {
        super(series);
        this.series = series;
        upperShadowInd = new UpperShadowIndicator(series);
        averageUpperShadowInd = new SMAIndicator(upperShadowInd, timeFrame);
        this.factor = factor;
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 3) {
            // We need 4 candles: 1 black, 3 white
            return false;
        }
        blackCandleIndex = index - 3;
        return series.getTick(blackCandleIndex).isBearish()
                && isWhiteSoldier(index - 2)
                && isWhiteSoldier(index - 1)
                && isWhiteSoldier(index);
    }
    
    /**
     * @param index the tick/candle index
     * @return true if the tick/candle has a very short upper shadow, false otherwise
     */
    private boolean hasVeryShortUpperShadow(int index) {
        Decimal currentUpperShadow = upperShadowInd.getValue(index);
        // We use the black candle index to remove to bias of the previous soldiers
        Decimal averageUpperShadow = averageUpperShadowInd.getValue(blackCandleIndex);
        
        return currentUpperShadow.isLessThan(averageUpperShadow.multipliedBy(factor));
    }
    
    /**
     * @param index the current tick/candle index
     * @return true if the current tick/candle is growing, false otherwise
     */
    private boolean isGrowing(int index) {
        Tick prevTick = series.getTick(index-1);
        Tick currTick = series.getTick(index);
        final Decimal prevOpenPrice = prevTick.getOpenPrice();
        final Decimal prevClosePrice = prevTick.getClosePrice();
        final Decimal currOpenPrice = currTick.getOpenPrice();
        final Decimal currClosePrice = currTick.getClosePrice();
        
        // Opens within the body of the previous candle
        return currOpenPrice.isGreaterThan(prevOpenPrice) && currOpenPrice.isLessThan(prevClosePrice)
                // Closes above the previous close price
                && currClosePrice.isGreaterThan(prevClosePrice);
    }
    
    /**
     * @param index the current tick/candle index
     * @return true if the current tick/candle is a white soldier, false otherwise
     */
    private boolean isWhiteSoldier(int index) {
        Tick prevTick = series.getTick(index-1);
        Tick currTick = series.getTick(index);
        if (currTick.isBullish()) {
            if (prevTick.isBearish()) {
                // First soldier case
                return hasVeryShortUpperShadow(index)
                        && currTick.getOpenPrice().isGreaterThan(prevTick.getMinPrice());
            } else {
                return hasVeryShortUpperShadow(index) && isGrowing(index);
            }
        }
        return false;
    }
}
