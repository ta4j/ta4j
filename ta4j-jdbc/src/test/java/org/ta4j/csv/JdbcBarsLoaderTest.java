/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Lukáš Kvídera
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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcBarsLoaderTest {

  private Connection connection;


  @BeforeEach
  void setUp() throws SQLException {
    this.connection = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "");

    try (final var stmt = this.connection.createStatement()) {
      stmt.execute(
          """
               CREATE TABLE OHLC (
                    TIMESTAMP TIMESTAMP NOT NULL PRIMARY KEY,
                    ASSET TEXT NOT NULL,
                    RESOLUTION TEXT NOT NULL,
                    OPEN DECIMAL(15, 2) NOT NULL,
                    HIGH DECIMAL(15, 2) NOT NULL,
                    LOW DECIMAL(15, 2) NOT NULL,
                    CLOSE DECIMAL(15, 2) NOT NULL,
                    VOLUME BIGINT NOT NULL
                );
              """);
    }
  }


  @Test
  void test() throws SQLException {
    try (final var stmt = this.connection.createStatement()) {
      final var timestamp = Timestamp.from(Instant.now());
      stmt.execute(
          """
              INSERT INTO OHLC (TIMESTAMP, ASSET, RESOLUTION, OPEN, HIGH, LOW, CLOSE, VOLUME)
                        VALUES ('%s', 'GOLD', '1m', 100.0, 105.0, 95.0, 102.0, 1500)
              """.formatted(timestamp));
    }

    final var series = JdbcBarsLoader.load(JdbcContext.builder()
        .connection(this.connection)
        .tableName("OHLC")
        .asset("GOLD")
        .resolution("1m")
        .build());

    assertThat(series.getBarCount()).isEqualTo(1);
  }
}