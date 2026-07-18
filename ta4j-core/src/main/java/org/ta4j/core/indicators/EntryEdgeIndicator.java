/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Rolling realized edge score for entry signals.
 *
 * <p>
 * The indicator measures how much better a signal performs than an
 * unconditional baseline using only fully matured forward-return observations.
 * At index {@code i}, the score is computed from signal bars whose
 * {@code i + horizonBars} outcome is already known, which avoids look-ahead
 * leakage.
 * </p>
 *
 * <p>
 * Values are returned in basis points. Positive values indicate the signal's
 * recent realized outcomes beat the unconditional baseline; negative values
 * indicate underperformance.
 * </p>
 *
 * @since 0.22.7
 */
public class EntryEdgeIndicator extends CachedIndicator<Num> {

    private static final double BASIS_POINTS = 10_000.0d;
    private static final int NO_SIGNAL_INDEX = -1;

    private final Indicator<Boolean> signalIndicator;
    private final Indicator<Num> priceIndicator;
    private final TradeType tradeType;
    private final int horizonBars;
    private final int lookbackSignals;
    private final Indicator<Integer> latestMaturedSignalIndexIndicator;

    /**
     * Creates a long-side edge indicator using close price as the return source.
     *
     * @param signalIndicator indicator returning {@code true} on signal bars
     * @param series          the bar series
     * @param horizonBars     the forward-return horizon
     * @param lookbackSignals the number of matured signal observations to include
     * @since 0.22.7
     */
    public EntryEdgeIndicator(Indicator<Boolean> signalIndicator, BarSeries series, int horizonBars,
            int lookbackSignals) {
        this(validatedConfig(signalIndicator, series, horizonBars, lookbackSignals));
    }

    /**
     * Creates a directional edge indicator using the supplied price source.
     *
     * @param signalIndicator indicator returning {@code true} on signal bars
     * @param priceIndicator  price indicator used to measure forward returns
     * @param tradeType       signal direction
     * @param horizonBars     the forward-return horizon
     * @param lookbackSignals the number of matured signal observations to include
     * @since 0.22.7
     */
    public EntryEdgeIndicator(Indicator<Boolean> signalIndicator, Indicator<Num> priceIndicator, TradeType tradeType,
            int horizonBars, int lookbackSignals) {
        this(validatedConfig(signalIndicator, priceIndicator, tradeType, horizonBars, lookbackSignals));
    }

    private EntryEdgeIndicator(Config config) {
        super(config.series());
        this.signalIndicator = config.signalIndicator();
        this.priceIndicator = config.priceIndicator();
        this.tradeType = config.tradeType();
        this.horizonBars = config.horizonBars();
        this.lookbackSignals = config.lookbackSignals();
        this.latestMaturedSignalIndexIndicator = createLatestMaturedSignalIndexIndicator();
    }

    private static Config validatedConfig(Indicator<Boolean> signalIndicator, Indicator<Num> priceIndicator,
            TradeType tradeType, int horizonBars, int lookbackSignals) {
        BarSeries series = IndicatorUtils.requireSameSeries(signalIndicator, priceIndicator);
        if (horizonBars < 1) {
            throw new IllegalArgumentException("horizonBars must be greater than zero");
        }
        if (lookbackSignals < 1) {
            throw new IllegalArgumentException("lookbackSignals must be greater than zero");
        }
        Indicator<Boolean> validatedSignalIndicator = Objects.requireNonNull(signalIndicator, "signalIndicator");
        Indicator<Num> validatedPriceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
        TradeType validatedTradeType = Objects.requireNonNull(tradeType, "tradeType");
        return new Config(series, validatedSignalIndicator, validatedPriceIndicator, validatedTradeType, horizonBars,
                lookbackSignals);
    }

    private static Config validatedConfig(Indicator<Boolean> signalIndicator, BarSeries series, int horizonBars,
            int lookbackSignals) {
        return validatedConfig(signalIndicator, new ClosePriceIndicator(series), TradeType.BUY, horizonBars,
                lookbackSignals);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        int lastEligibleIndex = index - horizonBars;
        int beginIndex = firstEligibleSignalIndex();
        if (lastEligibleIndex < beginIndex) {
            return NaN.NaN;
        }
        double signalTotal = 0.0d;
        int signalCount = 0;
        int signalIndex = latestMaturedSignalIndexIndicator.getValue(lastEligibleIndex);
        while (signalIndex >= beginIndex && signalCount < lookbackSignals) {
            double forwardReturn = signedForwardReturn(signalIndex);
            if (!Double.isFinite(forwardReturn)) {
                return NaN.NaN;
            }
            signalTotal += forwardReturn;
            signalCount++;
            int previousIndex = signalIndex - 1;
            signalIndex = previousIndex >= beginIndex ? latestMaturedSignalIndexIndicator.getValue(previousIndex)
                    : NO_SIGNAL_INDEX;
        }
        if (signalCount == 0) {
            return NaN.NaN;
        }

        double baselineTotal = 0.0d;
        int baselineCount = 0;
        int baselineStart = Math.max(beginIndex, lastEligibleIndex - Math.max(signalCount, lookbackSignals) + 1);
        for (int cursor = baselineStart; cursor <= lastEligibleIndex; cursor++) {
            double forwardReturn = signedForwardReturn(cursor);
            if (!Double.isFinite(forwardReturn)) {
                continue;
            }
            baselineTotal += forwardReturn;
            baselineCount++;
        }
        if (baselineCount == 0) {
            return NaN.NaN;
        }

        double edgeBps = (signalTotal / signalCount) - (baselineTotal / baselineCount);
        return getBarSeries().numFactory().numOf(edgeBps);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(signalIndicator.getCountOfUnstableBars(), priceIndicator.getCountOfUnstableBars())
                + horizonBars;
    }

    /**
     * @return the signal indicator used to pick entry bars
     * @since 0.22.7
     */
    public Indicator<Boolean> getSignalIndicator() {
        return signalIndicator;
    }

    /**
     * @return the price indicator used to measure forward returns
     * @since 0.22.7
     */
    public Indicator<Num> getPriceIndicator() {
        return priceIndicator;
    }

    /**
     * @return the trade direction used to sign forward returns
     * @since 0.22.7
     */
    public TradeType getTradeType() {
        return tradeType;
    }

    /**
     * @return the forward-return horizon
     * @since 0.22.7
     */
    public int getHorizonBars() {
        return horizonBars;
    }

    /**
     * @return the number of matured signal observations included in the score
     * @since 0.22.7
     */
    public int getLookbackSignals() {
        return lookbackSignals;
    }

    private double signedForwardReturn(int entryIndex) {
        int exitIndex = entryIndex + horizonBars;
        Num entryPrice = priceIndicator.getValue(entryIndex);
        Num exitPrice = priceIndicator.getValue(exitIndex);
        if (Num.isNaNOrNull(entryPrice) || Num.isNaNOrNull(exitPrice) || entryPrice.isZero()) {
            return Double.NaN;
        }
        Num basisPoints = entryPrice.getNumFactory().numOf(BASIS_POINTS);
        double rawReturn = exitPrice.minus(entryPrice).dividedBy(entryPrice).multipliedBy(basisPoints).doubleValue();
        return tradeType == TradeType.SELL ? -rawReturn : rawReturn;
    }

    private Indicator<Integer> createLatestMaturedSignalIndexIndicator() {
        return new RecursiveCachedIndicator<>(getBarSeries()) {
            @Override
            protected Integer calculate(int index) {
                int beginIndex = firstEligibleSignalIndex();
                if (index < beginIndex) {
                    return NO_SIGNAL_INDEX;
                }
                if (Boolean.TRUE.equals(signalIndicator.getValue(index))
                        && Double.isFinite(signedForwardReturn(index))) {
                    return index;
                }
                int previousIndex = index - 1;
                return previousIndex >= beginIndex ? getValue(previousIndex) : NO_SIGNAL_INDEX;
            }

            @Override
            public int getCountOfUnstableBars() {
                return EntryEdgeIndicator.this.getCountOfUnstableBars();
            }
        };
    }

    private int firstEligibleSignalIndex() {
        return Math.max(getBarSeries().getBeginIndex(),
                getBarSeries().getBeginIndex() + signalIndicator.getCountOfUnstableBars());
    }

    private record Config(BarSeries series, Indicator<Boolean> signalIndicator, Indicator<Num> priceIndicator,
            TradeType tradeType, int horizonBars, int lookbackSignals) {
    }
}
