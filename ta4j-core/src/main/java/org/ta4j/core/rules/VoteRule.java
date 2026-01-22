/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * A voting combination of multiple {@link Rule rules}.
 *
 * <p>
 * Satisfied when at least the required number of rules are satisfied.
 *
 * <p>
 * <b>Warning:</b> Early termination if we already have enough votes.
 *
 * @since 0.19
 */
public class VoteRule extends AbstractRule {

    private final List<Rule> rules;
    private final int requiredVotes;

    /**
     * Constructor with required votes count.
     *
     * @param requiredVotes the minimum number of rules that must be satisfied
     * @param rules         the rules to vote
     */
    public VoteRule(int requiredVotes, Rule... rules) {
        this(requiredVotes, Arrays.asList(rules));
    }

    /**
     * Constructor with required votes count.
     *
     * @param requiredVotes the minimum number of rules that must be satisfied
     * @param rules         the rules to vote
     */
    public VoteRule(int requiredVotes, List<Rule> rules) {
        if (requiredVotes < 1) {
            throw new IllegalArgumentException("Required votes must be at least 1");
        }
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("At least one rule is required");
        }
        if (requiredVotes > rules.size()) {
            throw new IllegalArgumentException("Required votes cannot exceed number of rules");
        }

        this.requiredVotes = requiredVotes;
        for (Rule rule : rules) {
            Objects.requireNonNull(rule, "rule cannot be null");
        }
        this.rules = List.copyOf(rules);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        int count = 0;
        for (Rule rule : rules) {
            if (rule.isSatisfied(index, tradingRecord)) {
                count++;
                // Early termination if we already have enough votes
                if (count >= requiredVotes) {
                    break;
                }
            }
        }

        final boolean satisfied = count >= requiredVotes;
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    /**
     * @return the list of rules
     */
    public List<Rule> getRules() {
        return rules;
    }

    /**
     * @return the required number of votes
     */
    public int getRequiredVotes() {
        return requiredVotes;
    }
}
