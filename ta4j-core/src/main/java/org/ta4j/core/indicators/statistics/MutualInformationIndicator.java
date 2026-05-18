/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Rolling mutual information indicator.
 *
 * <p>
 * Mutual information measures how much observing one windowed variable reduces
 * uncertainty about the other. This v1 implementation discretizes both numeric
 * windows into equal-width bins and reports natural-log mutual information in
 * nats. Constant windows have zero mutual information because they contain no
 * uncertainty to reduce.
 * </p>
 *
 * <p>
 * <b>Binning note:</b> Equal-width bins are intentionally fixed for this
 * initial API. Skewed or heavy-tailed financial series can place most samples
 * in only a few bins, so interpret the output as discretized mutual information
 * for the configured bins rather than as a continuous mutual-information
 * estimator.
 * </p>
 *
 * @since 0.22.7
 */
public class MutualInformationIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> first;
    private final Indicator<Num> second;
    private final int barCount;
    private final int binCount;

    /**
     * Constructor.
     *
     * @param first    first numeric indicator
     * @param second   second numeric indicator
     * @param barCount rolling window length, must be at least 2
     * @param binCount number of equal-width bins, must be at least 2; larger values
     *                 increase resolution but can produce sparse bins on small or
     *                 skewed windows
     * @throws IllegalArgumentException if {@code barCount < 2},
     *                                  {@code binCount < 2}, or indicators use
     *                                  different series
     * @throws NullPointerException     if an indicator is null
     * @since 0.22.7
     */
    public MutualInformationIndicator(Indicator<Num> first, Indicator<Num> second, int barCount, int binCount) {
        super(first);
        IndicatorUtils.requireSameSeries(first, second);
        this.first = first;
        this.second = second;
        this.barCount = CorrelationWindowSupport.validateBarCount(barCount);
        this.binCount = CorrelationWindowSupport.validateBinCount(binCount);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        CorrelationWindowSupport.NumericWindow window = CorrelationWindowSupport.pairedWindow(first, second, index,
                barCount);
        if (window == null) {
            return NaN.NaN;
        }

        NumFactory numFactory = getBarSeries().numFactory();
        BinMapper firstMapper = BinMapper.from(window.firstValues(), window.sampleCount(), binCount);
        BinMapper secondMapper = BinMapper.from(window.secondValues(), window.sampleCount(), binCount);
        if (firstMapper.constant || secondMapper.constant) {
            return numFactory.zero();
        }

        int[] jointCounts = new int[binCount * binCount];
        int[] firstCounts = new int[binCount];
        int[] secondCounts = new int[binCount];
        for (int i = 0; i < window.sampleCount(); i++) {
            int firstBin = firstMapper.bin(window.firstValues()[i]);
            int secondBin = secondMapper.bin(window.secondValues()[i]);
            jointCounts[(firstBin * binCount) + secondBin]++;
            firstCounts[firstBin]++;
            secondCounts[secondBin]++;
        }

        Num sampleCount = numFactory.numOf(window.sampleCount());
        Num mutualInformation = numFactory.zero();
        for (int firstBin = 0; firstBin < binCount; firstBin++) {
            for (int secondBin = 0; secondBin < binCount; secondBin++) {
                int jointCount = jointCounts[(firstBin * binCount) + secondBin];
                if (jointCount == 0) {
                    continue;
                }
                Num jointProbability = numFactory.numOf(jointCount).dividedBy(sampleCount);
                Num firstProbability = numFactory.numOf(firstCounts[firstBin]).dividedBy(sampleCount);
                Num secondProbability = numFactory.numOf(secondCounts[secondBin]).dividedBy(sampleCount);
                Num ratio = jointProbability.dividedBy(firstProbability.multipliedBy(secondProbability));
                mutualInformation = mutualInformation.plus(jointProbability.multipliedBy(ratio.log()));
            }
        }
        return CorrelationWindowSupport.isFinite(mutualInformation) ? mutualInformation : NaN.NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.unstableBars(barCount, first, second);
    }

    private static final class BinMapper {

        private final Num minimum;
        private final Num width;
        private final int binCount;
        private final boolean constant;

        private BinMapper(Num minimum, Num width, int binCount, boolean constant) {
            this.minimum = minimum;
            this.width = width;
            this.binCount = binCount;
            this.constant = constant;
        }

        static BinMapper from(Num[] values, int sampleCount, int binCount) {
            Num minimum = values[0];
            Num maximum = values[0];
            for (int i = 1; i < sampleCount; i++) {
                minimum = minimum.min(values[i]);
                maximum = maximum.max(values[i]);
            }
            if (minimum.compareTo(maximum) == 0) {
                return new BinMapper(minimum, minimum.getNumFactory().zero(), binCount, true);
            }
            return new BinMapper(minimum, maximum.minus(minimum).dividedBy(minimum.getNumFactory().numOf(binCount)),
                    binCount, false);
        }

        int bin(Num value) {
            int bin = value.minus(minimum).dividedBy(width).intValue();
            if (bin < 0) {
                return 0;
            }
            if (bin >= binCount) {
                return binCount - 1;
            }
            return bin;
        }
    }
}
