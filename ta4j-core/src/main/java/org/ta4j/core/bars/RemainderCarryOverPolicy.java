/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.bars;

/**
 * Policy for handling side/liquidity data when a volume or amount bar carries a
 * remainder into the next bar.
 *
 * <p>
 * Trade remainders occur when a single trade pushes the bar past its volume or
 * amount threshold. The bar is capped and the remainder is rolled into the next
 * bar. This policy controls whether side/liquidity breakdowns follow that
 * remainder.
 *
 * <p>
 * In practice:
 * <ul>
 * <li>{@link #NONE} keeps side/liquidity data attached to the trade that caused
 * the rollover. This preserves trade fidelity (no synthetic splits) but can
 * make side/liquidity totals diverge from the capped volume/amount.</li>
 * <li>{@link #PROPORTIONAL} splits the trade's side/liquidity volumes and
 * amounts proportionally between the capped bar and the remainder. This keeps
 * side/liquidity totals aligned with volume/amount but injects an assumption
 * about how to attribute partial trades.</li>
 * <li>{@link #PROPORTIONAL_WITH_TRADE_COUNT} applies the proportional split and
 * assigns the trade count to the bar that receives the larger portion of the
 * final trade (rounded: the remainder receives the count if its share is at
 * least 50%). This preserves integer trade counts while keeping them aligned
 * with the majority of the trade volume.</li>
 * </ul>
 *
 * <p>
 * Trade counts are not split by this policy unless
 * {@link #PROPORTIONAL_WITH_TRADE_COUNT} is selected; they remain whole-trade
 * counts.
 *
 * @since 0.22.2
 */
public enum RemainderCarryOverPolicy {

    /**
     * Do not carry side/liquidity data with the remainder.
     *
     * @since 0.22.2
     */
    NONE,

    /**
     * Split side/liquidity volumes and amounts proportionally with the remainder.
     *
     * @since 0.22.2
     */
    PROPORTIONAL,
    /**
     * Split side/liquidity volumes and amounts proportionally and allocate the
     * trade count to the remainder if its share is at least 50%.
     *
     * @since 0.22.2
     */
    PROPORTIONAL_WITH_TRADE_COUNT
}
