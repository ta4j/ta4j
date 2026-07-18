/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.zigzag;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.AbstractRecentSwingIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Recent ZigZag Swing Low Indicator.
 * <p>
 * Returns the price value of the most recently confirmed ZigZag swing low at
 * each bar. A swing low is confirmed when price reverses upward by at least the
 * reversal threshold after reaching a new low during a down-trend.
 * <p>
 * This indicator is useful for:
 * <ul>
 * <li>Tracking the most recent swing low price level</li>
 * <li>Identifying potential support levels</li>
 * <li>Calculating distances from current price to the last swing low</li>
 * <li>Building trend-following strategies based on swing lows</li>
 * </ul>
 * <p>
 * The indicator returns {@link NaN} if no swing low has been confirmed yet at
 * the given index. Use {@link ZigZagPivotLowIndicator} to detect when a new
 * swing low is confirmed in real-time.
 * <p>
 * The state-only constructor uses the state's low-price source. An explicit
 * source can be supplied when custom swing pricing is intentional.
 *
 * @see ZigZagStateIndicator
 * @see ZigZagPivotLowIndicator
 * @see RecentZigZagSwingHighIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinglow.asp">Investopedia: Swing
 *      Low</a>
 * @since 0.20
 */
public class RecentZigZagSwingLowIndicator extends AbstractRecentSwingIndicator {

    private final ZigZagStateIndicator stateIndicator;
    private final Indicator<Num> price;

    /**
     * Constructs an indicator using the state's low-price source.
     *
     * @param stateIndicator ZigZag state providing swing indexes and low prices
     * @since 0.22.9
     */
    public RecentZigZagSwingLowIndicator(ZigZagStateIndicator stateIndicator) {
        this(stateIndicator, Objects.requireNonNull(stateIndicator, "stateIndicator").lowPriceIndicator());
    }

    /**
     * Constructs an indicator with an explicit swing-low price source.
     *
     * @param stateIndicator the ZigZagStateIndicator that tracks the ZigZag pattern
     *                       state
     * @param price          the price indicator used to retrieve swing-low values;
     *                       typically the {@code LowPriceIndicator} corresponding
     *                       to the state indicator's low-price component
     */
    public RecentZigZagSwingLowIndicator(ZigZagStateIndicator stateIndicator, Indicator<Num> price) {
        this(validatedConfig(stateIndicator, price));
    }

    private RecentZigZagSwingLowIndicator(Config config) {
        super(config.price(), config.stateIndicator().getCountOfUnstableBars());
        this.stateIndicator = config.stateIndicator();
        this.price = config.price();
    }

    public RecentZigZagSwingLowIndicator(BarSeries series) {
        this(new ZigZagStateIndicator(new HighPriceIndicator(series), new LowPriceIndicator(series),
                new ATRIndicator(series, 14)), new LowPriceIndicator(series));
    }

    /**
     * Returns the index of the most recent confirmed swing low as of the given
     * index.
     *
     * @param index the bar index to evaluate
     * @return the index of the most recent confirmed swing low, or {@code -1} if no
     *         swing low has been confirmed yet
     */
    public int getLatestSwingLowIndex(int index) {
        return getLatestSwingIndex(index);
    }

    @Override
    public Indicator<Num> getPriceIndicator() {
        return price;
    }

    @Override
    protected int detectLatestSwingIndex(int index) {
        final ZigZagState state = stateIndicator.getValue(index);
        return state.getLastLowIndex();
    }

    private static Config validatedConfig(ZigZagStateIndicator stateIndicator, Indicator<Num> price) {
        ZigZagStateIndicator safeStateIndicator = Objects.requireNonNull(stateIndicator, "stateIndicator");
        Indicator<Num> safePrice = Objects.requireNonNull(price, "price");
        IndicatorUtils.requireSameSeries(safeStateIndicator, safePrice);
        return new Config(safeStateIndicator.copy(), safePrice);
    }

    private record Config(ZigZagStateIndicator stateIndicator, Indicator<Num> price) {
    }
}
