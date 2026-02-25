/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * Bill Williams Gator Oscillator.
 * <p>
 * Gator Oscillator derives two histograms from the {@link AlligatorIndicator}
 * lines:
 * <ul>
 * <li>upper histogram: {@code |jaw - teeth|}</li>
 * <li>lower histogram: {@code -|teeth - lips|}</li>
 * </ul>
 * Use {@link #upper(BarSeries)} and {@link #lower(BarSeries)} for canonical
 * Bill Williams defaults.
 * <p>
 * This class represents one histogram branch at a time.
 *
 * @see AlligatorIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/g/gatoroscillator.asp">Investopedia:
 *      Gator Oscillator</a>
 * @since 0.22.3
 */
public class GatorOscillatorIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> jaw;
    private final Indicator<Num> teeth;
    private final Indicator<Num> lips;
    private final transient Indicator<Num> jawMinusTeeth;
    private final transient Indicator<Num> teethMinusLips;
    private final boolean upperHistogram;
    private final int unstableBars;

    /**
     * Constructor.
     *
     * @param jaw            alligator jaw line
     * @param teeth          alligator teeth line
     * @param lips           alligator lips line
     * @param upperHistogram {@code true} for upper branch, {@code false} for lower
     *                       branch
     * @since 0.22.3
     */
    public GatorOscillatorIndicator(Indicator<Num> jaw, Indicator<Num> teeth, Indicator<Num> lips,
            boolean upperHistogram) {
        super(jaw);
        ensureSameSeries(jaw, teeth, lips);
        this.jaw = jaw;
        this.teeth = teeth;
        this.lips = lips;
        this.jawMinusTeeth = BinaryOperationIndicator.difference(jaw, teeth);
        this.teethMinusLips = BinaryOperationIndicator.difference(teeth, lips);
        this.upperHistogram = upperHistogram;
        this.unstableBars = Math.max(Math.max(jaw.getCountOfUnstableBars(), teeth.getCountOfUnstableBars()),
                lips.getCountOfUnstableBars());
    }

    /**
     * Constructor using canonical Alligator lines from median price.
     *
     * @param series         the series
     * @param upperHistogram {@code true} for upper branch, {@code false} for lower
     *                       branch
     * @since 0.22.3
     */
    public GatorOscillatorIndicator(BarSeries series, boolean upperHistogram) {
        this(AlligatorIndicator.jaw(series), AlligatorIndicator.teeth(series), AlligatorIndicator.lips(series),
                upperHistogram);
    }

    /**
     * Creates the upper histogram branch.
     *
     * @param jaw   alligator jaw line
     * @param teeth alligator teeth line
     * @param lips  alligator lips line
     * @return upper histogram indicator
     * @since 0.22.3
     */
    public static GatorOscillatorIndicator upper(Indicator<Num> jaw, Indicator<Num> teeth, Indicator<Num> lips) {
        return new GatorOscillatorIndicator(jaw, teeth, lips, true);
    }

    /**
     * Creates the lower histogram branch.
     *
     * @param jaw   alligator jaw line
     * @param teeth alligator teeth line
     * @param lips  alligator lips line
     * @return lower histogram indicator
     * @since 0.22.3
     */
    public static GatorOscillatorIndicator lower(Indicator<Num> jaw, Indicator<Num> teeth, Indicator<Num> lips) {
        return new GatorOscillatorIndicator(jaw, teeth, lips, false);
    }

    /**
     * Creates the upper histogram branch from default Alligator lines.
     *
     * @param series the series
     * @return upper histogram indicator
     * @since 0.22.3
     */
    public static GatorOscillatorIndicator upper(BarSeries series) {
        return new GatorOscillatorIndicator(series, true);
    }

    /**
     * Creates the lower histogram branch from default Alligator lines.
     *
     * @param series the series
     * @return lower histogram indicator
     * @since 0.22.3
     */
    public static GatorOscillatorIndicator lower(BarSeries series) {
        return new GatorOscillatorIndicator(series, false);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        final Num spread = upperHistogram ? jawMinusTeeth.getValue(index).abs() : teethMinusLips.getValue(index).abs();
        if (isInvalid(spread)) {
            return NaN;
        }

        if (upperHistogram) {
            return spread;
        }
        return spread.multipliedBy(getBarSeries().numFactory().minusOne());
    }

    /**
     * @return {@code true} for upper branch, {@code false} for lower branch
     * @since 0.22.3
     */
    public boolean isUpperHistogram() {
        return upperHistogram;
    }

    /**
     * @return alligator jaw line
     * @since 0.22.3
     */
    public Indicator<Num> getJawIndicator() {
        return jaw;
    }

    /**
     * @return alligator teeth line
     * @since 0.22.3
     */
    public Indicator<Num> getTeethIndicator() {
        return teeth;
    }

    /**
     * @return alligator lips line
     * @since 0.22.3
     */
    public Indicator<Num> getLipsIndicator() {
        return lips;
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    private static void ensureSameSeries(Indicator<Num> jaw, Indicator<Num> teeth, Indicator<Num> lips) {
        if (jaw == null || teeth == null || lips == null) {
            throw new IllegalArgumentException("jaw, teeth, and lips indicators must not be null");
        }
        final BarSeries series = jaw.getBarSeries();
        if (!series.equals(teeth.getBarSeries()) || !series.equals(lips.getBarSeries())) {
            throw new IllegalArgumentException("Alligator indicators must share the same BarSeries");
        }
    }

    private static boolean isInvalid(Num value) {
        return value == null || value.isNaN() || Double.isNaN(value.doubleValue());
    }
}
