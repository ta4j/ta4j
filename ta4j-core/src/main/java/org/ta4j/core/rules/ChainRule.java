/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.rules.helper.ChainLink;

/**
 * A {@code ChainRule} has an initial rule that has to be satisfied before a
 * list of {@link ChainLink chain links} are evaluated. If the initial rule is
 * satisfied, each rule in {@link ChainRule#rulesInChain chain links} has to be
 * satisfied within a specified "number of bars (= threshold)".
 */
public class ChainRule extends AbstractRule {

    private final Rule initialRule;
    private final LinkedList<ChainLink> rulesInChain = new LinkedList<>();
    /**
     * Link rule copies, refreshed only when a link's rule is replaced.
     * {@link ChainLink#getRule()} returns a fresh copy on every call, so reading it
     * per evaluation would rebuild each link rule through serialization for every
     * bar and threshold step and would discard the state of stateful link rules.
     */
    private transient Rule[] linkRules;
    private transient int[] linkRuleVersions;

    /** The backing series used to resolve the live begin index, or {@code null}. */
    private final BarSeries series;

    /**
     * @param initialRule the first rule that has to be satisfied before
     *                    {@link ChainLink} are evaluated
     * @param chainLinks  {@link ChainLink} that has to be satisfied after the
     *                    initial rule within their thresholds
     */
    public ChainRule(Rule initialRule, ChainLink... chainLinks) {
        this.initialRule = Objects.requireNonNull(initialRule, "initialRule cannot be null");
        Objects.requireNonNull(chainLinks, "chainLinks cannot be null");
        for (ChainLink chainLink : chainLinks) {
            this.rulesInChain.add(Objects.requireNonNull(chainLink, "chainLink cannot be null"));
        }
        this.series = findBarSeries(initialRule, rulesInChain.stream().map(ChainLink::getRule).toArray(Rule[]::new));
    }

    private Rule linkRule(final ChainLink link, final int linkIndex) {
        if (linkRules == null) {
            // Also rebuilds the copies after Java deserialization.
            linkRules = new Rule[rulesInChain.size()];
            linkRuleVersions = new int[rulesInChain.size()];
        }
        final int version = link.getRuleVersion();
        if (linkRules[linkIndex] == null || linkRuleVersions[linkIndex] != version) {
            linkRules[linkIndex] = link.getRule();
            linkRuleVersions[linkIndex] = version;
        }
        return linkRules[linkIndex];
    }

    private static BarSeries findBarSeries(Rule initialRule, Rule[] linkRules) {
        return RuleCopies.findBarSeries(initialRule)
                .or(() -> java.util.Arrays.stream(linkRules)
                        .map(RuleCopies::findBarSeries)
                        .flatMap(Optional::stream)
                        .findFirst())
                .orElse(null);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        // Resolve the retained begin index at evaluation time: on a rolling
        // series the constructor-time value goes stale as bars are evicted.
        final int beginIndex = series == null ? 0 : series.getBeginIndex();
        int lastRuleWasSatisfiedAfterBars = 0;
        int startIndex = index;

        if (!evaluateChildRule(initialRule, "initialRule", index, tradingRecord)) {
            if (isTraceEnabled()) {
                traceIsSatisfied(index, false, traceContext("initialRule", false));
            }
            return false;
        }

        int linkIndex = 0;
        for (ChainLink link : rulesInChain) {
            boolean satisfiedWithinThreshold = false;
            startIndex = startIndex - lastRuleWasSatisfiedAfterBars;
            lastRuleWasSatisfiedAfterBars = 0;

            for (int i = 0; i <= link.getThreshold(); i++) {
                int resultingIndex = startIndex - i;
                if (resultingIndex < beginIndex) {
                    break;
                }

                satisfiedWithinThreshold = evaluateChildRule(linkRule(link, linkIndex), "chainRule" + linkIndex,
                        resultingIndex, tradingRecord);

                if (satisfiedWithinThreshold) {
                    break;
                }

                lastRuleWasSatisfiedAfterBars++;
            }

            if (!satisfiedWithinThreshold) {
                if (isTraceEnabled()) {
                    traceIsSatisfied(index, false, traceContext("initialRule", true, "failedChainRule", linkIndex,
                            "threshold", link.getThreshold()));
                }
                return false;
            }
            linkIndex++;
        }

        if (isTraceEnabled()) {
            traceIsSatisfied(index, true, traceContext("initialRule", true, "chainRules", rulesInChain.size()));
        }
        return true;
    }
}
