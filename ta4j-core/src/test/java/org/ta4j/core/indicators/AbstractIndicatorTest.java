/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.indicators;

import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Abstract test class to extend BarSeries, Indicator an other test cases. The
 * extending class will be called twice. First time with
 * {@link DecimalNum#valueOf}, second time with {@link DoubleNum#valueOf} as
 * <code>Function<Number, Num></></code> parameter. This should ensure that the
 * defined test case is valid for both data types.
 *
 * @param <I> The generic class of the test indicator (could be
 *     <code>Num</code>, <code>Boolean</code>, ...)
 */
@RunWith(Parameterized.class)
public abstract class AbstractIndicatorTest<I> {

  public final NumFactory numFactory;


  @Parameterized.Parameters(name = "Test Case: {index} (0=DoubleNum, 1=DecimalNum)")
  public static List<NumFactory> function() {
    return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
  }


  /**
   * Constructor
   *
   * @param numFactory the function to convert a Number into a Num implementation
   *     (automatically inserted by Junit)
   */
  public AbstractIndicatorTest(final NumFactory numFactory) {
    this.numFactory = numFactory;
  }


  protected Num numOf(final Number n) {
    return this.numFactory.numOf(n);
  }
}
