/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

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
     * @param binCount number of equal-width bins, must be at least 2
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
        double[][] window = CorrelationWindowSupport.pairedWindow(first, second, index, barCount);
        if (window == null) {
            return NaN.NaN;
        }

        BinMapper firstMapper = BinMapper.from(window[0], binCount);
        BinMapper secondMapper = BinMapper.from(window[1], binCount);
        if (firstMapper.constant || secondMapper.constant) {
            return getBarSeries().numFactory().zero();
        }

        int[][] jointCounts = new int[binCount][binCount];
        int[] firstCounts = new int[binCount];
        int[] secondCounts = new int[binCount];
        for (int i = 0; i < barCount; i++) {
            int firstBin = firstMapper.bin(window[0][i]);
            int secondBin = secondMapper.bin(window[1][i]);
            jointCounts[firstBin][secondBin]++;
            firstCounts[firstBin]++;
            secondCounts[secondBin]++;
        }

        double sampleCount = barCount;
        double mutualInformation = 0.0;
        for (int firstBin = 0; firstBin < binCount; firstBin++) {
            for (int secondBin = 0; secondBin < binCount; secondBin++) {
                int jointCount = jointCounts[firstBin][secondBin];
                if (jointCount == 0) {
                    continue;
                }
                double jointProbability = jointCount / sampleCount;
                double firstProbability = firstCounts[firstBin] / sampleCount;
                double secondProbability = secondCounts[secondBin] / sampleCount;
                mutualInformation += jointProbability
                        * Math.log(jointProbability / (firstProbability * secondProbability));
            }
        }
        return getBarSeries().numFactory().numOf(mutualInformation);
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.unstableBars(barCount, first, second);
    }

    private static final class BinMapper {

        private final double minimum;
        private final double width;
        private final int binCount;
        private final boolean constant;

        private BinMapper(double minimum, double width, int binCount, boolean constant) {
            this.minimum = minimum;
            this.width = width;
            this.binCount = binCount;
            this.constant = constant;
        }

        static BinMapper from(double[] values, int binCount) {
            double minimum = values[0];
            double maximum = values[0];
            for (double value : values) {
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
            if (Double.compare(minimum, maximum) == 0) {
                return new BinMapper(minimum, 0.0, binCount, true);
            }
            return new BinMapper(minimum, (maximum - minimum) / binCount, binCount, false);
        }

        int bin(double value) {
            int bin = (int) ((value - minimum) / width);
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
