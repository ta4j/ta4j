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
import java.util.HexFormat;
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
            final JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            final BarSeries series = new BaseBarSeriesBuilder().withName(seriesName).build();
            for (final JsonElement element : root.getAsJsonArray("candles")) {
                final JsonObject candle = element.getAsJsonObject();
                final Instant endTime = Instant.ofEpochSecond(candle.get("start").getAsLong()).plus(Duration.ofDays(1));
                series.addBar(series.barBuilder()
                        .timePeriod(Duration.ofDays(1))
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
        } catch (Exception ex) {
            logger.error("Failed to load dataset from {}: {}", resource, ex.getMessage(), ex);
            return null;
        }
    }
}
