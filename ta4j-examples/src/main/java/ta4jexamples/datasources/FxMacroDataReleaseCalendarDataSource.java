/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads FXMacroData economic release-calendar events for event-aware backtests.
 * <p>
 * The data source returns macro and central-bank events such as CPI, payrolls,
 * and policy decisions. Strategies can use the returned dates to skip entries,
 * size down around tier-1 events, or evaluate event-window performance.
 */
public final class FxMacroDataReleaseCalendarDataSource {

    public static final String DEFAULT_BASE_URL = "https://fxmacrodata.com/api/v1";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    public FxMacroDataReleaseCalendarDataSource() {
        this(HttpClient.newHttpClient(), DEFAULT_BASE_URL, System.getenv("FXMACRODATA_API_KEY"));
    }

    public FxMacroDataReleaseCalendarDataSource(HttpClient httpClient, String baseUrl, String apiKey) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
    }

    public List<ReleaseEvent> fetch(String currency, int limit) throws IOException, InterruptedException {
        return fetch(currency, limit, null);
    }

    public List<ReleaseEvent> fetch(String currency, int limit, Integer minTier)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(currency, limit)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("FXMacroData request failed with HTTP status " + response.statusCode());
        }

        JsonObject payload = JsonParser.parseString(response.body()).getAsJsonObject();
        List<ReleaseEvent> events = new ArrayList<>();
        for (JsonElement element : payload.getAsJsonArray("data")) {
            JsonObject row = element.getAsJsonObject();
            int marketTier = getInt(row, "market_tier", 99);
            if (minTier != null && marketTier > minTier) {
                continue;
            }
            events.add(new ReleaseEvent(getString(row, "release"), getString(row, "name"),
                    getString(row, "currency"), getString(row, "date"), parseAnnouncementTime(row), marketTier,
                    getBoolean(row, "top_tier_for_currency"), getString(row, "source"),
                    getString(row, "source_url")));
        }
        return events;
    }

    private URI buildUri(String currency, int limit) {
        StringBuilder query = new StringBuilder("limit=").append(Math.max(1, limit));
        if (apiKey != null && !apiKey.isBlank()) {
            query.append("&api_key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        }
        return URI.create(baseUrl + "/calendar/" + currency.toLowerCase() + "?" + query);
    }

    private static Instant parseAnnouncementTime(JsonObject row) {
        String isoTimestamp = getString(row, "announcement_datetime_utc");
        if (!isoTimestamp.isBlank()) {
            return Instant.parse(isoTimestamp);
        }
        if (row.has("announcement_datetime") && !row.get("announcement_datetime").isJsonNull()) {
            return Instant.ofEpochSecond(row.get("announcement_datetime").getAsLong());
        }
        return LocalDate.parse(getString(row, "date")).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static String getString(JsonObject row, String key) {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : "";
    }

    private static int getInt(JsonObject row, String key, int defaultValue) {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsInt() : defaultValue;
    }

    private static boolean getBoolean(JsonObject row, String key) {
        return row.has(key) && !row.get(key).isJsonNull() && row.get(key).getAsBoolean();
    }

    public static final class ReleaseEvent {
        private final String release;
        private final String name;
        private final String currency;
        private final String date;
        private final Instant announcementTime;
        private final int marketTier;
        private final boolean topTierForCurrency;
        private final String source;
        private final String sourceUrl;

        private ReleaseEvent(String release, String name, String currency, String date, Instant announcementTime,
                int marketTier, boolean topTierForCurrency, String source, String sourceUrl) {
            this.release = release;
            this.name = name;
            this.currency = currency;
            this.date = date;
            this.announcementTime = announcementTime;
            this.marketTier = marketTier;
            this.topTierForCurrency = topTierForCurrency;
            this.source = source;
            this.sourceUrl = sourceUrl;
        }

        public String getRelease() {
            return release;
        }

        public String getName() {
            return name;
        }

        public String getCurrency() {
            return currency;
        }

        public String getDate() {
            return date;
        }

        public Instant getAnnouncementTime() {
            return announcementTime;
        }

        public int getMarketTier() {
            return marketTier;
        }

        public boolean isTopTierForCurrency() {
            return topTierForCurrency;
        }

        public String getSource() {
            return source;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }
    }
}
