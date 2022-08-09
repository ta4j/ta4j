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
package org.ta4j.core

import org.slf4j.LoggerFactory

/**
 * Base implementation of a [Strategy].
 */
class BaseStrategy @JvmOverloads constructor(
    name: String?,
    entryRule: Rule?,
    exitRule: Rule?,
    unstablePeriod: Int = 0
) : Strategy {
    /** The logger  */
    @Transient
    protected val log = LoggerFactory.getLogger(javaClass)

    /** The class name  */
    private val className = javaClass.simpleName

    /** Name of the strategy  */
    override val name: String?

    /** The entry rule  */
    override val entryRule: Rule

    /** The exit rule  */
    override val exitRule: Rule

    /**
     * The unstable period (number of bars).<br></br>
     * During the unstable period of the strategy any trade placement will be
     * cancelled.<br></br>
     * I.e. no entry/exit signal will be fired before index == unstablePeriod.
     */
    override var unstablePeriod: Int=0

    /**
     * Constructor.
     *
     * @param entryRule the entry rule
     * @param exitRule  the exit rule
     */
    constructor(entryRule: Rule?, exitRule: Rule?) : this(null, entryRule, exitRule, 0) {}

    /**
     * Constructor.
     *
     * @param entryRule      the entry rule
     * @param exitRule       the exit rule
     * @param unstablePeriod strategy will ignore possible signals at
     * `index` < `unstablePeriod`
     */
    constructor(entryRule: Rule?, exitRule: Rule?, unstablePeriod: Int) : this(
        null,
        entryRule,
        exitRule,
        unstablePeriod
    ) {
    }
    /**
     * Constructor.
     *
     * @param name           the name of the strategy
     * @param entryRule      the entry rule
     * @param exitRule       the exit rule
     * @param unstablePeriod strategy will ignore possible signals at
     * `index` < `unstablePeriod`
     */
    /**
     * Constructor.
     *
     * @param name      the name of the strategy
     * @param entryRule the entry rule
     * @param exitRule  the exit rule
     */
    init {
        require(!(entryRule == null || exitRule == null)) { "Rules cannot be null" }
        require(unstablePeriod >= 0) { "Unstable period bar count must be >= 0" }
        this.name = name
        this.entryRule = entryRule
        this.exitRule = exitRule
        this.unstablePeriod = unstablePeriod
    }

    override fun isUnstableAt(index: Int): Boolean {
        return index < unstablePeriod
    }

    override fun shouldEnter(index: Int, tradingRecord: TradingRecord?): Boolean {
        val enter: Boolean = super.shouldEnter(index, tradingRecord)
        traceShouldEnter(index, enter)
        return enter
    }

    override fun shouldExit(index: Int, tradingRecord: TradingRecord?): Boolean {
        val exit: Boolean = super.shouldExit(index, tradingRecord)
        traceShouldExit(index, exit)
        return exit
    }

    override fun and(strategy: Strategy): Strategy {
        val andName = "and(" + name + "," + strategy.name + ")"
        val unstable = Math.max(unstablePeriod, strategy.unstablePeriod)
        return and(andName, strategy, unstable)
    }

    override fun or(strategy: Strategy): Strategy {
        val orName = "or(" + name + "," + strategy.name + ")"
        val unstable = Math.max(unstablePeriod, strategy.unstablePeriod)
        return or(orName, strategy, unstable)
    }

    override fun opposite(): Strategy {
        return BaseStrategy("opposite($name)", exitRule, entryRule, unstablePeriod)
    }

    override fun and(name: String?, strategy: Strategy, unstablePeriod: Int): Strategy {
        return BaseStrategy(
            name, entryRule.and(strategy.entryRule), exitRule.and(strategy.exitRule),
            unstablePeriod
        )
    }

    override fun or(name: String?, strategy: Strategy, unstablePeriod: Int): Strategy {
        return BaseStrategy(
            name, entryRule.or(strategy.entryRule), exitRule.or(strategy.exitRule),
            unstablePeriod
        )
    }

    /**
     * Traces the shouldEnter() method calls.
     *
     * @param index the bar index
     * @param enter true if the strategy should enter, false otherwise
     */
    protected fun traceShouldEnter(index: Int, enter: Boolean) {
        if (log.isTraceEnabled) {
            log.trace(">>> {}#shouldEnter({}): {}", className, index, enter)
        }
    }

    /**
     * Traces the shouldExit() method calls.
     *
     * @param index the bar index
     * @param exit  true if the strategy should exit, false otherwise
     */
    protected fun traceShouldExit(index: Int, exit: Boolean) {
        if (log.isTraceEnabled) {
            log.trace(">>> {}#shouldExit({}): {}", className, index, exit)
        }
    }
}