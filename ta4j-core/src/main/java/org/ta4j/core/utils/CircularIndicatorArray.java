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

import java.time.Instant;
import java.util.Iterator;

import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.Num;

public class CircularIndicatorArray extends CircularArray<Indicator<Num>> {

  public CircularIndicatorArray(final int capacity) {
    super(capacity, null);
  }


  @Override
  public Iterable<Indicator<Num>> reversed() {
    return () -> new Iterator<>() {
      final int currentIndex = getCurrentIndex();
      int processed = 0;


      @Override
      public boolean hasNext() {
        return this.processed < capacity()
               && getIndex() > -1
               && get(getIndex() - 1) != null;
      }


      private int getIndex() {
        return this.currentIndex - this.processed;
      }


      @Override
      public Indicator<Num> next() {
        final var num = get(getIndex());
        ++this.processed;
        return num;
      }
    };
  }


  @Override
  public Iterator<Indicator<Num>> iterator() {
    return new Iterator<>() {
      int currentIndex = getCurrentIndex();
      int processed = 0;


      @Override
      public boolean hasNext() {
        return this.processed < capacity();
      }


      @Override
      public Indicator<Num> next() {
        ++this.currentIndex;
        ++this.processed;
        return get(this.currentIndex);
      }
    };
  }


  public void refresh(final Instant tick) {
    for (final var element : this.elements) {
      if (element != null) {
        element.refresh(tick);
      }
    }
  }
}
