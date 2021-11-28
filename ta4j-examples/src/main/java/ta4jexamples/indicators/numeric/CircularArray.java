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

import java.util.Arrays;

/**
 * A circular array can hold up to N elements. N is known as the capacity of the
 * array. All elements are null initially.
 * 
 * A circular array has no bounds. The get() and set() methods apply a modulo
 * operation
 * <p>
 * index % capacity
 * <p>
 * This ensures all get/set operations with index >= 0 are successful.
 * <p>
 * Objects of this class can provide the basis for a FIFO (first in, first out)
 * container. The client that implements such a FIFO is responsible for making
 * sure elements are accessed in a FIFO way.
 *
 * @param <T> the type of element held in the array
 */
public class CircularArray<T> {

    private final T[] elements;

    @SuppressWarnings("unchecked")
    public CircularArray(int capacity) {
        this((T[]) new Object[capacity]);
    }

    private CircularArray(T[] elements) {
        this.elements = elements;
    }

    public int capacity() {
        return elements.length;
    }

    T get(int index) {
        return elements[index % capacity()];
    }

    void set(int index, T element) {
        elements[index % capacity()] = element;
    }

    @Override
    public String toString() {
        return Arrays.toString(elements);
    }

    public static void main(String[] args) {
        CircularArray<String> array = new CircularArray<>(5);
        array.set(0, "a");
        array.set(1, "b");
        array.set(2, "c");
        array.set(3, "d");
        array.set(4, "e");
        System.out.println(array);

        array.set(5, "f");
        System.out.println(array);

    }

}
