package org.ta4j.core;

import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.function.Function;

public class ConvertibleBaseBarBuilder<T> extends BaseBarBuilder {

    private final Function<T, Num> conversionFunction;

    public ConvertibleBaseBarBuilder(Function<T, Num> conversionFunction) {
        this.conversionFunction = conversionFunction;
    }

    @Override
    public ConvertibleBaseBarBuilder<T> timePeriod(Duration timePeriod) {
        super.timePeriod(timePeriod);
        return this;
    }

    @Override
    public ConvertibleBaseBarBuilder<T> endTime(ZonedDateTime endTime) {
        super.endTime(endTime);
        return this;
    }

    @Override
    public ConvertibleBaseBarBuilder<T> trades(int trades) {
        super.trades(trades);
        return this;
    }

    public ConvertibleBaseBarBuilder<T> openPrice(T openPrice) {
        super.openPrice(conversionFunction.apply(openPrice));
        return this;
    }

    public ConvertibleBaseBarBuilder<T> highPrice(T highPrice) {
        super.highPrice(conversionFunction.apply(highPrice));
        return this;
    }

    public ConvertibleBaseBarBuilder<T> lowPrice(T lowPrice) {
        super.lowPrice(conversionFunction.apply(lowPrice));
        return this;
    }

    public ConvertibleBaseBarBuilder<T> closePrice(T closePrice) {
        super.closePrice(conversionFunction.apply(closePrice));
        return this;
    }

    public ConvertibleBaseBarBuilder<T> amount(T amount) {
        super.amount(conversionFunction.apply(amount));
        return this;
    }

    public ConvertibleBaseBarBuilder<T> volume(T volume) {
        super.volume(conversionFunction.apply(volume));
        return this;
    }
}
