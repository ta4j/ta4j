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
package org.ta4j.core.rules

import org.ta4j.core.*
import org.ta4j.core.rules.helper.ChainLink
import java.util.*

/**
 * A chainrule has an initial rule that has to be satisfied before chain links
 * are evaluated. If the initial rule is satisfied every rule of chain link has
 * to be satisfied within a specified amount of bars (threshold).
 *
 */
class ChainRule(private val initialRule: Rule, vararg chainLinks: ChainLink) : AbstractRule() {
    var rulesInChain = LinkedList<ChainLink>()

    /**
     * @param initialRule the first rule that has to be satisfied before
     * [ChainLink] are evaluated
     * @param chainLinks  [ChainLink] that has to be satisfied after the
     * inital rule within their thresholds
     */
    init {
        rulesInChain.addAll(chainLinks)
    }

    /** This rule uses the `tradingRecord`.  */
    override fun isSatisfied(index: Int, tradingRecord: TradingRecord?): Boolean {
        var lastRuleWasSatisfiedAfterBars = 0
        var startIndex = index
        if (!initialRule.isSatisfied(index, tradingRecord)) {
            traceIsSatisfied(index, false)
            return false
        }
        traceIsSatisfied(index, true)
        for (link in rulesInChain) {
            var satisfiedWithinThreshold = false
            startIndex = startIndex - lastRuleWasSatisfiedAfterBars
            lastRuleWasSatisfiedAfterBars = 0
            for (i in 0..link.threshold) {
                val resultingIndex = startIndex - i
                if (resultingIndex < 0) {
                    break
                }
                satisfiedWithinThreshold = link.rule.isSatisfied(resultingIndex, tradingRecord)
                if (satisfiedWithinThreshold == true) {
                    break
                }
                lastRuleWasSatisfiedAfterBars++
            }
            if (!satisfiedWithinThreshold) {
                traceIsSatisfied(index, false)
                return false
            }
        }
        traceIsSatisfied(index, true)
        return true
    }
}