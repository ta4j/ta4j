/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.csv;

import java.sql.SQLException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.backtest.BacktestBarSeriesBuilder;

/**
 * This class build a Ta4j bar series from a database table containing bars.
 */
public final class JdbcBarsLoader {
  private static final Logger LOG = LoggerFactory.getLogger(JdbcBarsLoader.class);


  private JdbcBarsLoader() {
    // just utility class
  }


  public static BacktestBarSeries load(final JdbcContext context) throws SQLException {
    LOG.trace("load(context={})", context);

    final var series = new BacktestBarSeriesBuilder().withName("DB").build();
    readTable(context, series);
    return series;
  }


  private static void readTable(
      final JdbcContext context,
      final BacktestBarSeries series
  ) throws SQLException {
    try (
        final var statement = context.connection().prepareStatement(
            String.format(
                "SELECT %s, %s, %s, %s, %s, %s FROM %s WHERE %s = ? AND %s = ? ORDER BY %s",
                context.timestampColumnName(),
                context.openColumName(),
                context.highColumnName(),
                context.lowColumnName(),
                context.closeColumnName(),
                context.volumeColumnName(),
                context.tableName(),
                context.assetColumnName(),
                context.resolutionColumnName(),
                context.orderByColumn()
            )
        )
    ) {
      statement.setString(1, context.asset());
      statement.setString(2, context.resolution());
      statement.execute();
      try (final var rs = statement.getResultSet()) {
        while (rs.next()) {
          final var date = rs.getTimestamp(context.timestampColumnName()).toInstant();
          final var open = rs.getBigDecimal(context.openColumName());
          final var high = rs.getBigDecimal(context.highColumnName());
          final var low = rs.getBigDecimal(context.lowColumnName());
          final var close = rs.getBigDecimal(context.closeColumnName());
          final var volume = rs.getBigDecimal(context.volumeColumnName());

          series.barBuilder()
              .timePeriod(Duration.ofDays(1))
              .endTime(date)
              .openPrice(open)
              .closePrice(close)
              .highPrice(high)
              .lowPrice(low)
              .volume(volume)
              .amount(0)
              .add();
        }
      }
    }
  }
}
