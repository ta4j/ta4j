/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core.rules.helper;

import java.io.Serializable;
import java.util.Objects;

import org.ta4j.core.Rule;

/**
 * A ChainLink is part of a {@link org.ta4j.core.rules.ChainRule}. Every
 * Chainlink has a {@link Rule} and a threshold. ChainLinks are evaluated in the
 * trade they are added to the ChainRule and the rule has to be satisfied within
 * the threshold number of bars.
 */
public class ChainLink implements Serializable {

    private static final long serialVersionUID = -436033401669929601L;

    private Rule rule;
    private int threshold = 0;

    /**
     * Threshold is the number of bars the provided rule has to be satisfied after
     * the preceding rule
     *
     * @param rule      A {@link Rule} that has to be satisfied within the threshold
     * @param threshold Number of bars the rule has to be satisfied in. The current
     *                  index is included.
     */
    public ChainLink(Rule rule, int threshold) {
        this.rule = rule;
        this.threshold = threshold;
    }

    public Rule getRule() {
        return rule;
    }

    public void setRule(Rule rule) {
        this.rule = rule;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ChainLink))
            return false;
        ChainLink chainLink = (ChainLink) o;
        return getThreshold() == chainLink.getThreshold() && Objects.equals(getRule(), chainLink.getRule());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRule(), getThreshold());
    }

    @Override
    public String toString() {
        return "ChainLink{" + "rule=" + rule + ", threshold=" + threshold + '}';
    }
}
