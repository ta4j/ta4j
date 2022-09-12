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
package ta4jexamples.loaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;

import com.opencsv.CSVReader;

/**
 * This class builds a Ta4j bar series from a CSV file containing trades.
 * * 此类从包含交易的 CSV 文件构建 Ta4j 条形系列。
 */
public class CsvTradesLoader {

    /**
     * @return the bar series from Bitstamp (bitcoin exchange) trades
     * * @return 来自 Bitstamp（比特币交易所）交易的 bar 系列
     */
    public static BarSeries loadBitstampSeries() {

        // Reading all lines of the CSV file
        // 读取 CSV 文件的所有行
        InputStream stream = CsvTradesLoader.class.getClassLoader()
                .getResourceAsStream("bitstamp_trades_from_20131125_usd.csv");
        CSVReader csvReader = null;
        List<String[]> lines = null;
        try {
            csvReader = new CSVReader(new InputStreamReader(stream, Charset.forName("UTF-8")), ',');
            lines = csvReader.readAll();
            lines.remove(0); // Removing header line // 删除标题行
        } catch (IOException ioe) {
            Logger.getLogger(CsvTradesLoader.class.getName()).log(Level.SEVERE, "Unable to load trades from CSV 无法从 CSV 加载交易", ioe);
        } finally {
            if (csvReader != null) {
                try {
                    csvReader.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }

        BarSeries series = new BaseBarSeries();
        if ((lines != null) && !lines.isEmpty()) {

            // Getting the first and last trades timestamps
            // 获取第一笔和最后一笔交易的时间戳
            ZonedDateTime beginTime = ZonedDateTime
                    .ofInstant(Instant.ofEpochMilli(Long.parseLong(lines.get(0)[0]) * 1000), ZoneId.systemDefault());
            ZonedDateTime endTime = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(lines.get(lines.size() - 1)[0]) * 1000),
                    ZoneId.systemDefault());
            if (beginTime.isAfter(endTime)) {
                Instant beginInstant = beginTime.toInstant();
                Instant endInstant = endTime.toInstant();
                beginTime = ZonedDateTime.ofInstant(endInstant, ZoneId.systemDefault());
                endTime = ZonedDateTime.ofInstant(beginInstant, ZoneId.systemDefault());
                // Since the CSV file has the most recent trades at the top of the file, we'll reverse the list to feed  the List<Bar> correctly.
                // 由于 CSV 文件在文件顶部有最近的交易，我们将反转列表以正确输入 List<Bar>。
                Collections.reverse(lines);
            }
            // build the list of populated bars
            // 建立填充柱的列表
            buildSeries(series, beginTime, endTime, 300, lines);
        }

        return series;
    }

    /**
     * Builds a list of populated bars from csv data.
     * * 从 csv 数据构建填充条的列表。
     *
     * @param beginTime the begin time of the whole period
     *                  整个时段的开始时间
     * @param endTime   the end time of the whole period
     *                  整个周期的结束时间
     * @param duration  the bar duration (in seconds)
     *                  条形持续时间（以秒为单位）
     * @param lines     the csv data returned by CSVReader.readAll()
     *                  CSVReader.readAll() 返回的 csv 数据
     */
    @SuppressWarnings("deprecation")
    private static void buildSeries(BarSeries series, ZonedDateTime beginTime, ZonedDateTime endTime, int duration,
            List<String[]> lines) {

        Duration barDuration = Duration.ofSeconds(duration);
        ZonedDateTime barEndTime = beginTime;
        // line number of trade data
        // 交易数据的行号
        int i = 0;
        do {
            // build a bar
            // 建立一个酒吧
            barEndTime = barEndTime.plus(barDuration);
            Bar bar = new BaseBar(barDuration, barEndTime, series.function());
            do {
                // get a trade
                // 获得一笔交易
                String[] tradeLine = lines.get(i);
                ZonedDateTime tradeTimeStamp = ZonedDateTime
                        .ofInstant(Instant.ofEpochMilli(Long.parseLong(tradeLine[0]) * 1000), ZoneId.systemDefault());
                // if the trade happened during the bar
                // 如果交易发生在柱期间
                if (bar.inPeriod(tradeTimeStamp)) {
                    // add the trade to the bar
                    // 将交易添加到柱
                    double tradePrice = Double.parseDouble(tradeLine[1]);
                    double tradeVolume = Double.parseDouble(tradeLine[2]);
                    bar.addTrade(tradeVolume, tradePrice, series.function());
                } else {
                    // the trade happened after the end of the bar go to the next bar but stay with the same trade (don't increment i) this break will drop us after the inner "while", skipping the increment
                    // 交易发生在柱结束后转到下一个柱，但保持相同的交易（不要增加 i）这个中断将在内部“while”之后放弃我们，跳过增量
                    break;
                }
                i++;
            } while (i < lines.size());
            // if the bar has any trades add it to the bars list this is where the break drops to
            // 如果柱有任何交易，则将其添加到柱列表中，这是突破下降的位置
            if (bar.getTrades() > 0) {
                series.addBar(bar);
            }
        } while (barEndTime.isBefore(endTime));
    }

    public static void main(String[] args) {
        BarSeries series = CsvTradesLoader.loadBitstampSeries();

        System.out.println("Series: " + series.getName() + " (" + series.getSeriesPeriodDescription() + ")");
        System.out.println("Number of bars: " + series.getBarCount());
        System.out.println("First bar: \n" + "\tVolume: " + series.getBar(0).getVolume() + "\n" + "\tNumber of trades: "
                + series.getBar(0).getTrades() + "\n" + "\tClose price: " + series.getBar(0).getClosePrice());
    }
}