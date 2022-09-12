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

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

import org.ta4j.core.num.Num;

/**
 * Sequence of {@link Bar bars} separated by a predefined period (e.g. 15 minutes, 1 day, etc.)
 * * 由预定义时间段分隔的 {@link Bar bar} 序列（例如 15 分钟、1 天等）
 *
 * Notably, a {@link BarSeries bar series} can be:
 * * 值得注意的是，{@link BarSeries bar series} 可以是：
 * <ul>
 * <li>the base of {@link Indicator indicator} calculations
 * <li>constrained between begin and end indexes (e.g. for some backtesting cases)
 * <li>limited to a fixed number of bars (e.g. for actual trading)
 * * <li>{@link Indicator indicator} 计算的基础
 *   * <li>限制在开始和结束索引之间（例如，对于一些回测案例）
 *   * <li>仅限于固定数量的柱（例如，用于实际交易）
 * </ul>
 */
public interface BarSeries extends Serializable {

    /**
     * @return the name of the series
     * * @return 系列名称
     */
    String getName();

    /**
     * @param i an index
     *          一个索引
     * @return the bar at the i-th position
     * * @return 第 i 个位置的条形图
     */
    Bar getBar(int i);

    /**
     * @return the first bar of the series
     * * @return 系列的第一个柱
     */
    default Bar getFirstBar() {
        return getBar(getBeginIndex());
    }

    /**
     * @return the last bar of the series
     * * @return 该系列的最后一个小节
     */
    default Bar getLastBar() {
        return getBar(getEndIndex());
    }

    /**
     * @return the number of bars in the series
     * * @return 序列中的柱数
     */
    int getBarCount();

    /**
     * @return true if the series is empty, false otherwise
     * * @return 如果序列为空，则返回 true，否则返回 false
     */
    default boolean isEmpty() {
        return getBarCount() == 0;
    }

    /**
     * Warning: should be used carefully!
     * 警告：应谨慎使用！
     *
     * Returns the raw bar data. It means that it returns the current List object
      used internally to store the {@link Bar bars}. It may be: - a shortened bar
      list if a maximum bar count has been set - an extended bar list if it is a
      constrained bar series
     返回原始柱数据。 这意味着它返回当前的List对象
     在内部用于存储 {@link Bar bar}。 它可能是： - 缩短的条形图
     如果设置了最大柱数，则列出 - 扩展柱列表，如果它是
     约束杆系列
     *
     * @return the raw bar data
     * * @return 原始柱数据
     */
    List<Bar> getBarData();

    /**
     * @return the begin index of the series
     * * @return 系列的开始索引
     */
    int getBeginIndex();

    /**
     * @return the end index of the series
     * * @return 系列的结束索引
     */
    int getEndIndex();

    /**
     * @return the description of the series period (e.g. "from 12:00 21/01/2014 to 12:15 21/01/2014")
     * * @return 系列周期的描述（例如“从 12:00 21/01/2014 到 12:15 21/01/2014”）
     */
    default String getSeriesPeriodDescription() {
        StringBuilder sb = new StringBuilder();
        if (!getBarData().isEmpty()) {
            Bar firstBar = getFirstBar();
            Bar lastBar = getLastBar();
            sb.append(firstBar.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)).append(" - ")
                    .append(lastBar.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME));
        }
        return sb.toString();
    }

    /**
     * @return the maximum number of bars
     * * @return 最大柱数
     */
    int getMaximumBarCount();

    /**
     * Sets the maximum number of bars that will be retained in the series.
     * * 设置将在系列中保留的最大柱数。
     *
     * If a new bar is added to the series such that the number of bars will exceed
      the maximum bar count, then the FIRST bar in the series is automatically
      removed, ensuring that the maximum bar count is not exceeded.
     如果将新柱添加到系列中，柱的数量将超过
     最大柱数，则系列中的第一个柱自动
     移除，确保不超过最大柱数。
     *
     * @param maximumBarCount the maximum bar count
     *                        最大条数
     */
    void setMaximumBarCount(int maximumBarCount);

    /**
     * @return the number of removed bars
     *      * @return the number of removed bars
     */
    int getRemovedBarsCount();

    /**
     * Adds a bar at the end of the series.
     * 在系列末尾添加一个栏。
     *
     * Begin index set to 0 if it wasn't initialized.<br>
      End index set to 0 if it wasn't initialized, or incremented if it matches the
      end of the series.<br>
      Exceeding bars are removed.
     如果未初始化，则开始索引设置为 0。<br>
     如果未初始化，结束索引设置为 0，如果匹配
     系列结束。<br>
     超出的条将被删除。
     *
     * @param bar the bar to be added
     *            要添加的栏
     * @apiNote use #addBar(Duration, ZonedDateTime, Num, Num, Num, Num, Num) to add   bar data directly
     * * @apiNote 使用#addBar(Duration, ZonedDateTime, Num, Num, Num, Num, Num) 直接添加柱数据
     * @see BarSeries#setMaximumBarCount(int)
     */
    default void addBar(Bar bar) {
        addBar(bar, false);
    }

    /**
     * Adds a bar at the end of the series.
     * * 在系列末尾添加一个栏。
     *
     * Begin index set to 0 if it wasn't initialized.<br>
      End index set to 0 if it wasn't initialized, or incremented if it matches the
      end of the series.<br>
      Exceeding bars are removed.
     如果未初始化，则开始索引设置为 0。<br>
     如果未初始化，结束索引设置为 0，如果匹配
     系列结束。<br>
     超出的条将被删除。
     *
     * @param bar     the bar to be added
     *                要添加的栏
     * @param replace true to replace the latest bar. Some exchange provide continuous new bar data in the time period. (eg. 1s in 1m  Duration)<br>
     *                真要替换最新的吧。 一些交易所在该时间段内提供连续的新柱数据。 （例如，1m 持续时间中的 1 秒）<br>
     * @apiNote use #addBar(Duration, ZonedDateTime, Num, Num, Num, Num, Num) to add  bar data directly
     *      使用#addBar(Duration, ZonedDateTime, Num, Num, Num, Num, Num) 直接添加柱数据
     * @see BarSeries#setMaximumBarCount(int) Maximum Bar Count 最大条数
     */
    void addBar(Bar bar, boolean replace);

    /**
     * Adds a bar at the end of the series.
     * 在系列末尾添加一个栏。
     *
     * @param timePeriod the {@link Duration} of this bar
     *                   此栏的 {@link Duration}
     * @param endTime    the {@link ZonedDateTime end time} of this bar
     *                   此栏的 {@link ZonedDateTime 结束时间}
     */
    void addBar(Duration timePeriod, ZonedDateTime endTime);

    default void addBar(ZonedDateTime endTime, Number openPrice, Number highPrice, Number lowPrice, Number closePrice) {
        this.addBar(endTime, numOf(openPrice), numOf(highPrice), numOf(lowPrice), numOf(closePrice), numOf(0),
                numOf(0));
    }

    default void addBar(ZonedDateTime endTime, Number openPrice, Number highPrice, Number lowPrice, Number closePrice,
            Number volume) {
        this.addBar(endTime, numOf(openPrice), numOf(highPrice), numOf(lowPrice), numOf(closePrice), numOf(volume));
    }

    default void addBar(ZonedDateTime endTime, Number openPrice, Number highPrice, Number lowPrice, Number closePrice,
            Number volume, Number amount) {
        this.addBar(endTime, numOf(openPrice), numOf(highPrice), numOf(lowPrice), numOf(closePrice), numOf(volume),
                numOf(amount));
    }

    default void addBar(Duration timePeriod, ZonedDateTime endTime, Number openPrice, Number highPrice, Number lowPrice,
            Number closePrice, Number volume) {
        this.addBar(timePeriod, endTime, numOf(openPrice), numOf(highPrice), numOf(lowPrice), numOf(closePrice),
                numOf(volume), numOf(0));
    }

    default void addBar(Duration timePeriod, ZonedDateTime endTime, Number openPrice, Number highPrice, Number lowPrice,
            Number closePrice, Number volume, Number amount) {
        this.addBar(timePeriod, endTime, numOf(openPrice), numOf(highPrice), numOf(lowPrice), numOf(closePrice),
                numOf(volume), numOf(amount));
    }

    default void addBar(ZonedDateTime endTime, String openPrice, String highPrice, String lowPrice, String closePrice) {
        this.addBar(endTime, numOf(new BigDecimal(openPrice)), numOf(new BigDecimal(highPrice)),
                numOf(new BigDecimal(lowPrice)), numOf(new BigDecimal(closePrice)), numOf(0), numOf(0));
    }

    default void addBar(ZonedDateTime endTime, String openPrice, String highPrice, String lowPrice, String closePrice,
            String volume) {
        this.addBar(endTime, numOf(new BigDecimal(openPrice)), numOf(new BigDecimal(highPrice)),
                numOf(new BigDecimal(lowPrice)), numOf(new BigDecimal(closePrice)), numOf(new BigDecimal(volume)),
                numOf(0));
    }

    default void addBar(ZonedDateTime endTime, String openPrice, String highPrice, String lowPrice, String closePrice,
            String volume, String amount) {
        this.addBar(endTime, numOf(new BigDecimal(openPrice)), numOf(new BigDecimal(highPrice)),
                numOf(new BigDecimal(lowPrice)), numOf(new BigDecimal(closePrice)), numOf(new BigDecimal(volume)),
                numOf(new BigDecimal(amount)));
    }

    default void addBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume) {
        this.addBar(endTime, openPrice, highPrice, lowPrice, closePrice, volume, numOf(0));
    }

    /**
     * Adds a new <code>Bar</code> to the bar series.
     * * 将新的 <code>Bar</code> 添加到条形系列。
     *
     * @param endTime    end time of the bar
     *                   酒吧的结束时间
     *
     * @param openPrice  the open price
     *                   开盘价
     *
     * @param highPrice  the high/max price
     *                   最高/最高价格
     *
     * @param lowPrice   the low/min price
     *                   最低/最低价格
     *
     * @param closePrice the last/close price
     *                   最后/收盘价
     *
     * @param volume     the volume (default zero)
     *                   音量（默认为零）
     *
     * @param amount     the amount (default zero)
     *                   金额（默认为零）
     */
    void addBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume,
            Num amount);

    /**
     * Adds a new <code>Bar</code> to the bar series.
     * * 将新的 <code>Bar</code> 添加到条形系列。
     *
     * @param endTime    end time of the bar
     *                   酒吧的结束时间
     *
     * @param openPrice  the open price
     *                   开盘价
     *
     * @param highPrice  the high/max price
     *                   最高/最高价格
     *
     * @param lowPrice   the low/min price
     *                   最低/最低价格
     *
     * @param closePrice the last/close price
     *                   最后/收盘价
     *
     * @param volume     the volume (default zero)
     *                   音量（默认为零）
     */
    void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice,
            Num volume);

    /**
     * Adds a new <code>Bar</code> to the bar series.
     *      * Adds a new <code>Bar</code> to the bar series.
     *
     * @param timePeriod the time period of the bar
     *                   酒吧的时间段
     *
     * @param endTime    end time of the bar
     *                   酒吧的结束时间
     *
     * @param openPrice  the open price
     *                   开盘价
     *
     * @param highPrice  the high/max price
     *                   最高/最高价格
     *
     * @param lowPrice   the low/min price
     *                   最低/最低价格
     *
     * @param closePrice the last/close price
     *                   最后/收盘价
     *
     * @param volume     the volume (default zero)
     *                   音量（默认为零）
     *
     * @param amount     the amount (default zero)
     *                   金额（默认为零）
     */
    void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice,
            Num volume, Num amount);

    /**
     * Adds a trade at the end of bar period.
     * 在柱周期结束时添加交易。
     *
     * @param tradeVolume the traded volume
     *                    成交量
     *
     * @param tradePrice  the price
     *                    价格
     */
    default void addTrade(Number tradeVolume, Number tradePrice) {
        addTrade(numOf(tradeVolume), numOf(tradePrice));
    }

    /**
     * Adds a trade at the end of bar period.
     * 在柱周期结束时添加交易。
     *
     * @param tradeVolume the traded volume
     *                    成交量
     *
     * @param tradePrice  the price
     *                    价格
     */
    default void addTrade(String tradeVolume, String tradePrice) {
        addTrade(numOf(new BigDecimal(tradeVolume)), numOf(new BigDecimal(tradePrice)));
    }

    /**
     * Adds a trade at the end of bar period.
     * 在柱周期结束时添加交易。
     *
     * @param tradeVolume the traded volume
     *                    成交量
     * @param tradePrice  the price
     *                    价格
     */
    void addTrade(Num tradeVolume, Num tradePrice);

    /**
     * Adds a price to the last bar
     * * 将价格添加到最后一根柱
     *
     * @param price the price for the bar
     *              * @param price 柱的价格
     */
    void addPrice(Num price);

    default void addPrice(String price) {
        addPrice(new BigDecimal(price));
    }

    default void addPrice(Number price) {
        addPrice(numOf(price));
    }

    /**
     * Returns a new {@link BarSeries} instance that is a subset of this BarSeries
      instance. It holds a copy of all {@link Bar bars} between <tt>startIndex</tt>
      (inclusive) and <tt>endIndex</tt> (exclusive) of this BarSeries. The indices
      of this BarSeries and the new subset BarSeries can be different. I. e. index
      0 of the new BarSeries will be index <tt>startIndex</tt> of this BarSeries.
      If <tt>startIndex</tt> < this.seriesBeginIndex the new BarSeries will start
      with the first available Bar of this BarSeries. If <tt>endIndex</tt> >
      this.seriesEndIndex the new BarSeries will end at the last available Bar of
      this BarSeries
     返回一个新的 {@link BarSeries} 实例，它是此 BarSeries 的子集
     实例。 它包含 <tt>startIndex</tt> 之间所有 {@link Bar bar} 的副本
     此 BarSeries 的（含）和 <tt>endIndex</tt>（不含）。 指数
     这个 BarSeries 和新的子集 BarSeries 可以不同。 IE。 指数
     新 BarSeries 的 0 将是此 BarSeries 的索引 <tt>startIndex</tt>。
     如果 <tt>startIndex</tt> < this.seriesBeginIndex 新的 BarSeries 将开始
     与此 BarSeries 的第一个可用酒吧。 如果 <tt>endIndex</tt> >
     this.seriesEndIndex 新的 BarSeries 将在最后一个可用的 Bar 结束
     这个酒吧系列
     *
     * @param startIndex the startIndex (inclusive)
     *                   startIndex（含）
     *
     * @param endIndex   the endIndex (exclusive)
     *                   endIndex（不包括）
     *
     * @return a new BarSeries with Bars from startIndex to endIndex-1
     * * @return 一个新的 BarSeries，从 startIndex 到 endIndex-1
     *
     * @throws IllegalArgumentException if endIndex <= startIndex or startIndex < 0
     * * @throws IllegalArgumentException 如果 endIndex <= startIndex 或 startIndex < 0
     */
    BarSeries getSubSeries(int startIndex, int endIndex);

    /**
     * Transforms a {@link Number} into the {@link Num implementation} used by this bar series
     * * 将 {@link Number} 转换为此栏系列使用的 {@link Num implementation}
     *
     * @param number a {@link Number} implementing object.
     *               {@link Number} 实现对象。
     *
     * @return the corresponding value as a Num implementing object
     * * @return 对应的值作为 Num 实现对象
     */
    Num numOf(Number number);

    /**
     * Returns the underlying function to transform a Number into the Num implementation used by this bar series
     * * 返回底层函数，将 Number 转换为这个 bar 系列使用的 Num 实现
     *
     * @return a function Number -> Num
     * @return 一个函数 Number -> Num
     */
    Function<Number, Num> function();

}
