package org.ta4j.core;

import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class BaseTimeSeriesBuilder implements TimeSeriesBuilder {

    private static final long serialVersionUID = 111164611841087550L;
    /**
     * Default Num type function
     **/
    private static Function<Number, Num> defaultFunction = PrecisionNum::valueOf;
    private List<Bar> bars;
    private String name;
    private Function<Number, Num> numFunction;
    private boolean constrained;
    private int maxBarCount;

    public BaseTimeSeriesBuilder() {
        initValues();
    }

    public static void setDefaultFunction(Function<Number, Num> defaultFunction) {
        BaseTimeSeriesBuilder.defaultFunction = defaultFunction;
    }

    private void initValues() {
        this.bars = new ArrayList<>();
        this.name = "unnamed_series";
        this.numFunction = BaseTimeSeriesBuilder.defaultFunction;
        this.constrained = false;
        this.maxBarCount = Integer.MAX_VALUE;
    }

    @Override
    public TimeSeries build() {
        int beginIndex = -1;
        int endIndex = -1;
        if (!bars.isEmpty()) {
            beginIndex = 0;
            endIndex = bars.size() - 1;
        }
        TimeSeries series = new BaseTimeSeries(name, bars, beginIndex, endIndex, constrained, numFunction);
        series.setMaximumBarCount(maxBarCount);
        initValues(); // reinitialize values for next series
        return series;
    }

    public BaseTimeSeriesBuilder setConstrained(boolean constrained) {
        this.constrained = constrained;
        return this;
    }

    public BaseTimeSeriesBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public BaseTimeSeriesBuilder withBars(List<Bar> bars) {
        this.bars = bars;
        return this;
    }

    public BaseTimeSeriesBuilder withMaxBarCount(int maxBarCount) {
        this.maxBarCount = maxBarCount;
        return this;
    }

    public BaseTimeSeriesBuilder withNumTypeOf(Num type) {
        numFunction = type.function();
        return this;
    }

    public BaseTimeSeriesBuilder withNumTypeOf(Function<Number, Num> function) {
        numFunction = function;
        return this;
    }

    public BaseTimeSeriesBuilder withNumTypeOf(Class<? extends Num> abstractNumClass) {
        if (abstractNumClass == PrecisionNum.class) {
            numFunction = PrecisionNum::valueOf;
            return this;
        } else if (abstractNumClass == DoubleNum.class) {
            numFunction = DoubleNum::valueOf;
            return this;
        }
        numFunction = PrecisionNum::valueOf;
        return this;
    }

}
