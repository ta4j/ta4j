/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Thread-safe {@link BarSeries} implementation for concurrent read/write use
 * cases.
 *
 * @since 0.19
 */
public class ConcurrentBarSeries extends BaseBarSeries {

    private static final long serialVersionUID = -1868546230609071876L;

    private final Lock readLock;
    private final Lock writeLock;

    ConcurrentBarSeries(final String name, final List<Bar> bars, final int seriesBeginIndex, final int seriesEndIndex,
            final boolean constrained, final NumFactory numFactory, final BarBuilderFactory barBuilderFactory) {
        this(name, bars, seriesBeginIndex, seriesEndIndex, constrained, numFactory, barBuilderFactory,
                new ReentrantReadWriteLock());
    }

    ConcurrentBarSeries(final String name, final List<Bar> bars, final int seriesBeginIndex, final int seriesEndIndex,
            final boolean constrained, final NumFactory numFactory, final BarBuilderFactory barBuilderFactory,
            final ReadWriteLock readWriteLock) {
        super(name, bars, seriesBeginIndex, seriesEndIndex, constrained, numFactory, barBuilderFactory);
        ReadWriteLock rwLock = Objects.requireNonNull(readWriteLock, "readWriteLock");
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
    }

    private static List<Bar> cut(final List<Bar> bars, final int startIndex, final int endIndex) {
        return new ArrayList<>(bars.subList(startIndex, endIndex));
    }

    @Override
    public ConcurrentBarSeries getSubSeries(final int startIndex, final int endIndex) {
        this.readLock.lock();
        try {
            if (startIndex < 0) {
                throw new IllegalArgumentException(
                        String.format("the startIndex: %s must not be negative", startIndex));
            }
            if (startIndex >= endIndex) {
                throw new IllegalArgumentException(
                        String.format("the endIndex: %s must be greater than startIndex: %s", endIndex, startIndex));
            }
            final List<Bar> bars = super.getBarData();
            if (!bars.isEmpty()) {
                final int start = startIndex - super.getRemovedBarsCount();
                final int end = Math.min(endIndex - super.getRemovedBarsCount(), super.getEndIndex() + 1);
                return new ConcurrentBarSeriesBuilder().withName(getName())
                        .withBars(cut(bars, start, end))
                        .withNumFactory(super.numFactory())
                        .build();
            }
            return new ConcurrentBarSeriesBuilder().withNumFactory(super.numFactory()).withName(getName()).build();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public BarBuilder barBuilder() {
        this.readLock.lock();
        try {
            return super.barBuilder();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public Bar getBar(final int i) {
        this.readLock.lock();
        try {
            return super.getBar(i);
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public int getBarCount() {
        this.readLock.lock();
        try {
            return super.getBarCount();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public List<Bar> getBarData() {
        this.readLock.lock();
        try {
            return List.copyOf(super.getBarData());
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public int getBeginIndex() {
        this.readLock.lock();
        try {
            return super.getBeginIndex();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public int getEndIndex() {
        this.readLock.lock();
        try {
            return super.getEndIndex();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public int getMaximumBarCount() {
        this.readLock.lock();
        try {
            return super.getMaximumBarCount();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public void setMaximumBarCount(final int maximumBarCount) {
        this.writeLock.lock();
        try {
            super.setMaximumBarCount(maximumBarCount);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public int getRemovedBarsCount() {
        this.readLock.lock();
        try {
            return super.getRemovedBarsCount();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public void addBar(final Bar bar, final boolean replace) {
        this.writeLock.lock();
        try {
            super.addBar(bar, replace);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public void addTrade(final Number tradeVolume, final Number tradePrice) {
        this.writeLock.lock();
        try {
            super.addTrade(tradeVolume, tradePrice);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public void addTrade(final Num tradeVolume, final Num tradePrice) {
        this.writeLock.lock();
        try {
            super.addTrade(tradeVolume, tradePrice);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public void addPrice(final Num price) {
        this.writeLock.lock();
        try {
            super.addPrice(price);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public String getSeriesPeriodDescription() {
        this.readLock.lock();
        try {
            return super.getSeriesPeriodDescription();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public String getSeriesPeriodDescriptionInSystemTimeZone() {
        this.readLock.lock();
        try {
            return super.getSeriesPeriodDescriptionInSystemTimeZone();
        } finally {
            this.readLock.unlock();
        }
    }
}
