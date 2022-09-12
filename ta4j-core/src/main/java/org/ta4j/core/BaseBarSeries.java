/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import static org.ta4j.core.num.NaN.NaN;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link BarSeries}.
 * * {@link BarSeries} 的基本实现。
 * </p>
 */
public class BaseBarSeries implements BarSeries {

    private static final long serialVersionUID = -1878027009398790126L;
    /**
     * Name for unnamed series
     * * 未命名系列的名称
     */
    private static final String UNNAMED_SERIES_NAME = "unnamed_series";
    /**
     * Num type function
     * * 数字类型函数
     **/
    protected final transient Function<Number, Num> numFunction;
    /**
     * The logger
     * * 记录器
     */
    private final transient Logger log = LoggerFactory.getLogger(getClass());
    /**
     * Name of the series
     * * 系列名称
     */
    private final String name;
    /**
     * List of bars
     * * 酒吧列表
     */
    private final List<Bar> bars;
    /**
     * Begin index of the bar series
     * * 柱系列的开始索引
     */
    private int seriesBeginIndex;
    /**
     * End index of the bar series
     * * 条形系列的结束索引
     */
    private int seriesEndIndex;
    /**
     * Maximum number of bars for the bar series
     * * 条形系列的最大条数
     */
    private int maximumBarCount = Integer.MAX_VALUE;
    /**
     * Number of removed bars
     * * 删除的条数
     */
    private int removedBarsCount = 0;
    /**
     * True if the current series is constrained (i.e. its indexes cannot change), false otherwise
     * * 如果当前序列受到约束（即其索引不能更改）则为真，否则为假
     */
    private boolean constrained;

    /**
     * Constructor of an unnamed series.
     * * 一个未命名系列的构造函数。
     */
    public BaseBarSeries() {
        this(UNNAMED_SERIES_NAME);
    }

    /**
     * Constructor.
     *
     * @param name the name of the series
     *             系列名称
     */
    public BaseBarSeries(String name) {
        this(name, new ArrayList<>());
    }

    /**
     * Constructor of an unnamed series.
     * 未命名系列的构造函数。
     *
     * @param bars the list of bars of the series
     *             该系列的酒吧列表
     */
    public BaseBarSeries(List<Bar> bars) {
        this(UNNAMED_SERIES_NAME, bars);
    }

    /**
     * Constructor.
     *
     * @param name the name of the series
     *             系列名称
     *
     * @param bars the list of bars of the series
     *             该系列的酒吧列表
     */
    public BaseBarSeries(String name, List<Bar> bars) {
        this(name, bars, 0, bars.size() - 1, false);
    }

    /**
     * Constructor.
     *
     * @param name        the name of the series
     *                    系列名称
     * @param numFunction a {@link Function} to convert a {@link Number} to a  {@link Num Num implementation}
     *                    * @param numFunction a {@link Function} 将 {@link Number} 转换为 {@link Num Num implementation}
     */
    public BaseBarSeries(String name, Function<Number, Num> numFunction) {
        this(name, new ArrayList<>(), numFunction);
    }

    /**
     * Constructor.
     *
     * @param name the name of the series
     *             系列名称
     *
     * @param bars the list of bars of the series
     *             该系列的酒吧列表
     */
    public BaseBarSeries(String name, List<Bar> bars, Function<Number, Num> numFunction) {
        this(name, bars, 0, bars.size() - 1, false, numFunction);
    }

    /**
     * Constructor.
     * <p/>
     * Creates a BaseBarSeries with default {@link DecimalNum} as type for the data and all operations on it
     * * 创建一个 BaseBarSeries，默认 {@link DecimalNum} 作为数据类型和所有操作
     *
     * @param name             the name of the series
     *                         系列名称
     *
     * @param bars             the list of bars of the series
     *                         该系列的酒吧列表
     *
     * @param seriesBeginIndex the begin index (inclusive) of the bar series
     *                         条形系列的开始索引（包括）
     *
     * @param seriesEndIndex   the end index (inclusive) of the bar series
     *                         bar系列的结束索引（包括）
     *
     * @param constrained      true to constrain the bar series (i.e. indexes cannot  change), false otherwise
     *                         true 约束条形系列（即索引不能改变），否则为 false
     */
    private BaseBarSeries(String name, List<Bar> bars, int seriesBeginIndex, int seriesEndIndex, boolean constrained) {
        this(name, bars, seriesBeginIndex, seriesEndIndex, constrained, DecimalNum::valueOf);
    }

    /**
     * Constructor.
     *
     * @param name             the name of the series
     *                         系列名称
     *
     * @param bars             the list of bars of the series
     *                         该系列的酒吧列表
     *
     * @param seriesBeginIndex the begin index (inclusive) of the bar series
     *                         条形系列的开始索引（包括）
     *
     * @param seriesEndIndex   the end index (inclusive) of the bar series
     *                         bar系列的结束索引（包括）
     *
     * @param constrained      true to constrain the bar series (i.e. indexes cannot  change), false otherwise
     *                         true 约束条形系列（即索引不能改变），否则为 false
     *
     * @param numFunction      a {@link Function} to convert a {@link Number} to a   {@link Num Num implementation}
     *                         * @param numFunction a {@link Function} 将 {@link Number} 转换为 {@link Num Num implementation}
     */
    BaseBarSeries(String name, List<Bar> bars, int seriesBeginIndex, int seriesEndIndex, boolean constrained,
            Function<Number, Num> numFunction) {
        this.name = name;

        this.bars = bars;
        if (bars.isEmpty()) {
            // Bar list empty
            // 条形列表为空
            this.seriesBeginIndex = -1;
            this.seriesEndIndex = -1;
            this.constrained = false;
            this.numFunction = numFunction;
            return;
        }
        // Bar list not empty: take Function of first bar
        // 柱列表非空：取第一个柱的函数
        this.numFunction = bars.get(0).getClosePrice().function();
        // Bar list not empty: checking num types
        // 条形列表不为空：检查 num 类型
        if (!checkBars(bars)) {
            throw new IllegalArgumentException(String.format(
                    "Num implementation of bars num执行吧: %s" + " does not match to Num implementation of bar series 与 bar 系列的 Num 实现不匹配: %s",
                    bars.get(0).getClosePrice().getClass(), numFunction));
        }
        // Bar list not empty: checking indexes
        // 柱状列表不为空：检查索引
        if (seriesEndIndex < seriesBeginIndex - 1) {
            throw new IllegalArgumentException("End index must be >= to begin index - 1 结束索引必须 >= 才能开始索引 - 1");
        }
        if (seriesEndIndex >= bars.size()) {
            throw new IllegalArgumentException("End index must be < to the bar list size 结束索引必须 < 到条形列表大小");
        }
        this.seriesBeginIndex = seriesBeginIndex;
        this.seriesEndIndex = seriesEndIndex;
        this.constrained = constrained;
    }

    /**
     * Cuts a list of bars into a new list of bars that is a subset of it
     * * 将一个条形列表剪切成一个新的条形列表，它是它的一个子集
     *
     * @param bars       the list of {@link Bar bars}
     *                   {@link 酒吧列表}
     *
     * @param startIndex start index of the subset
     *                   子集的起始索引
     *
     * @param endIndex   end index of the subset
     *                   子集的结束索引
     *
     * @return a new list of bars with tick from startIndex (inclusive) to endIndex  (exclusive)
     * * @return 一个新的柱线列表，从 startIndex（包括）到 endIndex（不包括）
     */
    private static List<Bar> cut(List<Bar> bars, final int startIndex, final int endIndex) {
        return new ArrayList<>(bars.subList(startIndex, endIndex));
    }

    /**
     * @param series a bar series
     *               酒吧系列
     *
     * @param index  an out of bounds bar index
     *               越界条形索引
     *
     * @return a message for an OutOfBoundsException
     * * @return 针对 OutOfBoundsException 的消息
     */
    private static String buildOutOfBoundsMessage(BaseBarSeries series, int index) {
        return String.format("Size of series 系列尺寸: %s bars, %s bars removed 删除的bars, index = %s", series.bars.size(),
                series.removedBarsCount, index);
    }

    /**
     * Returns a new BaseBarSeries that is a subset of this BaseBarSeries. The new
      series holds a copy of all {@link Bar bars} between <tt>startIndex</tt>
      (inclusive) and <tt>endIndex</tt> (exclusive) of this BaseBarSeries. The
      indices of this BaseBarSeries and the new subset BaseBarSeries can be
      different. I. e. index 0 of the new BaseBarSeries will be index
      <tt>startIndex</tt> of this BaseBarSeries. If <tt>startIndex</tt> <
      this.seriesBeginIndex the new BaseBarSeries will start with the first
      available Bar of this BaseBarSeries. If <tt>endIndex</tt> >
      this.seriesEndIndex+1 the new BaseBarSeries will end at the last available
      Bar of this BaseBarSeries
     * 返回一个新的 BaseBarSeries，它是此 BaseBarSeries 的子集。 新的
     series 拥有 <tt>startIndex</tt> 之间所有 {@link Bar bar} 的副本
     此 BaseBarSeries 的（含）和 <tt>endIndex</tt>（不含）。 这
     此 BaseBarSeries 和新子集 BaseBarSeries 的索引可以是
     不同的。 IE。 新 BaseBarSeries 的索引 0 将是索引
     此 BaseBarSeries 的 <tt>startIndex</tt>。 如果 <tt>startIndex</tt> <
     this.seriesBeginIndex 新的 BaseBarSeries 将从第一个开始
     此 BaseBarSeries 的可用 Bar。 如果 <tt>endIndex</tt> >
     this.seriesEndIndex+1 新的 BaseBarSeries 将在最后一个可用时结束
     这个 BaseBarSeries 的酒吧
     *
     * @param startIndex the startIndex (inclusive)
     *                   startIndex（含）
     *
     * @param endIndex   the endIndex (exclusive)
     *                   endIndex（不包括）
     *
     * @return a new BarSeries with Bars from startIndex to endIndex-1
     * 从 startIndex 到 endIndex-1 的新 BarSeries
     * @throws IllegalArgumentException if endIndex <= startIndex or startIndex < 0
     * * @throws IllegalArgumentException 如果 endIndex <= startIndex 或 startIndex < 0
     */
    @Override
    public BaseBarSeries getSubSeries(int startIndex, int endIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException(String.format("the startIndex 起始索引: %s must not be negative 不能为负", startIndex));
        }
        if (startIndex >= endIndex) {
            throw new IllegalArgumentException(
                    String.format("the endIndex 结束索引: %s must be greater than startIndex 必须大于 startIndex: %s", endIndex, startIndex));
        }
        if (!bars.isEmpty()) {
            int start = Math.max(startIndex - getRemovedBarsCount(), this.getBeginIndex());
            int end = Math.min(endIndex - getRemovedBarsCount(), this.getEndIndex() + 1);
            return new BaseBarSeries(getName(), cut(bars, start, end), numFunction);
        }
        return new BaseBarSeries(name, numFunction);

    }

    @Override
    public Num numOf(Number number) {
        return this.numFunction.apply(number);
    }

    @Override
    public Function<Number, Num> function() {
        return numFunction;
    }

    /**
     * Checks if all {@link Bar bars} of a list fits to the {@link Num NumFunction} used by this bar series.
     * * 检查列表的所有 {@link Bar bars} 是否适合此 bar 系列使用的 {@link Num NumFunction}。
     *
     * @param bars a List of Bar objects.
     *             一个 Bar 对象的列表。
     *
     * @return false if a Num implementation of at least one Bar does not fit.
     * * @return false 如果至少一个 Bar 的 Num 实现不适合。
     */
    private boolean checkBars(List<Bar> bars) {
        for (Bar bar : bars) {
            if (!checkBar(bar)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the {@link Num} implementation of a {@link Bar} fits to the NumFunction used by bar series.
     * * 检查 {@link Bar} 的 {@link Num} 实现是否适合 bar 系列使用的 NumFunction。
     *
     * @param bar a Bar object.
     *            一个 Bar 对象。
     *
     * @return false if another Num implementation is used than by this bar series.
     * * @return false 如果使用了另一个 Num 实现而不是这个 bar 系列。
     * @see Num
     * @see Bar
     * @see #addBar(Duration, ZonedDateTime)
     */
    private boolean checkBar(Bar bar) {
        if (bar.getClosePrice() == null) {
            return true; // bar has not been initialized with data (uses deprecated constructor) // bar 尚未使用数据初始化（使用不推荐使用的构造函数）

        }
        // all other constructors initialize at least the close price, check if Num implementation fits to numFunction
        // 所有其他构造函数至少初始化收盘价，检查 Num 实现是否适合 numFunction
        Class<? extends Num> f = numOf(1).getClass();
        return f == bar.getClosePrice().getClass() || bar.getClosePrice().equals(NaN);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Bar getBar(int i) {
        int innerIndex = i - removedBarsCount;
        if (innerIndex < 0) {
            if (i < 0) {
                // Cannot return the i-th bar if i < 0
                // 如果 i < 0，则无法返回第 i 个柱
                throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i));
            }
            log.trace("Bar series  酒吧系列`{}` ({} bars): bar {} already removed 已删除, use {}-th instead -th 而不是", name, bars.size(), i,
                    removedBarsCount);
            if (bars.isEmpty()) {
                throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, removedBarsCount));
            }
            innerIndex = 0;
        } else if (innerIndex >= bars.size()) {
            // Cannot return the n-th bar if n >= bars.size()
            // 如果 n >= bars.size() 则不能返回第 n 个柱
            throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i));
        }
        return bars.get(innerIndex);
    }

    @Override
    public int getBarCount() {
        if (seriesEndIndex < 0) {
            return 0;
        }
        final int startIndex = Math.max(removedBarsCount, seriesBeginIndex);
        return seriesEndIndex - startIndex + 1;
    }

    @Override
    public List<Bar> getBarData() {
        return bars;
    }

    @Override
    public int getBeginIndex() {
        return seriesBeginIndex;
    }

    @Override
    public int getEndIndex() {
        return seriesEndIndex;
    }

    @Override
    public int getMaximumBarCount() {
        return maximumBarCount;
    }

    @Override
    public void setMaximumBarCount(int maximumBarCount) {
        if (constrained) {
            throw new IllegalStateException("Cannot set a maximum bar count on a constrained bar series 无法在受约束的条形系列上设置最大条数");
        }
        if (maximumBarCount <= 0) {
            throw new IllegalArgumentException("Maximum bar count must be strictly positive 最大柱数必须严格为正");
        }
        this.maximumBarCount = maximumBarCount;
        removeExceedingBars();
    }

    @Override
    public int getRemovedBarsCount() {
        return removedBarsCount;
    }

    /**
     * @param bar the <code>Bar</code> to be added
     *            @param bar 要添加的 <code>Bar</code>
     *
     * @apiNote to add bar data directly use #addBar(Duration, ZonedDateTime, Num, Num, Num, Num, Num)
     * * @apiNote 添加柱数据直接使用#addBar(Duration, ZonedDateTime, Num, Num, Num, Num, Num)
     */
    @Override
    public void addBar(Bar bar, boolean replace) {
        Objects.requireNonNull(bar);
        if (!checkBar(bar)) {
            throw new IllegalArgumentException(
                    String.format("Cannot add Bar with data type: %s to series with data 无法将数据类型为 %s 的 Bar 添加到包含数据的系列" + "type: %s",
                            bar.getClosePrice().getClass(), numOf(1).getClass()));
        }
        if (!bars.isEmpty()) {
            if (replace) {
                bars.set(bars.size() - 1, bar);
                return;
            }
            final int lastBarIndex = bars.size() - 1;
            ZonedDateTime seriesEndTime = bars.get(lastBarIndex).getEndTime();
            if (!bar.getEndTime().isAfter(seriesEndTime)) {
                throw new IllegalArgumentException(
                        String.format("Cannot add a bar with end time 无法添加带有结束时间的条形图:%s that is <= to series end time （即 <= 到系列结束时间）： %s",
                                bar.getEndTime(), seriesEndTime));
            }
        }

        bars.add(bar);
        if (seriesBeginIndex == -1) {
            // Begin index set to 0 only if it wasn't initialized
            // 仅当未初始化时才将开始索引设置为 0
            seriesBeginIndex = 0;
        }
        seriesEndIndex++;
        removeExceedingBars();
    }

    @Override
    public void addBar(Duration timePeriod, ZonedDateTime endTime) {
        this.addBar(new BaseBar(timePeriod, endTime, function()));
    }

    @Override
    public void addBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume) {
        this.addBar(
                new BaseBar(Duration.ofDays(1), endTime, openPrice, highPrice, lowPrice, closePrice, volume, numOf(0)));
    }

    @Override
    public void addBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume,
            Num amount) {
        this.addBar(
                new BaseBar(Duration.ofDays(1), endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount));
    }

    @Override
    public void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume) {
        this.addBar(new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, numOf(0)));
    }

    @Override
    public void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume, Num amount) {
        this.addBar(new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount));
    }

    @Override
    public void addTrade(Number amount, Number price) {
        addTrade(numOf(amount), numOf(price));
    }

    @Override
    public void addTrade(String amount, String price) {
        addTrade(numOf(new BigDecimal(amount)), numOf(new BigDecimal(price)));
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        getLastBar().addTrade(tradeVolume, tradePrice);
    }

    @Override
    public void addPrice(Num price) {
        getLastBar().addPrice(price);
    }

    /**
     * Removes the N first bars which exceed the maximum bar count.
     * * 删除超过最大柱数的前 N 个柱。
     */
    private void removeExceedingBars() {
        int barCount = bars.size();
        if (barCount > maximumBarCount) {
            // Removing old bars
            // 删除旧条
            int nbBarsToRemove = barCount - maximumBarCount;
            for (int i = 0; i < nbBarsToRemove; i++) {
                bars.remove(0);
            }
            // Updating removed bars count
            // 更新移除的柱数
            removedBarsCount += nbBarsToRemove;
        }
    }

}
