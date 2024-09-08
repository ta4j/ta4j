/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.rules;

import org.ta4j.core.Rule;

/**
 * An AND combination of two {@link Rule rules}.
 *
 * <p>
 * Satisfied when both rules are satisfied.
 *
 * <p>
 * <b>Warning:</b> The second rule is not tested if the first rule is not
 * satisfied.
 */
public class AndRule extends AbstractRule {

  private final Rule rule1;
  private final Rule rule2;


  /**
   * Constructor.
   *
   * @param rule1 a trading rule
   * @param rule2 another trading rule
   */
  public AndRule(final Rule rule1, final Rule rule2) {
    this.rule1 = rule1;
    this.rule2 = rule2;
  }


  @Override
  public boolean isSatisfied() {
    final boolean satisfied = this.rule1.isSatisfied() && this.rule2.isSatisfied();
    traceIsSatisfied(satisfied);
    return satisfied;
  }


  /** @return the first rule */
  public Rule getRule1() {
    return this.rule1;
  }


  /** @return the second rule */
  public Rule getRule2() {
    return this.rule2;
  }


  @Override
  public String toString() {
    return String.format("UnderRule[%s, %s] => %s", this.rule1, this.rule2, isSatisfied());
  }
}
