/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Raw JSON client for FXMacroData's public read/data endpoints.
 */
public final class FxMacroDataClient {

    public static final String DEFAULT_BASE_URL = "https://fxmacrodata.com/api/v1";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final Gson gson = new Gson();

    public FxMacroDataClient() {
        this(HttpClient.newHttpClient(), DEFAULT_BASE_URL, System.getenv("FXMACRODATA_API_KEY"));
    }

    public FxMacroDataClient(HttpClient httpClient, String baseUrl, String apiKey) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey != null && !apiKey.isBlank() ? apiKey : System.getenv("FXMD_API_KEY");
    }

    public JsonObject dataCatalogue(String currency, boolean includeCapabilities, boolean includeCoverage)
            throws IOException, InterruptedException {
        return get("data_catalogue/" + currency.toLowerCase(), params(
                "include_capabilities", includeCapabilities,
                "include_coverage", includeCoverage));
    }

    public JsonObject announcements(String currency, String indicator, LocalDate startDate, LocalDate endDate, int limit)
            throws IOException, InterruptedException {
        return get("announcements/" + currency.toLowerCase() + "/" + indicator, params(
                "start_date", startDate,
                "end_date", endDate,
                "series_mode", "raw",
                "revisions", "latest",
                "official_only", true,
                "limit", limit));
    }

    public JsonObject latestAnnouncements(String currency) throws IOException, InterruptedException {
        return get("announcements/" + currency.toLowerCase() + "/latest", params());
    }

    public JsonObject announcementChanges(String currencies, String indicators, String since, int limit)
            throws IOException, InterruptedException {
        return get("announcements/changes", params(
                "currencies", currencies,
                "indicators", indicators,
                "since", since,
                "limit", limit,
                "payload", "compact"));
    }

    public JsonObject predictions(String currency, String indicator, LocalDate startDate, LocalDate endDate, int limit)
            throws IOException, InterruptedException {
        return get("predictions/" + currency.toLowerCase() + "/" + indicator, params(
                "start_date", startDate,
                "end_date", endDate,
                "limit", limit));
    }

    public JsonObject releaseCalendar(String currency, String indicator, LocalDate startDate, LocalDate endDate,
            String timezone) throws IOException, InterruptedException {
        return get("calendar/" + currency.toLowerCase(), params(
                "indicator", indicator,
                "start_date", startDate,
                "end_date", endDate,
                "timezone", timezone));
    }

    public JsonObject forex(String base, String quote, LocalDate startDate, LocalDate endDate, int limit)
            throws IOException, InterruptedException {
        return get("forex/" + base.toLowerCase() + "/" + quote.toLowerCase(), params(
                "start_date", startDate,
                "end_date", endDate,
                "limit", limit));
    }

    public JsonObject cot(String currency, LocalDate startDate, LocalDate endDate, int limit)
            throws IOException, InterruptedException {
        return get("cot/" + currency.toLowerCase(), params(
                "start_date", startDate,
                "end_date", endDate,
                "limit", limit));
    }

    public JsonObject commodity(String indicator, LocalDate startDate, LocalDate endDate, int limit)
            throws IOException, InterruptedException {
        return get("commodities/" + indicator, params(
                "start_date", startDate,
                "end_date", endDate,
                "limit", limit));
    }

    public JsonObject latestCommodities() throws IOException, InterruptedException {
        return get("commodities/latest", params());
    }

    public JsonObject curves(String currency, String curveFamily, String metric, LocalDate date)
            throws IOException, InterruptedException {
        return get("curves/" + currency.toLowerCase(), params(
                "curve_family", defaultValue(curveFamily, "government_nominal"),
                "metric", defaultValue(metric, "spot"),
                "date", date));
    }

    public JsonObject curveProxies(String currency, String curveFamily, LocalDate date)
            throws IOException, InterruptedException {
        return get("curve_proxies/" + currency.toLowerCase(), params(
                "curve_family", defaultValue(curveFamily, "government_nominal"),
                "date", date));
    }

    public JsonObject forwardCurves(String currency, String curveFamily, String method, LocalDate date)
            throws IOException, InterruptedException {
        return get("forward_curves/" + currency.toLowerCase(), params(
                "curve_family", defaultValue(curveFamily, "government_nominal"),
                "method", defaultValue(method, "derived_from_spot_nodes"),
                "date", date));
    }

    public JsonObject rateDifferentials(String base, String quote, String measure, LocalDate startDate, LocalDate endDate,
            int limit) throws IOException, InterruptedException {
        return get("rate_differentials/" + base.toLowerCase() + "/" + quote.toLowerCase(), params(
                "measure", defaultValue(measure, "auto"),
                "start_date", startDate,
                "end_date", endDate,
                "limit", limit));
    }

    public JsonObject forwardDifferentials(String base, String quote, LocalDate startDate, LocalDate endDate, int limit)
            throws IOException, InterruptedException {
        return get("forward_differentials/" + base.toLowerCase() + "/" + quote.toLowerCase(), params(
                "curve_family", "government_nominal",
                "start_tenor_years", 2,
                "end_tenor_years", 5,
                "start_date", startDate,
                "end_date", endDate,
                "limit", limit));
    }

    public JsonObject marketSessions(String at) throws IOException, InterruptedException {
        return get("market_sessions", params("at", at));
    }

    public JsonObject riskSentiment(LocalDate startDate, LocalDate endDate, int limit)
            throws IOException, InterruptedException {
        return get("risk_sentiment", params(
                "start_date", startDate,
                "end_date", endDate,
                "limit", limit));
    }

    public JsonObject news(String currency, int limit) throws IOException, InterruptedException {
        return get("news/" + currency.toLowerCase(), params("limit", limit));
    }

    public JsonObject pressReleases(String currency, int limit) throws IOException, InterruptedException {
        return get("press-releases/" + currency.toLowerCase(), params("limit", limit));
    }

    public JsonObject graphQl(String query, JsonObject variables) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("query", query);
        payload.add("variables", variables == null ? new JsonObject() : variables);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response);
        return gson.fromJson(response.body(), JsonObject.class);
    }

    private JsonObject get(String path, Map<String, Object> query) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path, query)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response);
        return gson.fromJson(response.body(), JsonObject.class);
    }

    private URI buildUri(String path, Map<String, Object> query) {
        Map<String, Object> params = new LinkedHashMap<>(query);
        if (apiKey != null && !apiKey.isBlank()) {
            params.put("api_key", apiKey);
        }
        String queryString = params.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> encode(entry.getKey()) + "=" + encode(format(entry.getValue())))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        String url = baseUrl + "/" + path;
        return URI.create(queryString.isBlank() ? url : url + "?" + queryString);
    }

    private static Map<String, Object> params(Object... pairs) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            params.put(Objects.toString(pairs[index]), pairs[index + 1]);
        }
        return params;
    }

    private static void ensureSuccess(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 400) {
            throw new IOException("FXMacroData request failed with HTTP status " + response.statusCode());
        }
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String format(Object value) {
        return value instanceof LocalDate ? value.toString() : Objects.toString(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

