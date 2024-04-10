package org.ta4j.core;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A builder to build a new {@code BaseBar} with conversion from a
 * {@link Number} of type {@code T} to a {@link Num Num implementation}.
 */
public class BaseBarConvertibleBuilder extends BaseBarBuilder {

    private final NumFactory numFactory;

    public BaseBarConvertibleBuilder(final NumFactory numFactory) {
        this.numFactory = numFactory;
    }

    @Override
    public BaseBarConvertibleBuilder timePeriod(final Duration timePeriod) {
        super.timePeriod(timePeriod);
        return this;
    }

    @Override
    public BaseBarConvertibleBuilder endTime(final ZonedDateTime endTime) {
        super.endTime(endTime);
        return this;
    }

    @Override
    public BaseBarConvertibleBuilder trades(final long trades) {
        super.trades(trades);
        return this;
    }

    public BaseBarConvertibleBuilder trades(final String trades) {
        super.trades(Long.parseLong(trades));
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder openPrice(final Number openPrice) {
        super.openPrice(numFactory.numOf(openPrice));
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder openPrice(final String openPrice) {
        super.openPrice(numFactory.numOf(openPrice));
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder highPrice(final Number highPrice) {
        super.highPrice(numFactory.numOf(highPrice));
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder highPrice(final String highPrice) {
        super.highPrice(numFactory.numOf(highPrice));
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder lowPrice(final Number lowPrice) {
        super.lowPrice(numFactory.numOf(lowPrice));
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder lowPrice(final String lowPrice) {
        super.lowPrice(numFactory.numOf(lowPrice));
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder closePrice(final Number closePrice) {
        super.closePrice(numFactory.numOf(closePrice));
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder closePrice(final String closePrice) {
        super.closePrice(numFactory.numOf(closePrice));
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder volume(final Number volume) {
        super.volume(numFactory.numOf(volume));
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder volume(final String volume) {
        super.volume(numFactory.numOf(volume));
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder amount(final Number amount) {
        super.amount(numFactory.numOf(amount));
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder amount(final String amount) {
        super.amount(numFactory.numOf(amount));
        return this;
    }

    @Override
    public BaseBarConvertibleBuilder bindTo(final BarSeries baseBarSeries) {
        super.bindTo(baseBarSeries);
        return this;
    }
}
