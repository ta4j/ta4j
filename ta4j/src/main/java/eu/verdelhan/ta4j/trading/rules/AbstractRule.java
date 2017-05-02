/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.trading.rules;

import eu.verdelhan.ta4j.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract trading {@link Rule rule}.
 */
public abstract class AbstractRule implements Rule {

    /** The logger */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public Rule and(Rule rule) {
        return new AndRule(this, rule);
    }

    @Override
    public Rule or(Rule rule) {
        return new OrRule(this, rule);
    }

    @Override
    public Rule xor(Rule rule) {
        return new XorRule(this, rule);
    }

    @Override
    public Rule negation() {
        return new NotRule(this);
    }

    @Override
    public boolean isSatisfied(int index) {
        return isSatisfied(index, null);
    }
    
    /**
     * Traces the isSatisfied() method calls.
     * @param index the tick index
     * @param isSatisfied true if the rule is satisfied, false otherwise
     */
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        log.trace("{}#isSatisfied({}): {}", getClass().getSimpleName(), index, isSatisfied);
    }
}
