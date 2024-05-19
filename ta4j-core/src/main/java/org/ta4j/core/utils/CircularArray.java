/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2024 Ta4j Organization & respective
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
package org.ta4j.core.utils;

import java.util.ArrayList;

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
abstract class CircularArray<T> implements Iterable<T> {

  protected final ArrayList<T> elements;
  private final int capacity;
  private int currentIndex = -1;


  protected CircularArray(final int capacity, final T defaultValue) {
    this.capacity = capacity;
    this.elements = new ArrayList<>(this.capacity);
    for (int i = 0; i < capacity; i++) {
      this.elements.add(defaultValue);
    }
  }


  public int capacity() {
    return this.capacity;
  }


  public int size() {
    return this.elements.size();
  }


  public T get(final int index) {
    return this.elements.get(getIndex(index));
  }


  protected int getCurrentIndex() {
    return this.currentIndex;
  }


  private int getIndex(final int index) {
    return index % capacity();
  }


  public T getFirst() {
    return this.elements.get(getIndex(this.currentIndex - this.capacity));
  }


  public void addLast(final T element) {
    ++this.currentIndex;
    this.elements.set(getIndex(getCurrentIndex()), element);
  }


  @Override
  public String toString() {
    return this.elements.toString();
  }


  public abstract Iterable<T> reversed();


  public boolean isEmpty() {
    return this.currentIndex == -1;
  }
}
