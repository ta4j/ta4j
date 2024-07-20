package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

public class TTMSqueezeProIndicator extends CachedIndicator<Num> {

    private final ClosePriceIndicator closePriceIndicator;
    private final HighPriceIndicator highPriceIndicator;
    private final LowPriceIndicator lowPriceIndicator;
    private final TypicalPriceIndicator typicalPriceIndicator;
    private final int length;
    private final Num numDevDn;
    private final Num numDevUp;
    private final Num factorHigh;
    private final Num factorMid;
    private final Num factorLow;
    private final TRIndicator trueRangeIndicator;
    private final StandardDeviationIndicator stDevIndicator;
    private final CachedIndicator<Num> midLineBB;
    private final CachedIndicator<Num> upperBandBB;
    private final CachedIndicator<Num> lowerBandBB;
    private final CachedIndicator<Num> shiftHigh;
    private final CachedIndicator<Num> shiftMid;
    private final CachedIndicator<Num> shiftLow;
    private final CachedIndicator<Num> average;
    private final CachedIndicator<Num> upperBandKCLow;
    private final CachedIndicator<Num> lowerBandKCLow;
    private final CachedIndicator<Num> upperBandKCMid;
    private final CachedIndicator<Num> lowerBandKCMid;
    private final CachedIndicator<Num> upperBandKCHigh;
    private final CachedIndicator<Num> lowerBandKCHigh;

    public TTMSqueezeProIndicator(BarSeries series, int length, double numDevDn, double numDevUp, double factorHigh, double factorMid, double factorLow) {
        super(series);
        this.closePriceIndicator = new ClosePriceIndicator(series);
        this.highPriceIndicator = new HighPriceIndicator(series);
        this.lowPriceIndicator = new LowPriceIndicator(series);
        this.typicalPriceIndicator = new TypicalPriceIndicator(series);
        this.length = length;
        this.numDevDn = numOf(numDevDn);
        this.numDevUp = numOf(numDevUp);
        this.factorHigh = numOf(factorHigh);
        this.factorMid = numOf(factorMid);
        this.factorLow = numOf(factorLow);
        this.trueRangeIndicator = new TRIndicator(series);
        this.stDevIndicator = new StandardDeviationIndicator(closePriceIndicator, length);
        this.midLineBB = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return average.getValue(index);
            }
        };
        this.upperBandBB = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return midLineBB.getValue(index).plus(stDevIndicator.getValue(index).multipliedBy(numDevUp));
            }
        };
        this.lowerBandBB = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return midLineBB.getValue(index).plus(stDevIndicator.getValue(index).multipliedBy(numDevDn));
            }
        };
        this.average = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return closePriceIndicator.getValue(index).dividedBy(numOf(length));
            }
        };
        this.shiftHigh = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return factorHigh.multipliedBy(average.getValue(index));
            }
        };
        this.shiftMid = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return factorMid.multipliedBy(average.getValue(index));
            }
        };
        this.shiftLow = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return factorLow.multipliedBy(average.getValue(index));
            }
        };
        this.upperBandKCLow = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return average.getValue(index).plus(shiftLow.getValue(index));
            }
        };
        this.lowerBandKCLow = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return average.getValue(index).minus(shiftLow.getValue(index));
            }
        };
        this.upperBandKCMid = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return average.getValue(index).plus(shiftMid.getValue(index));
            }
        };
        this.lowerBandKCMid = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return average.getValue(index).minus(shiftMid.getValue(index));
            }
        };
        this.upperBandKCHigh = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return average.getValue(index).plus(shiftHigh.getValue(index));
            }
        };
        this.lowerBandKCHigh = new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                return average.getValue(index).minus(shiftHigh.getValue(index));
            }
        };
    }

    @Override
    protected Num calculate(int index) {
        boolean presqueeze = lowerBandBB.getValue(index).isGreaterThan(lowerBandKCLow.getValue(index)) && upperBandBB.getValue(index).isLessThan(upperBandKCLow.getValue(index));
        boolean originalSqueeze = lowerBandBB.getValue(index).isGreaterThan(lowerBandKCMid.getValue(index)) && upperBandBB.getValue(index).isLessThan(upperBandKCMid.getValue(index));
        boolean extrSqueeze = lowerBandBB.getValue(index).isGreaterThan(lowerBandKCHigh.getValue(index)) && upperBandBB.getValue(index).isLessThan(upperBandKCHigh.getValue(index));

        if (presqueeze || originalSqueeze || extrSqueeze) {
            return numOf(1);
        } else {
            return numOf(0);
        }
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
