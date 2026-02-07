/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import org.ta4j.core.indicators.PriceChannel;
import org.ta4j.core.num.Num;

/**
 * Encapsulates a projected Elliott price channel for the current bar.
 *
 * <p>
 * Elliott channels are parallel trend lines that help validate wave structure
 * by projecting expected support and resistance levels. The channel is
 * calculated from the most recent two swing highs and lows, with the median
 * representing the arithmetic midline between the boundaries.
 *
 * <p>
 * This record implements {@link org.ta4j.core.indicators.PriceChannel} and
 * provides all standard channel operations including validity checks, width
 * calculation, and price containment tests.
 *
 * <p>
 * Use this value object when you need to pass channel boundaries to charting,
 * confidence scoring, or rule logic without re-computing projection math.
 *
 * @param upper  expected resistance boundary
 * @param lower  expected support boundary
 * @param median arithmetic midline between upper and lower bounds
 * @see org.ta4j.core.indicators.elliott.ElliottChannelIndicator
 * @see org.ta4j.core.indicators.PriceChannel
 * @since 0.22.0
 */
public record ElliottChannel(Num upper, Num lower, Num median) implements PriceChannel {
}
