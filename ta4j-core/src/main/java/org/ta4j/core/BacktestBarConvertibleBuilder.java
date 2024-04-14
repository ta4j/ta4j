package org.ta4j.core;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.ta4j.core.backtest.BacktestBarBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A builder to build a new {@code BacktestBar} with conversion from a
 * {@link Number} of type {@code T} to a {@link Num Num implementation}.
 */
public class BacktestBarConvertibleBuilder extends BacktestBarBuilder {

    private final NumFactory numFactory;

    public BacktestBarConvertibleBuilder(final NumFactory numFactory) {
        this.numFactory = numFactory;
    }

    @Override
    public BacktestBarConvertibleBuilder timePeriod(final Duration timePeriod) {
        super.timePeriod(timePeriod);
        return this;
    }

    @Override
    public BacktestBarConvertibleBuilder endTime(final ZonedDateTime endTime) {
        super.endTime(endTime);
        return this;
    }

    @Override
    public BacktestBarConvertibleBuilder trades(final long trades) {
        super.trades(trades);
        return this;
    }

    public BacktestBarConvertibleBuilder trades(final String trades) {
        super.trades(Long.parseLong(trades));
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder openPrice(final Number openPrice) {
        super.openPrice(numFactory.numOf(openPrice));
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder openPrice(final String openPrice) {
        super.openPrice(numFactory.numOf(openPrice));
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder highPrice(final Number highPrice) {
        super.highPrice(numFactory.numOf(highPrice));
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder highPrice(final String highPrice) {
        super.highPrice(numFactory.numOf(highPrice));
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder lowPrice(final Number lowPrice) {
        super.lowPrice(numFactory.numOf(lowPrice));
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder lowPrice(final String lowPrice) {
        super.lowPrice(numFactory.numOf(lowPrice));
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder closePrice(final Number closePrice) {
        super.closePrice(numFactory.numOf(closePrice));
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder closePrice(final String closePrice) {
        super.closePrice(numFactory.numOf(closePrice));
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder volume(final Number volume) {
        super.volume(numFactory.numOf(volume));
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder volume(final String volume) {
        super.volume(numFactory.numOf(volume));
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder amount(final Number amount) {
        super.amount(numFactory.numOf(amount));
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarConvertibleBuilder amount(final String amount) {
        super.amount(numFactory.numOf(amount));
        return this;
    }

    @Override
    public BacktestBarConvertibleBuilder bindTo(final BarSeries baseBarSeries) {
        super.bindTo(baseBarSeries);
        return this;
    }
}
