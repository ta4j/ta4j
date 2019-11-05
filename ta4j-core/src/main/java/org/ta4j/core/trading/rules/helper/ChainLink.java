package org.ta4j.core.trading.rules.helper;

import org.ta4j.core.Rule;

import java.io.Serializable;
import java.util.Objects;

public class ChainLink implements Serializable {
    private Rule rule;
    private int threshold = 0;

    /**
     * Threshold is the number of bars the provided rule has to be satisfied after the preceding rule
     *
     * @param rule
     * @param threshold
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
        if (this == o) return true;
        if (!(o instanceof ChainLink)) return false;
        ChainLink chainLink = (ChainLink) o;
        return getThreshold() == chainLink.getThreshold() &&
                Objects.equals(getRule(), chainLink.getRule());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRule(), getThreshold());
    }

    @Override
    public String toString() {
        return "ChainLink{" +
                "rule=" + rule +
                ", threshold=" + threshold +
                '}';
    }
}
