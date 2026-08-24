/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Deterministic stationary-block-bootstrap null generator.
 *
 * <p>The generator resamples contiguous blocks of observed log returns on a
 * circular return tape. Reconstructing prices from those returns preserves the
 * observed return scale and local dependence while retaining the original bar
 * timestamps and non-price fields. The source series is never mutated.</p>
 */
final class BlockBootstrapNulls {

    private static final long SEED_MULTIPLIER = 1_000_003L;

    private BlockBootstrapNulls() {
    }

    /**
     * Generates a deterministic ensemble for one expected block length.
     *
     * @param source source price series
     * @param blockLength expected block length in bars
     * @param ensembleSize number of null series
     * @param seed stable ensemble seed
     * @return immutable null series in ensemble order
     */
    static List<BarSeries> generate(final BarSeries source, final int blockLength, final int ensembleSize,
            final long seed) {
        Objects.requireNonNull(source, "source");
        if (blockLength <= 0 || ensembleSize <= 0) {
            throw new IllegalArgumentException("blockLength and ensembleSize must be positive");
        }
        final int count = source.getBarCount();
        if (count < 2) {
            throw new IllegalArgumentException("stationary bootstrap requires at least two bars");
        }
        final double[] logReturns = logReturns(source);
        final List<BarSeries> generated = new ArrayList<>(ensembleSize);
        for (int ensembleIndex = 0; ensembleIndex < ensembleSize; ensembleIndex++) {
            final long memberSeed = seed * SEED_MULTIPLIER + ensembleIndex;
            generated.add(generateMember(source, logReturns, blockLength, memberSeed, ensembleIndex));
        }
        return List.copyOf(generated);
    }

    private static double[] logReturns(final BarSeries source) {
        final int count = source.getBarCount();
        final double[] returns = new double[count - 1];
        for (int offset = 1; offset < count; offset++) {
            final double previous = source.getBar(source.getBeginIndex() + offset - 1).getClosePrice().doubleValue();
            final double current = source.getBar(source.getBeginIndex() + offset).getClosePrice().doubleValue();
            if (!(previous > 0.0d) || !(current > 0.0d)) {
                throw new IllegalArgumentException("bootstrap source close prices must be positive");
            }
            returns[offset - 1] = Math.log(current / previous);
        }
        return returns;
    }

    private static BarSeries generateMember(final BarSeries source, final double[] logReturns, final int blockLength,
            final long seed, final int ensembleIndex) {
        final int count = source.getBarCount();
        final SplittableRandom random = new SplittableRandom(seed);
        final double[] closes = new double[count];
        closes[0] = source.getBar(source.getBeginIndex()).getClosePrice().doubleValue();
        int tapePosition = random.nextInt(logReturns.length);
        for (int offset = 1; offset < count; offset++) {
            if (offset > 1 && random.nextInt(blockLength) == 0) {
                tapePosition = random.nextInt(logReturns.length);
            }
            final double sampledReturn = logReturns[tapePosition];
            closes[offset] = closes[offset - 1] * Math.exp(sampledReturn);
            tapePosition = (tapePosition + 1) % logReturns.length;
        }

        final NumFactory numFactory = source.numFactory();
        final BarSeries result = new BaseBarSeriesBuilder().withName(source.getName() + "-null-" + ensembleIndex)
                .withNumFactory(numFactory).build();
        for (int offset = 0; offset < count; offset++) {
            final Bar sourceBar = source.getBar(source.getBeginIndex() + offset);
            final double close = closes[offset];
            final double sourceClose = sourceBar.getClosePrice().doubleValue();
            final double open = scaled(sourceBar.getOpenPrice(), sourceClose, close);
            final double high = scaled(sourceBar.getHighPrice(), sourceClose, close);
            final double low = scaled(sourceBar.getLowPrice(), sourceClose, close);
            final Num volume = sourceBar.getVolume() == null ? numFactory.zero() : sourceBar.getVolume();
            final Num amount = sourceBar.getAmount() == null ? numFactory.zero() : sourceBar.getAmount();
            final BaseBar nullBar = new BaseBar(sourceBar.getTimePeriod(), sourceBar.getBeginTime(),
                    sourceBar.getEndTime(), numFactory.numOf(open), numFactory.numOf(Math.max(high, close)),
                    numFactory.numOf(Math.min(low, close)), numFactory.numOf(close), volume, amount,
                    sourceBar.getTrades());
            result.addBar(nullBar);
        }
        return result;
    }

    private static double scaled(final Num value, final double sourceClose, final double close) {
        if (value == null || !(sourceClose > 0.0d)) {
            return close;
        }
        return close * value.doubleValue() / sourceClose;
    }
}
