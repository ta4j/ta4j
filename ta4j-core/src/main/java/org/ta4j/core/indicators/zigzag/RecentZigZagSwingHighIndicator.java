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
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Recent ZigZag Swing High Indicator.
 * <p>
 * Returns the price value of the most recently confirmed ZigZag swing high at
 * each bar. A swing high is confirmed when price reverses downward by at least
 * the reversal threshold after reaching a new high during an up-trend.
 * <p>
 * This indicator is useful for:
 * <ul>
 * <li>Tracking the most recent swing high price level</li>
 * <li>Identifying potential resistance levels</li>
 * <li>Calculating distances from current price to the last swing high</li>
 * <li>Building trend-following strategies based on swing highs</li>
 * </ul>
 * <p>
 * The indicator returns {@link NaN} if no swing high has been confirmed yet at
 * the given index. Use {@link ZigZagPivotHighIndicator} to detect when a new
 * swing high is confirmed in real-time.
 * <p>
 * The state-only constructor uses the state's high-price source. An explicit
 * source can be supplied when custom swing pricing is intentional.
 *
 * @see ZigZagStateIndicator
 * @see ZigZagPivotHighIndicator
 * @see RecentZigZagSwingLowIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinghigh.asp">Investopedia: Swing
 *      High</a>
 * @since 0.20
 */
public class RecentZigZagSwingHighIndicator extends AbstractRecentSwingIndicator {

    private final ZigZagStateIndicator stateIndicator;
    private final Indicator<Num> price;

    /**
     * Constructs an indicator using the state's high-price source.
     *
     * @param stateIndicator ZigZag state providing swing indexes and high prices
     * @since 0.22.9
     */
    public RecentZigZagSwingHighIndicator(ZigZagStateIndicator stateIndicator) {
        this(stateIndicator, Objects.requireNonNull(stateIndicator, "stateIndicator").highPriceIndicator());
    }

    /**
     * Constructs an indicator with an explicit swing-high price source.
     *
     * @param stateIndicator the ZigZagStateIndicator that tracks the ZigZag pattern
     *                       state
     * @param price          the price indicator used to retrieve swing-high values;
     *                       typically the {@code HighPriceIndicator} corresponding
     *                       to the state indicator's high-price component
     */
    public RecentZigZagSwingHighIndicator(ZigZagStateIndicator stateIndicator, Indicator<Num> price) {
        this(validatedConfig(stateIndicator, price));
    }

    private RecentZigZagSwingHighIndicator(Config config) {
        super(config.price(), config.stateIndicator().getCountOfUnstableBars());
        this.stateIndicator = config.stateIndicator();
        this.price = config.price();
    }

    public RecentZigZagSwingHighIndicator(BarSeries series) {
        this(new ZigZagStateIndicator(new HighPriceIndicator(series), new LowPriceIndicator(series),
                new ATRIndicator(series, 14)), new HighPriceIndicator(series));
    }

    /**
     * Returns the index of the most recent confirmed swing high as of the given
     * index.
     *
     * @param index the bar index to evaluate
     * @return the index of the most recent confirmed swing high, or {@code -1} if
     *         no swing high has been confirmed yet
     */
    public int getLatestSwingHighIndex(int index) {
        return getLatestSwingIndex(index);
    }

    @Override
    public Indicator<Num> getPriceIndicator() {
        return price;
    }

    @Override
    protected int detectLatestSwingIndex(int index) {
        final ZigZagState state = stateIndicator.getValue(index);
        return state.getLastHighIndex();
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
