/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package org.ta4j.core.indicators.adx;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.MMAIndicator;
import org.ta4j.core.num.Num;

/**
 * ADX indicator. Part of the Directional Movement System.
 *
 * @see <a
 *      href="https://www.investopedia.com/terms/a/adx.asp>https://www.investopedia.com/terms/a/adx.asp</a>
 */
public class ADXIndicator extends CachedIndicator<Num> {

	private final DXIndicator dx;
    private final MMAIndicator averageDXIndicator;
    private final int diBarCount;
    private final int adxBarCount;

    public ADXIndicator(BarSeries series, int diBarCount, int adxBarCount) {
    	this(new ATRIndicator(series, diBarCount), adxBarCount);
    } 

    public ADXIndicator(BarSeries series, int barCount) {
        this(series, barCount, barCount);
    }
    
    /*
     * This constructor can be used in the unlikely event the user wants to share an ATR
     * between ADX and another subsystem (Keltner channels, for example).
     * 
     * Users will normally use one of the bar series constructors,
     * which will create all the necessary components.
     * 
     * Use getPlusDIIndicator and getMinusDIIndicator if you need access to those indicators.
     * 
     */
    public ADXIndicator(ATRIndicator atr, int adxBarCount) {
    	super(atr.getBarSeries());
    	this.diBarCount = atr.getBarCount();
        this.adxBarCount = adxBarCount;
        this.dx = new DXIndicator(atr);
        this.averageDXIndicator = new MMAIndicator(dx, adxBarCount);    	
    }

    /*
     * This is obviously pure delegation.  
     * ADX is caching the exact same values needlessly, I believe.
     * ATR and 5-6 others do this too.
     */
    @Override
    protected Num calculate(int index) {
        return averageDXIndicator.getValue(index);
    }
    
    /**
     * Provide access to PlusDI so it does not need to be recreated for an ADX strategy.
     * 
     * @return The PlusDIIndicator
     */
    public PlusDIIndicator getPlusDIIndicator() {
    	return dx.getPlusDIIndicator();
    }
    
    /**
     * Provide access to MinusDI so it does not need to be recreated for an ADX strategy.
     * 
     * @return The MinusDIIndicator
     */
    public MinusDIIndicator getMinusDIIndicator() {
    	return dx.getMinusDIIndicator();
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + " diBarCount: " + diBarCount + " adxBarCount: " + adxBarCount;
    }
}
