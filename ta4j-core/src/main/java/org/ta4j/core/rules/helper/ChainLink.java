/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.helper;

import java.io.Serializable;
import java.util.Objects;

import org.ta4j.core.Rule;

/**
 * A {@code ChainLink} is part of a {@link org.ta4j.core.rules.ChainRule
 * ChainRule}. Every Chainlink has a {@link Rule} and a {@code threshold}.
 * ChainLinks are evaluated in the trade they are added to the ChainRule and the
 * rule has to be satisfied within a specified "number of bars (= threshold)".
 */
public class ChainLink implements Serializable {

    private static final long serialVersionUID = -436033401669929601L;

    /** The {@link Rule}, which must be satisfied within the threshold. */
    private Rule rule;

    /**
     * The number of bars in which the rule must be satisfied. The current index is
     * included.
     */
    private int threshold = 0;

    /**
     * Threshold is the number of bars the provided rule has to be satisfied after
     * the preceding rule.
     *
     * @param rule      the {@link Rule}, which must be satisfied within the
     *                  threshold
     * @param threshold the number of bars in which the rule must be satisfied. The
     *                  current index is included.
     */
    public ChainLink(Rule rule, int threshold) {
        this.rule = rule;
        this.threshold = threshold;
    }

    /**
     * @return {@link #rule}
     */
    public Rule getRule() {
        return rule;
    }

    /**
     * @param rule the {@link #rule}
     */
    public void setRule(Rule rule) {
        this.rule = rule;
    }

    /**
     * @return {@link #threshold}
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * @param threshold the {@link #threshold}
     */
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
