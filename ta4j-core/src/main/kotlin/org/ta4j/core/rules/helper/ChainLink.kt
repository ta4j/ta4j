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
package org.ta4j.core.rules.helper

import org.ta4j.core.*
import java.io.Serializable
import java.util.*

/**
 * A ChainLink is part of a [org.ta4j.core.rules.ChainRule]. Every
 * Chainlink has a [Rule] and a threshold. ChainLinks are evaluated in the
 * trade they are added to the ChainRule and the rule has to be satisfied within
 * the threshold number of bars.
 */
/**
 * Threshold is the number of bars the provided rule has to be satisfied after
 * the preceding rule
 *
 * @param rule      A [Rule] that has to be satisfied within the threshold
 * @param threshold Number of bars the rule has to be satisfied in. The current
 * index is included.
 */

class ChainLink(var rule: Rule, var threshold: Int) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChainLink) return false
        val chainLink = other
        return threshold == chainLink.threshold && rule == chainLink.rule
    }

    override fun hashCode(): Int {
        return Objects.hash(rule, threshold)
    }

    override fun toString(): String {
        return "ChainLink{rule=$rule, threshold=$threshold}"
    }

    companion object {
        private const val serialVersionUID = -436033401669929601L
    }
}