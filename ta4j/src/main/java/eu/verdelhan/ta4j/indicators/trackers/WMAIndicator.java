/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TADecimal;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * WMA indicator.
 * <p>
 */
public class WMAIndicator extends CachedIndicator<TADecimal> {

    private int timeFrame;

    private Indicator<? extends TADecimal> indicator;

    public WMAIndicator(Indicator<? extends TADecimal> indicator, int timeFrame) {
        super(indicator);
        this.indicator = indicator;
        this.timeFrame = timeFrame;
    }

    @Override
    protected TADecimal calculate(int index) {
        if (index == 0) {
            return indicator.getValue(0);
        }
        TADecimal value = TADecimal.ZERO;
        if(index - timeFrame < 0) {
            
            for(int i = index + 1; i > 0; i--) {
                value = value.plus(TADecimal.valueOf(i).multipliedBy(indicator.getValue(i-1)));
            }
            return value.dividedBy(TADecimal.valueOf(((index + 1) * (index + 2)) / 2));
        }
        
        int actualIndex = index;
        for(int i = timeFrame; i > 0; i--) {
            value = value.plus(TADecimal.valueOf(i).multipliedBy(indicator.getValue(actualIndex)));
            actualIndex--;
        }
        return value.dividedBy(TADecimal.valueOf((timeFrame * (timeFrame + 1)) / 2));
    }

    @Override
    public String toString() {
        return String.format(getClass().getSimpleName() + " timeFrame: %s", timeFrame);
    }
}
