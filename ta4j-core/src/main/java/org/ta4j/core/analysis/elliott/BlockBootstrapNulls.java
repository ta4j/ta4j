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
 * <p>
 * The generator resamples contiguous blocks of observed log returns on a
 * circular return tape. Reconstructing prices from those returns preserves the
 * observed return scale and local dependence while retaining the original bar
 * timestamps and non-price fields. The source series is never mutated.
 * </p>
 */
final class BlockBootstrapNulls {

    private static final long SEED_MULTIPLIER = 1_000_003L;

    private BlockBootstrapNulls() {
    }

    /**
     * Generates a deterministic ensemble for one expected block length.
     *
     * @param source       source price series
     * @param blockLength  expected block length in bars
     * @param ensembleSize number of null series
     * @param seed         stable ensemble seed
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
            // The ratio stays in the active Num domain so DecimalNum closes
            // beyond double range keep a finite ratio; only the bounded value
            // handed to Math.log narrows to double.
            final Num previous = source.getBar(source.getBeginIndex() + offset - 1).getClosePrice();
            final Num current = source.getBar(source.getBeginIndex() + offset).getClosePrice();
            if (!previous.isPositive() || !current.isPositive()) {
                throw new IllegalArgumentException("bootstrap source close prices must be positive");
            }
            returns[offset - 1] = Math.log(current.dividedBy(previous).doubleValue());
        }
        return returns;
    }

    private static BarSeries generateMember(final BarSeries source, final double[] logReturns, final int blockLength,
            final long seed, final int ensembleIndex) {
        final int count = source.getBarCount();
        final SplittableRandom random = new SplittableRandom(seed);
        // Member prices are reconstructed entirely in the active Num domain so
        // DecimalNum sources beyond double range neither overflow nor lose
        // high-precision return variation; only the bounded values passed to
        // Math.exp/Math.log narrow to double.
        final Num[] closes = new Num[count];
        closes[0] = source.getBar(source.getBeginIndex()).getClosePrice();

        final NumFactory numFactory = source.numFactory();
        final BarSeries result = new BaseBarSeriesBuilder().withName(source.getName() + "-null-" + ensembleIndex)
                .withNumFactory(numFactory)
                .build();
        // Intrabar shape travels WITH its resampled return: bar k of a member
        // carries the OHLC ratios of the source bar whose close-to-close return
        // was drawn for k, never the ratios at the same chronological position.
        // Otherwise every member would inherit the real series' wick sequence.
        final int[] shapePositions = new int[count];
        int tapePosition = random.nextInt(logReturns.length);
        for (int offset = 1; offset < count; offset++) {
            if (offset > 1 && random.nextInt(blockLength) == 0) {
                tapePosition = random.nextInt(logReturns.length);
            }
            closes[offset] = closes[offset - 1].multipliedBy(numFactory.numOf(Math.exp(logReturns[tapePosition])));
            shapePositions[offset] = tapePosition + 1;
            tapePosition = (tapePosition + 1) % logReturns.length;
        }
        for (int offset = 0; offset < count; offset++) {
            // Timeline stays chronological; only the intrabar shape follows the
            // sampled return's source bar.
            final Bar timelineBar = source.getBar(source.getBeginIndex() + offset);
            final Bar shapeBar = offset == 0 ? timelineBar
                    : source.getBar(source.getBeginIndex() + shapePositions[offset]);
            final Num close = closes[offset];
            final Num sourceClose = shapeBar.getClosePrice();
            final Num open = scaled(shapeBar.getOpenPrice(), sourceClose, close);
            Num high = scaled(shapeBar.getHighPrice(), sourceClose, close);
            Num low = scaled(shapeBar.getLowPrice(), sourceClose, close);
            if (high == null || high.isLessThan(close)) {
                high = close;
            }
            if (low == null || low.isGreaterThan(close)) {
                low = close;
            }
            final Num volume = timelineBar.getVolume() == null ? numFactory.zero() : timelineBar.getVolume();
            final Num amount = timelineBar.getAmount() == null ? numFactory.zero() : timelineBar.getAmount();
            final BaseBar nullBar = new BaseBar(timelineBar.getTimePeriod(), timelineBar.getBeginTime(),
                    timelineBar.getEndTime(), open, high, low, close, volume, amount, timelineBar.getTrades());
            result.addBar(nullBar);
        }
        return result;
    }

    private static Num scaled(final Num value, final Num sourceClose, final Num close) {
        if (value == null || !sourceClose.isPositive()) {
            return close;
        }
        return close.multipliedBy(value).dividedBy(sourceClose);
    }
}
