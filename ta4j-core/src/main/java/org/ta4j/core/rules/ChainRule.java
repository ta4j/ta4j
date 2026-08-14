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
        this.series = findBarSeries(initialRule, rulesInChain);
    }

    private static BarSeries findBarSeries(Rule initialRule, LinkedList<ChainLink> rulesInChain) {
        return RuleCopies.findBarSeries(initialRule)
                .or(() -> rulesInChain.stream()
                        .map(chainLink -> RuleCopies.findBarSeries(chainLink.getRule()))
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

                satisfiedWithinThreshold = evaluateChildRule(link.getRule(), "chainRule" + linkIndex, resultingIndex,
                        tradingRecord);

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
