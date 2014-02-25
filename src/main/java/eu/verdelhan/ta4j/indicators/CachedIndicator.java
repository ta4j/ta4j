/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
package eu.verdelhan.ta4j.indicators;

import eu.verdelhan.ta4j.Indicator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cached {@link Indicator indicators}.
 * <p>
 * Caches the constructor of the indicator. Avoid to calculate the same index of the indicator twice.
 */
public abstract class CachedIndicator<T> implements Indicator<T> {

    private List<T> results = new ArrayList<T>();

    @Override
    public T getValue(int index) {
        increaseLength(index);
        if (results.get(index) == null) {
            int i = index;
            while ((i > 0) && (results.get(i--) == null)) {
                ;
            }
            for (; i <= index; i++) {
                if (results.get(i) == null) {
                    results.set(i, calculate(i));
                }
            }
        }
        return results.get(index);
    }

    protected abstract T calculate(int index);

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

	/**
	 * Increases the size of cached results buffer.
	 * @param index
	 */
    private void increaseLength(int index) {
        if (results.size() <= index) {
            results.addAll(Collections.<T> nCopies((index - results.size()) + 1, null));
        }
    }
}
