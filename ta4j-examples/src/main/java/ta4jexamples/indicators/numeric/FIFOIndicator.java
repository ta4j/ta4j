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
package ta4jexamples.indicators.numeric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

public class FIFOIndicator extends NumericIndicator {

    protected final transient Logger log = LoggerFactory.getLogger(getClass());

    private final CircularArray<Num> cache;
    private int highIndex = -1;

    public FIFOIndicator(Indicator<Num> delegate) {
        super(delegate);
//		int capacity = delegate.getBarSeries().getMaximumBarCount();  or getBarCount() or...
        int capacity = 256; // small for testing; 1024 is probably big enough for most use cases
        this.cache = new CircularArray<>(capacity);
    }

    @Override
    public Num getValue(int index) {
        int lowIndex = Math.max(0, highIndex - cache.capacity() + 1);
//		if ( index < lowIndex || index > highIndex  + 1 ) {
        if (index < lowIndex) {
            log.error("FIFOIndicator::getValue(" + index + ") - out of range");
            throw new IndexOutOfBoundsException();
        }
        Num result = cache.get(index);
        if (result == null) {
            log.info("FIFOIndicator::getValue(" + index + ") - cache miss");
            result = delegate.getValue(index);
            cache.set(index, result);
            highIndex = Math.max(highIndex, index);
            log.info("FIFOIndicator::getValue(" + index + ") - cache miss - highIndex = " + highIndex);
        } else {
            log.info("FIFOIndicator::getValue(" + index + ") - cache hit");

        }
        return super.getValue(index);
    }

    @Override
    public NumericIndicator cached() {
        return this;
    }

}
