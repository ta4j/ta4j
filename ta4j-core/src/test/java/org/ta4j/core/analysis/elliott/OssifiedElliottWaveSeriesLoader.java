/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

/**
 * Loads the frozen study's classpath JSON candles without depending on the
 * examples module's datasource layer.
 */
final class OssifiedElliottWaveSeriesLoader {

    private OssifiedElliottWaveSeriesLoader() {
    }

    static BarSeries loadSeries(final Class<?> resourceOwner, final String resource, final String seriesName,
            final String expectedSha256, final Logger logger) {
        Objects.requireNonNull(resourceOwner, "resourceOwner");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(seriesName, "seriesName");
        Objects.requireNonNull(expectedSha256, "expectedSha256");
        Objects.requireNonNull(logger, "logger");
        final String normalizedResource = resource.startsWith("/") ? resource : "/" + resource;
        try (InputStream stream = resourceOwner.getResourceAsStream(normalizedResource)) {
            if (stream == null) {
                logger.error("Missing resource: {}", resource);
                return null;
            }
            final byte[] bytes = stream.readAllBytes();
            final String actualSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!expectedSha256.equals(actualSha256)) {
                logger.error("Dataset checksum mismatch for {}: expected {}, found {}", resource, expectedSha256,
                        actualSha256);
                return null;
            }
            return parseCandles(bytes, seriesName);
        } catch (Exception ex) {
            logger.error("Failed to load dataset from {}: {}", resource, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Parses the bundled candle JSON layout ({@code {"candles":[{start, open, high,
     * low, close, volume}]}}, {@code start} in epoch seconds) into daily bars.
     * Missing days are allowed, but starts must be distinct and separated by whole
     * multiples of one day. A consistent non-midnight daily offset is preserved.
     *
     * @param bytes      UTF-8 candle JSON
     * @param seriesName name of the created series
     * @return series ordered by candle start
     * @throws IllegalArgumentException when candle starts are incompatible with
     *                                  daily bars
     * @since 0.25.1
     */
    static BarSeries parseCandles(final byte[] bytes, final String seriesName) {
        final JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        final List<JsonObject> candles = new ArrayList<>();
        for (final JsonElement element : root.getAsJsonArray("candles")) {
            candles.add(element.getAsJsonObject());
        }
        candles.sort(Comparator.comparingLong(OssifiedElliottWaveSeriesLoader::startEpochSecond));
        final Duration period = Duration.ofDays(1);
        for (int index = 1; index < candles.size(); index++) {
            final long previous = startEpochSecond(candles.get(index - 1));
            final long current = startEpochSecond(candles.get(index));
            if (current == previous
                    || Math.floorMod(current, period.getSeconds()) != Math.floorMod(previous, period.getSeconds())) {
                throw new IllegalArgumentException("daily candle cadence requires distinct starts separated by whole "
                        + "multiples of " + period.getSeconds() + " seconds; found " + previous + " and " + current);
            }
        }
        final BarSeries series = new BaseBarSeriesBuilder().withName(seriesName).build();
        for (final JsonObject candle : candles) {
            final Instant endTime = Instant.ofEpochSecond(startEpochSecond(candle)).plus(period);
            series.addBar(series.barBuilder()
                    .timePeriod(period)
                    .endTime(endTime)
                    .openPrice(candle.get("open").getAsString())
                    .highPrice(candle.get("high").getAsString())
                    .lowPrice(candle.get("low").getAsString())
                    .closePrice(candle.get("close").getAsString())
                    .volume(candle.get("volume").getAsString())
                    .trades(0)
                    .build());
        }
        return series;
    }

    private static long startEpochSecond(final JsonObject candle) {
        try {
            return candle.get("start").getAsBigDecimal().longValueExact();
        } catch (final ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "daily candle start must be an integer epoch second: " + candle.get("start"), ex);
        }
    }
}
