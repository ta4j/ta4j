package org.ta4j.core;

import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.ZonedDateTime;

public class BaseBarBuilder {

    private Duration timePeriod;
    private ZonedDateTime endTime;
    private Num openPrice;
    private Num closePrice;
    private Num highPrice;
    private Num lowPrice;
    private Num amount;
    private Num volume;
    private int trades;

    BaseBarBuilder() {
    }

    public BaseBarBuilder timePeriod(Duration timePeriod) {
        this.timePeriod = timePeriod;
        return this;
    }

    public BaseBarBuilder endTime(ZonedDateTime endTime) {
        this.endTime = endTime;
        return this;
    }

    public BaseBarBuilder openPrice(Num openPrice) {
        this.openPrice = openPrice;
        return this;
    }

    public BaseBarBuilder closePrice(Num closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    public BaseBarBuilder highPrice(Num highPrice) {
        this.highPrice = highPrice;
        return this;
    }

    public BaseBarBuilder lowPrice(Num lowPrice) {
        this.lowPrice = lowPrice;
        return this;
    }

    public BaseBarBuilder amount(Num amount) {
        this.amount = amount;
        return this;
    }

    public BaseBarBuilder volume(Num volume) {
        this.volume = volume;
        return this;
    }

    public BaseBarBuilder trades(int trades) {
        this.trades = trades;
        return this;
    }

    public BaseBar build() {
        return new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
    }
}
