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
package org.ta4j.core.indicators.elliott;

import static java.util.Objects.requireNonNull;
import static org.ta4j.core.num.NaN.NaN;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.elliott.ElliottRatio.RatioType;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Aggregates Fibonacci ratio and channel alignment checks into a confluence
 * score suitable for trading rules.
 *
 * @since 0.19
 */
public class ElliottConfluenceIndicator extends CachedIndicator<Num> {

    private static final double[] DEFAULT_RETRACEMENT_LEVELS = { 0.236, 0.382, 0.5, 0.618, 0.786, 1.0 };
    private static final double[] DEFAULT_EXTENSION_LEVELS = { 1.272, 1.414, 1.618, 2.0 };

    private final Indicator<Num> priceIndicator;
    private final ElliottRatioIndicator ratioIndicator;
    private final ElliottChannelIndicator channelIndicator;
    private final List<Num> retracementLevels;
    private final List<Num> extensionLevels;
    private final Num ratioTolerance;
    private final Num channelTolerance;
    private final Num minimumScore;
    private final NumFactory numFactory;

    /**
     * Builds an indicator with default Fibonacci levels, 0.05 absolute tolerance
     * and a minimum score of 2.
     *
     * @param priceIndicator   price reference (typically close price)
     * @param ratioIndicator   ratio detector
     * @param channelIndicator channel detector
     * @since 0.19
     */
    public ElliottConfluenceIndicator(final Indicator<Num> priceIndicator, final ElliottRatioIndicator ratioIndicator,
            final ElliottChannelIndicator channelIndicator) {
        this(priceIndicator, ratioIndicator, channelIndicator, toList(priceIndicator, DEFAULT_RETRACEMENT_LEVELS),
                toList(priceIndicator, DEFAULT_EXTENSION_LEVELS),
                priceIndicator.getBarSeries().numFactory().numOf(0.05),
                priceIndicator.getBarSeries().numFactory().numOf(0.5),
                priceIndicator.getBarSeries().numFactory().numOf(2));
    }

    /**
     * Builds an indicator using custom ratios, tolerances and minimum score.
     *
     * @param priceIndicator    price reference (typically close price)
     * @param ratioIndicator    ratio detector
     * @param channelIndicator  channel detector
     * @param retracementLevels retracement targets to inspect
     * @param extensionLevels   extension targets to inspect
     * @param ratioTolerance    absolute tolerance around ratio targets
     * @param channelTolerance  absolute tolerance applied to channel containment
     * @param minimumScore      minimum confluence score considered a match
     * @since 0.19
     */
    public ElliottConfluenceIndicator(final Indicator<Num> priceIndicator, final ElliottRatioIndicator ratioIndicator,
            final ElliottChannelIndicator channelIndicator, final Collection<Num> retracementLevels,
            final Collection<Num> extensionLevels, final Num ratioTolerance, final Num channelTolerance,
            final Num minimumScore) {
        super(requireSeries(priceIndicator));
        this.priceIndicator = requireNonNull(priceIndicator, "priceIndicator");
        this.ratioIndicator = requireNonNull(ratioIndicator, "ratioIndicator");
        this.channelIndicator = requireNonNull(channelIndicator, "channelIndicator");
        this.numFactory = getBarSeries().numFactory();
        this.retracementLevels = toImmutableList(retracementLevels);
        this.extensionLevels = toImmutableList(extensionLevels);
        this.ratioTolerance = requireNonNull(ratioTolerance, "ratioTolerance");
        this.channelTolerance = requireNonNull(channelTolerance, "channelTolerance");
        this.minimumScore = requireNonNull(minimumScore, "minimumScore");
    }

    private static BarSeries requireSeries(final Indicator<Num> priceIndicator) {
        final BarSeries series = requireNonNull(priceIndicator, "priceIndicator").getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("Price indicator must expose a backing series");
        }
        return series;
    }

    private static List<Num> toList(final Indicator<Num> indicator, final double[] levels) {
        final List<Num> values = new ArrayList<>(levels.length);
        final NumFactory factory = requireSeries(indicator).numFactory();
        for (double level : levels) {
            values.add(factory.numOf(level));
        }
        return values;
    }

    private static List<Num> toImmutableList(final Collection<Num> levels) {
        final List<Num> result = new ArrayList<>(levels.size());
        for (Num level : levels) {
            if (level != null) {
                result.add(level);
            }
        }
        return List.copyOf(result);
    }

    @Override
    protected Num calculate(final int index) {
        if (index < getBarSeries().getBeginIndex()) {
            return NaN;
        }
        int score = 0;

        final ElliottRatio ratio = ratioIndicator.getValue(index);
        if (matchesRatioLevel(ratio)) {
            score++;
        }

        final ElliottChannel channel = channelIndicator.getValue(index);
        final Num price = priceIndicator.getValue(index);
        if (channel != null && channel.isValid() && channel.contains(price, channelTolerance)) {
            score++;
        }

        return numFactory.numOf(score);
    }

    private boolean matchesRatioLevel(final ElliottRatio ratio) {
        if (ratio == null || ratio.type() == RatioType.NONE) {
            return false;
        }
        final Num value = ratio.value();
        if (value == null || value.isNaN()) {
            return false;
        }
        final List<Num> levels = ratio.type() == RatioType.RETRACEMENT ? retracementLevels : extensionLevels;
        for (Num level : levels) {
            if (level == null || level.isNaN()) {
                continue;
            }
            final Num distance = value.minus(level).abs();
            if (!distance.isGreaterThan(ratioTolerance)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param index bar index
     * @return {@code true} when the cached confluence score meets or exceeds the
     *         configured minimum score
     * @since 0.19
     */
    public boolean isConfluent(final int index) {
        final Num score = getValue(index);
        if (score == null || score.isNaN()) {
            return false;
        }
        return score.isGreaterThanOrEqual(minimumScore);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(Math.max(priceIndicator.getCountOfUnstableBars(), ratioIndicator.getCountOfUnstableBars()),
                channelIndicator.getCountOfUnstableBars());
    }
}
