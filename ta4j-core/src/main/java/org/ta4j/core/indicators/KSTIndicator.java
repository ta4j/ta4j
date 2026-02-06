/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Know Sure Thing (KST) indicator.
 *
 * <pre>
 * RCMA1 = X1-Period SMA of Y1-Period Rate-of-Change
 * RCMA2 = X2-Period SMA of Y2-Period Rate-of-Change
 * RCMA3 = X3-Period SMA of Y3-Period Rate-of-Change
 * RCMA4 = X4-Period SMA of Y4-Period Rate-of-Change
 *
 * KST = (RCMA1 x 1) + (RCMA2 x 2) + (RCMA3 x 3) + (RCMA4 x 4)
 * </pre>
 *
 * @see <a href=
 *      "https://school.stockcharts.com/doku.php?id=technical_indicators:know_sure_thing_kst">
 *      https://school.stockcharts.com/doku.php?id=technical_indicators:know_sure_thing_kst
 *      </a>
 */
public class KSTIndicator extends CachedIndicator<Num> {

    private final SMAIndicator RCMA1;
    private final SMAIndicator RCMA2;
    private final SMAIndicator RCMA3;
    private final SMAIndicator RCMA4;
    private final Num RCMA1_Multiplier = getBarSeries().numFactory().one();
    private final Num RCMA2_Multiplier = getBarSeries().numFactory().numOf(2);
    private final Num RCMA3_Multiplier = getBarSeries().numFactory().numOf(3);
    private final Num RCMA4_Multiplier = getBarSeries().numFactory().numOf(4);

    /**
     * Constructor with:
     *
     * <ul>
     * <li>RCMA1 = 10-Period SMA of 10-Period Rate-of-Change
     * <li>RCMA2 = 10-Period SMA of 15-Period Rate-of-Change
     * <li>RCMA3 = 10-Period SMA of 20-Period Rate-of-Change
     * <li>RCMA4 = 15-Period SMA of 30-Period Rate-of-Change
     * </ul>
     *
     * @param indicator the {@link Indicator}
     */
    public KSTIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.RCMA1 = new SMAIndicator(new ROCIndicator(indicator, 10), 10);
        this.RCMA2 = new SMAIndicator(new ROCIndicator(indicator, 15), 10);
        this.RCMA3 = new SMAIndicator(new ROCIndicator(indicator, 20), 10);
        this.RCMA4 = new SMAIndicator(new ROCIndicator(indicator, 30), 15);
    }

    /**
     * Constructor.
     *
     * @param indicator        the indicator.
     * @param rcma1SMABarCount RCMA1 SMA period.
     * @param rcma1ROCBarCount RCMA1 ROC period.
     * @param rcma2SMABarCount RCMA2 SMA period.
     * @param rcma2ROCBarCount RCMA2 ROC period.
     * @param rcma3SMABarCount RCMA3 SMA period.
     * @param rcma3ROCBarCount RCMA3 ROC period.
     * @param rcma4SMABarCount RCMA4 SMA period.
     * @param rcma4ROCBarCount RCMA4 ROC period.
     */
    public KSTIndicator(Indicator<Num> indicator, int rcma1SMABarCount, int rcma1ROCBarCount, int rcma2SMABarCount,
            int rcma2ROCBarCount, int rcma3SMABarCount, int rcma3ROCBarCount, int rcma4SMABarCount,
            int rcma4ROCBarCount) {
        super(indicator);
        this.RCMA1 = new SMAIndicator(new ROCIndicator(indicator, rcma1ROCBarCount), rcma1SMABarCount);
        this.RCMA2 = new SMAIndicator(new ROCIndicator(indicator, rcma2ROCBarCount), rcma2SMABarCount);
        this.RCMA3 = new SMAIndicator(new ROCIndicator(indicator, rcma3ROCBarCount), rcma3SMABarCount);
        this.RCMA4 = new SMAIndicator(new ROCIndicator(indicator, rcma4ROCBarCount), rcma4SMABarCount);
    }

    @Override
    protected Num calculate(int index) {
        return ((RCMA1.getValue(index).multipliedBy(RCMA1_Multiplier))
                .plus(RCMA2.getValue(index).multipliedBy(RCMA2_Multiplier))
                .plus(RCMA3.getValue(index).multipliedBy(RCMA3_Multiplier))
                .plus(RCMA4.getValue(index).multipliedBy(RCMA4_Multiplier)));
    }

    @Override
    public int getCountOfUnstableBars() {
        int firstPair = Math.max(RCMA1.getCountOfUnstableBars(), RCMA2.getCountOfUnstableBars());
        int secondPair = Math.max(RCMA3.getCountOfUnstableBars(), RCMA4.getCountOfUnstableBars());
        return Math.max(firstPair, secondPair);
    }
}
