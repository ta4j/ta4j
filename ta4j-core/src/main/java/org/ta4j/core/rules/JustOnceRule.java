/*
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
package org.ta4j.core.rules;

import org.ta4j.core.Rule;

/**
 * A one-shot rule.
 *
 * <p>
 * Satisfied when the rule is satisfied for the first time, then never again.
 */
public class JustOnceRule extends AbstractRule {

  private final Rule rule;
  private boolean satisfied = false;


  /**
   * Satisfied when the given {@code rule} is satisfied the first time, then never
   * again.
   */
  public JustOnceRule() {
    this(BooleanRule.TRUE);
  }


  /**
   * Satisfied when the given {@code rule} is satisfied the first time, then never
   * again.
   *
   * @param rule the rule that should be satisfied only the first time
   */
  public JustOnceRule(final Rule rule) {
    this.rule = rule;
  }


  @Override
  public boolean isSatisfied() {
    if (this.satisfied) {
      return false;
    }

    this.satisfied = this.rule.isSatisfied();
    return this.satisfied;
  }
}
