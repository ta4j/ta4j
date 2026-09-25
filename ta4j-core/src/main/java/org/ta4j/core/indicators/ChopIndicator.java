/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Choppiness Index (CHOP), used to distinguish sideways markets from
 * directional trends.
 *
 * <pre>
 * CHOP = log(sum(ATR(1), n) / (highestHigh(n) - lowestLow(n))) / log(n)
 * </pre>
 *
 * <p>
 * The two-argument constructor returns conventional percentage values, normally
 * read on a {@code 0-100} scale. Use {@link ReturnRepresentation#DECIMAL} for
 * the equivalent decimal ratio. Log and multiplicative return representations
 * are not meaningful for this bounded oscillator and are rejected.
 *
 * <p>
 * The legacy integer scale constructor remains available for compatibility with
 * arbitrary historical scales.
 *
 * @see <a href=
 *      "https://www.tradingview.com/wiki/Choppiness_Index_(CHOP)">https://www.tradingview.com/wiki/Choppiness_Index_(CHOP)</a>
 */
public final class ChopIndicator extends CachedIndicator<Num> {

    private final int ciTimeFrame;
    private final ReturnRepresentation returnRepresentation;
    private final Integer scaleTo;
    private final transient HighestValueIndicator hvi;
    private final transient LowestValueIndicator lvi;
    private final transient RunningTotalIndicator atrSumIndicator;
    private final transient Num logTimeFrame;

    /**
     * Constructor using conventional percentage output.
     *
     * @param barSeries   bar series
     * @param ciTimeFrame rolling observation count
     * @since 0.23.1
     */
    public ChopIndicator(BarSeries barSeries, int ciTimeFrame) {
        this(barSeries, ciTimeFrame, ReturnRepresentation.PERCENTAGE);
    }

    /**
     * Constructor using an explicit decimal or percentage representation.
     *
     * @param barSeries            bar series
     * @param ciTimeFrame          rolling observation count, at least two
     * @param returnRepresentation output representation; either
     *                             {@link ReturnRepresentation#DECIMAL} or
     *                             {@link ReturnRepresentation#PERCENTAGE}
     * @since 0.23.1
     */
    public ChopIndicator(BarSeries barSeries, int ciTimeFrame, ReturnRepresentation returnRepresentation) {
        this(validatedConfig(barSeries, ciTimeFrame, returnRepresentation, null));
    }

    /**
     * Legacy constructor using an arbitrary output scale.
     *
     * @param barSeries   bar series
     * @param ciTimeFrame rolling observation count, at least two
     * @param scaleTo     legacy output scale
     * @deprecated use {@link #ChopIndicator(BarSeries, int, ReturnRepresentation)}
     *             with {@link ReturnRepresentation#DECIMAL} or
     *             {@link ReturnRepresentation#PERCENTAGE}
     */
    @Deprecated(since = "0.23.1")
    public ChopIndicator(BarSeries barSeries, int ciTimeFrame, int scaleTo) {
        this(validatedConfig(barSeries, ciTimeFrame, null, scaleTo));
    }

    private ChopIndicator(Config config) {
        super(config.barSeries());
        this.ciTimeFrame = config.ciTimeFrame();
        this.returnRepresentation = config.returnRepresentation();
        this.scaleTo = config.scaleTo();
        ATRIndicator atrIndicator = new ATRIndicator(config.barSeries(), 1);
        this.atrSumIndicator = new RunningTotalIndicator(atrIndicator, ciTimeFrame);
        this.hvi = new HighestValueIndicator(new HighPriceIndicator(config.barSeries()), ciTimeFrame);
        this.lvi = new LowestValueIndicator(new LowPriceIndicator(config.barSeries()), ciTimeFrame);
        this.logTimeFrame = getBarSeries().numFactory().numOf(ciTimeFrame).log();
    }

    @Override
    public Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num atrSum = atrSumIndicator.getValue(index);
        Num range = hvi.getValue(index).minus(lvi.getValue(index));
        if (!Num.isFinite(atrSum) || !Num.isFinite(range) || !range.isPositive()) {
            return NaN.NaN;
        }
        try {
            Num normalized = atrSum.dividedBy(range).log().dividedBy(logTimeFrame);
            if (!Num.isFinite(normalized)) {
                return NaN.NaN;
            }
            if (returnRepresentation != null) {
                Num represented = returnRepresentation.toRepresentationFromRateOfReturn(normalized);
                return Num.isFinite(represented) ? represented : NaN.NaN;
            }
            Num scaled = normalized.multipliedBy(getBarSeries().numFactory().numOf(scaleTo));
            return Num.isFinite(scaled) ? scaled : NaN.NaN;
        } catch (ArithmeticException exception) {
            return NaN.NaN;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        int atrUnstableBars = atrSumIndicator.getCountOfUnstableBars();
        int highLowUnstableBars = Math.max(hvi.getCountOfUnstableBars(), lvi.getCountOfUnstableBars());
        return Math.max(atrUnstableBars, highLowUnstableBars);
    }

    private static Config validatedConfig(BarSeries barSeries, int ciTimeFrame,
            ReturnRepresentation returnRepresentation, Integer scaleTo) {
        BarSeries validatedSeries = Objects.requireNonNull(barSeries, "barSeries must not be null");
        if (ciTimeFrame < 2) {
            throw new IllegalArgumentException("ciTimeFrame must be >= 2");
        }
        if (scaleTo == null && returnRepresentation != ReturnRepresentation.DECIMAL
                && returnRepresentation != ReturnRepresentation.PERCENTAGE) {
            throw new IllegalArgumentException("returnRepresentation must be DECIMAL or PERCENTAGE");
        }
        return new Config(validatedSeries, ciTimeFrame, returnRepresentation, scaleTo);
    }

    private record Config(BarSeries barSeries, int ciTimeFrame, ReturnRepresentation returnRepresentation,
            Integer scaleTo) {
    }
}
