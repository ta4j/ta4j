/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource;
import ta4jexamples.datasources.http.DefaultHttpClientWrapper;
import ta4jexamples.datasources.http.HttpClientWrapper;
import ta4jexamples.datasources.http.HttpResponseWrapper;

final class SpdrSectorReferenceDataUpdater {

    static final int DEFAULT_OVERLAP_DAYS = 7;
    static final String UPDATE_REFERENCE_DATA_PROPERTY = "ta4j.lpplUpdateReferenceData";
    static final String REFERENCE_DATA_DIR_PROPERTY = "ta4j.lpplReferenceDataDir";
    static final String DEMO_OUTPUT_DIR_PROPERTY = "ta4j.lpplDemoOutputDir";
    static final String OVERLAP_DAYS_PROPERTY = "ta4j.lpplReferenceOverlapDays";

    private static final Logger LOG = LogManager.getLogger(SpdrSectorReferenceDataUpdater.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime DAILY_DATA_SETTLED_TIME = LocalTime.of(18, 0);

    private final YahooDailyBarFetcher fetcher;

    SpdrSectorReferenceDataUpdater() {
        this(null);
    }

    SpdrSectorReferenceDataUpdater(YahooDailyBarFetcher fetcher) {
        this.fetcher = fetcher;
    }

    RefreshSummary refresh(List<SpdrSectorLpplRotationDemo.SectorDefinition> universe, Settings settings)
            throws IOException {
        if (universe == null || universe.isEmpty()) {
            return new RefreshSummary(List.of(), settings.outputDataDirectory(), settings.responseCacheDirectory());
        }
        Files.createDirectories(settings.outputDataDirectory());
        Files.createDirectories(settings.responseCacheDirectory());

        YahooDailyBarFetcher effectiveFetcher = fetcher != null ? fetcher
                : new YahooFinanceAdjustedDailyBarFetcher(settings.responseCacheDirectory());
        List<TickerRefresh> refreshes = new ArrayList<>(universe.size());
        for (SpdrSectorLpplRotationDemo.SectorDefinition definition : universe) {
            refreshes.add(refreshTicker(definition, settings, effectiveFetcher));
        }
        return new RefreshSummary(refreshes, settings.outputDataDirectory(), settings.responseCacheDirectory());
    }

    private TickerRefresh refreshTicker(SpdrSectorLpplRotationDemo.SectorDefinition definition, Settings settings,
            YahooDailyBarFetcher effectiveFetcher) throws IOException {
        Path sourcePath = settings.referenceDataDirectory().resolve(definition.resource());
        List<ReferenceBar> existingBars = readReferenceBars(sourcePath, definition.resource());
        if (existingBars.isEmpty()) {
            throw new IOException("No existing SPDR reference bars for " + definition.ticker());
        }

        ReferenceBar lastExisting = existingBars.get(existingBars.size() - 1);
        Instant fetchStart = lastExisting.startInstant().minus(Duration.ofDays(settings.overlapDays()));
        Instant fetchEnd = settings.now();
        Path outputPath = settings.outputPath(definition.resource());
        LocalDate previousLastDate = lastExisting.localDate();

        try {
            List<ReferenceBar> fetchedBars = effectiveFetcher.fetch(definition.ticker(), fetchStart, fetchEnd)
                    .stream()
                    .filter(bar -> bar.isCompleteFor(settings.now()))
                    .toList();
            MergeResult merge = merge(existingBars, fetchedBars);
            if (!settings.updateReferenceData() || merge.hasChanges()) {
                writeReferenceBars(outputPath, merge.bars());
            }
            return new TickerRefresh(definition.ticker(), definition.sector(), outputPath, previousLastDate,
                    merge.lastDate(), existingBars.size(), fetchedBars.size(), merge.bars().size(), merge.addedBars(),
                    merge.revisedBars(), false, "");
        } catch (IOException | RuntimeException exception) {
            LOG.warn("Unable to refresh SPDR reference data for {}", definition.ticker(), exception);
            if (!settings.updateReferenceData()) {
                writeReferenceBars(outputPath, existingBars);
            }
            return new TickerRefresh(definition.ticker(), definition.sector(), outputPath, previousLastDate,
                    previousLastDate, existingBars.size(), 0, existingBars.size(), 0, 0, true,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    private static MergeResult merge(List<ReferenceBar> existingBars, List<ReferenceBar> fetchedBars) {
        TreeMap<Long, ReferenceBar> merged = new TreeMap<>();
        for (ReferenceBar bar : existingBars) {
            merged.put(bar.startEpochSecond(), bar);
        }

        int addedBars = 0;
        int revisedBars = 0;
        for (ReferenceBar fetched : fetchedBars) {
            ReferenceBar previous = merged.put(fetched.startEpochSecond(), fetched);
            if (previous == null) {
                addedBars++;
            } else if (!previous.hasSameValues(fetched)) {
                revisedBars++;
            }
        }
        return new MergeResult(List.copyOf(merged.values()), addedBars, revisedBars);
    }

    static List<ReferenceBar> readReferenceBars(Path path, String fallbackResource) throws IOException {
        String json;
        if (Files.exists(path)) {
            json = Files.readString(path, StandardCharsets.UTF_8);
        } else {
            try (java.io.InputStream stream = SpdrSectorReferenceDataUpdater.class.getClassLoader()
                    .getResourceAsStream(fallbackResource)) {
                if (stream == null) {
                    throw new IOException("Missing SPDR reference resource: " + fallbackResource);
                }
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return parseCoinbaseStyleReferenceBars(json);
    }

    static List<ReferenceBar> parseCoinbaseStyleReferenceBars(String json) throws IOException {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray candles = root.getAsJsonArray("candles");
        if (candles == null) {
            throw new IOException("SPDR reference JSON must contain candles");
        }

        TreeMap<Long, ReferenceBar> bars = new TreeMap<>();
        for (JsonElement element : candles) {
            ReferenceBar bar = ReferenceBar.fromCoinbaseStyleJson(element.getAsJsonObject());
            bars.put(bar.startEpochSecond(), bar);
        }
        return List.copyOf(bars.values());
    }

    static void writeReferenceBars(Path path, List<ReferenceBar> bars) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{\n  \"candles\": [\n");
        for (int i = 0; i < bars.size(); i++) {
            ReferenceBar bar = bars.get(i);
            builder.append("    {\n")
                    .append("      \"start\": \"")
                    .append(bar.startEpochSecond())
                    .append("\",\n")
                    .append("      \"low\": \"")
                    .append(formatNumber(bar.low()))
                    .append("\",\n")
                    .append("      \"high\": \"")
                    .append(formatNumber(bar.high()))
                    .append("\",\n")
                    .append("      \"open\": \"")
                    .append(formatNumber(bar.open()))
                    .append("\",\n")
                    .append("      \"close\": \"")
                    .append(formatNumber(bar.close()))
                    .append("\",\n")
                    .append("      \"volume\": \"")
                    .append(formatNumber(bar.volume()))
                    .append("\"\n")
                    .append("    }");
            if (i + 1 < bars.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n}\n");
        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
    }

    private static String formatNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    record Settings(Path referenceDataDirectory, Path outputDirectory, Path responseCacheDirectory,
            boolean updateReferenceData, int overlapDays, Instant now) {

        Settings {
            if (referenceDataDirectory == null) {
                throw new IllegalArgumentException("referenceDataDirectory must not be null");
            }
            if (outputDirectory == null) {
                throw new IllegalArgumentException("outputDirectory must not be null");
            }
            if (responseCacheDirectory == null) {
                throw new IllegalArgumentException("responseCacheDirectory must not be null");
            }
            if (overlapDays < 0) {
                throw new IllegalArgumentException("overlapDays must be non-negative");
            }
            if (now == null) {
                throw new IllegalArgumentException("now must not be null");
            }
            referenceDataDirectory = referenceDataDirectory.toAbsolutePath().normalize();
            outputDirectory = outputDirectory.toAbsolutePath().normalize();
            responseCacheDirectory = responseCacheDirectory.toAbsolutePath().normalize();
        }

        static Settings fromSystemProperties() {
            Path outputDirectory = configuredPath(DEMO_OUTPUT_DIR_PROPERTY,
                    "target/analysis-demos/lppl-sector-rotation");
            Path referenceDataDirectory = configuredPath(REFERENCE_DATA_DIR_PROPERTY,
                    "ta4j-examples/src/main/resources");
            boolean updateReferenceData = Boolean.getBoolean(UPDATE_REFERENCE_DATA_PROPERTY);
            int overlapDays = Integer.getInteger(OVERLAP_DAYS_PROPERTY, DEFAULT_OVERLAP_DAYS);
            return new Settings(referenceDataDirectory, outputDirectory, outputDirectory.resolve("responses"),
                    updateReferenceData, overlapDays, Instant.now());
        }

        private static Path configuredPath(String propertyName, String defaultValue) {
            Path configuredPath = Path.of(System.getProperty(propertyName, defaultValue));
            if (configuredPath.isAbsolute()) {
                return configuredPath;
            }
            return repositoryRoot().resolve(configuredPath);
        }

        private static Path repositoryRoot() {
            String mavenRoot = System.getProperty("maven.multiModuleProjectDirectory");
            if (mavenRoot != null && !mavenRoot.isBlank()) {
                Path root = Path.of(mavenRoot).toAbsolutePath().normalize();
                if (Files.exists(root.resolve("pom.xml"))) {
                    return root;
                }
            }

            Path directory = Path.of("").toAbsolutePath().normalize();
            while (directory != null) {
                if (Files.exists(directory.resolve("pom.xml"))
                        && Files.isDirectory(directory.resolve("ta4j-examples"))) {
                    return directory;
                }
                directory = directory.getParent();
            }
            return Path.of("").toAbsolutePath().normalize();
        }

        Path outputDataDirectory() {
            return updateReferenceData ? referenceDataDirectory : outputDirectory.resolve("reference-data");
        }

        Path outputPath(String resource) {
            return outputDataDirectory().resolve(resource);
        }
    }

    record RefreshSummary(List<TickerRefresh> tickers, Path analysisDataDirectory, Path responseCacheDirectory) {

        RefreshSummary {
            tickers = tickers == null ? List.of() : List.copyOf(tickers);
        }

        String sourceFor(SpdrSectorLpplRotationDemo.SectorDefinition definition) {
            return tickers.stream()
                    .filter(refresh -> refresh.ticker().equals(definition.ticker()))
                    .map(refresh -> refresh.outputPath().toString())
                    .findFirst()
                    .orElse(definition.resource());
        }

    }

    record TickerRefresh(String ticker, String sector, Path outputPath, LocalDate previousLastDate,
            LocalDate newLastDate, int existingBars, int fetchedBars, int mergedBars, int addedBars, int revisedBars,
            boolean skipped, String message) {
    }

    record MergeResult(List<ReferenceBar> bars, int addedBars, int revisedBars) {

        boolean hasChanges() {
            return addedBars > 0 || revisedBars > 0;
        }

        LocalDate lastDate() {
            return bars.isEmpty() ? null : bars.get(bars.size() - 1).localDate();
        }
    }

    record ReferenceBar(long startEpochSecond, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
            BigDecimal volume) {

        ReferenceBar {
            open = normalizedPrice(open, "open");
            high = normalizedPrice(high, "high");
            low = normalizedPrice(low, "low");
            close = normalizedPrice(close, "close");
            volume = normalizedVolume(volume);
        }

        static ReferenceBar fromCoinbaseStyleJson(JsonObject candle) throws IOException {
            return new ReferenceBar(requiredLong(candle, "start"), requiredDecimal(candle, "open"),
                    requiredDecimal(candle, "high"), requiredDecimal(candle, "low"), requiredDecimal(candle, "close"),
                    requiredDecimal(candle, "volume"));
        }

        static ReferenceBar yahooAdjusted(long startEpochSecond, double open, double high, double low, double close,
                double adjustedClose, double volume) {
            double ratio = Double.isFinite(adjustedClose) && adjustedClose > 0.0 && close > 0.0 ? adjustedClose / close
                    : 1.0;
            return new ReferenceBar(startEpochSecond, price(open * ratio), price(high * ratio), price(low * ratio),
                    price(close * ratio), volume(volume));
        }

        Instant startInstant() {
            return Instant.ofEpochSecond(startEpochSecond);
        }

        LocalDate localDate() {
            return startInstant().atZone(MARKET_ZONE).toLocalDate();
        }

        boolean isCompleteFor(Instant now) {
            ZonedDateTime nowInMarket = now.atZone(MARKET_ZONE);
            LocalDate barDate = localDate();
            if (barDate.isBefore(nowInMarket.toLocalDate())) {
                return true;
            }
            return barDate.equals(nowInMarket.toLocalDate())
                    && !nowInMarket.toLocalTime().isBefore(DAILY_DATA_SETTLED_TIME);
        }

        boolean hasSameValues(ReferenceBar other) {
            return startEpochSecond == other.startEpochSecond && open.compareTo(other.open) == 0
                    && high.compareTo(other.high) == 0 && low.compareTo(other.low) == 0
                    && close.compareTo(other.close) == 0 && volume.compareTo(other.volume) == 0;
        }

        private static long requiredLong(JsonObject object, String field) throws IOException {
            JsonElement element = object.get(field);
            if (element == null || element.isJsonNull()) {
                throw new IOException("Missing required SPDR reference field: " + field);
            }
            return element.getAsLong();
        }

        private static BigDecimal requiredDecimal(JsonObject object, String field) throws IOException {
            JsonElement element = object.get(field);
            if (element == null || element.isJsonNull()) {
                throw new IOException("Missing required SPDR reference field: " + field);
            }
            return new BigDecimal(element.getAsString());
        }

        private static BigDecimal normalizedPrice(BigDecimal value, String field) {
            if (value == null || value.signum() <= 0) {
                throw new IllegalArgumentException("SPDR reference " + field + " must be positive");
            }
            return value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        private static BigDecimal normalizedVolume(BigDecimal value) {
            if (value == null || value.signum() < 0) {
                throw new IllegalArgumentException("SPDR reference volume must be non-negative");
            }
            return value.setScale(0, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        private static BigDecimal price(double value) {
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException("SPDR Yahoo price must be finite and positive");
            }
            return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        private static BigDecimal volume(double value) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("SPDR Yahoo volume must be finite and non-negative");
            }
            return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).stripTrailingZeros();
        }
    }

    interface YahooDailyBarFetcher {

        List<ReferenceBar> fetch(String ticker, Instant start, Instant end) throws IOException;
    }

    static final class YahooFinanceAdjustedDailyBarFetcher implements YahooDailyBarFetcher {

        private final HttpClientWrapper httpClient;
        private final Path responseCacheDirectory;

        YahooFinanceAdjustedDailyBarFetcher(Path responseCacheDirectory) {
            this(new DefaultHttpClientWrapper(), responseCacheDirectory);
        }

        YahooFinanceAdjustedDailyBarFetcher(HttpClientWrapper httpClient, Path responseCacheDirectory) {
            if (httpClient == null) {
                throw new IllegalArgumentException("httpClient must not be null");
            }
            if (responseCacheDirectory == null) {
                throw new IllegalArgumentException("responseCacheDirectory must not be null");
            }
            this.httpClient = httpClient;
            this.responseCacheDirectory = responseCacheDirectory.toAbsolutePath().normalize();
        }

        @Override
        public List<ReferenceBar> fetch(String ticker, Instant start, Instant end) throws IOException {
            Files.createDirectories(responseCacheDirectory);
            Path cachePath = cachePath(ticker, start, end);
            String json;
            if (Files.exists(cachePath)) {
                json = Files.readString(cachePath, StandardCharsets.UTF_8);
            } else {
                json = fetchYahooResponse(ticker, start, end);
                Files.writeString(cachePath, json, StandardCharsets.UTF_8);
            }
            return parseYahooAdjustedBars(json, ticker);
        }

        private String fetchYahooResponse(String ticker, Instant start, Instant end) throws IOException {
            try {
                String encodedTicker = URLEncoder.encode(ticker, StandardCharsets.UTF_8);
                String url = YahooFinanceHttpBarSeriesDataSource.YAHOO_FINANCE_API_URL + encodedTicker
                        + "?interval=1d&period1=" + start.getEpochSecond() + "&period2=" + end.getEpochSecond()
                        + "&events=history&includeAdjustedClose=true";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponseWrapper<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException("Yahoo Finance returned HTTP " + response.statusCode() + " for " + ticker);
                }
                return response.body();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching Yahoo Finance data for " + ticker, exception);
            }
        }

        private Path cachePath(String ticker, Instant start, Instant end) {
            LocalDate endDate = end.atZone(ZoneOffset.UTC).toLocalDate();
            String fileName = "YahooFinance-" + ticker.toUpperCase() + "-PT1D-" + start.getEpochSecond() + "_" + endDate
                    + "-lppl-reference.json";
            return responseCacheDirectory.resolve(fileName);
        }

        static List<ReferenceBar> parseYahooAdjustedBars(String json, String ticker) throws IOException {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject chart = root.getAsJsonObject("chart");
            JsonArray results = chart == null ? null : chart.getAsJsonArray("result");
            if (results == null || results.isEmpty()) {
                throw new IOException("No Yahoo chart result for " + ticker);
            }
            JsonObject result = results.get(0).getAsJsonObject();
            JsonArray timestamps = result.getAsJsonArray("timestamp");
            JsonObject indicators = result.getAsJsonObject("indicators");
            JsonArray quotes = indicators == null ? null : indicators.getAsJsonArray("quote");
            if (timestamps == null || quotes == null || quotes.isEmpty()) {
                throw new IOException("Incomplete Yahoo chart result for " + ticker);
            }

            JsonObject quote = quotes.get(0).getAsJsonObject();
            JsonArray opens = quote.getAsJsonArray("open");
            JsonArray highs = quote.getAsJsonArray("high");
            JsonArray lows = quote.getAsJsonArray("low");
            JsonArray closes = quote.getAsJsonArray("close");
            JsonArray volumes = quote.getAsJsonArray("volume");
            JsonArray adjustedCloses = adjustedCloses(indicators);

            List<ReferenceBar> bars = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                if (isMissing(timestamps, i) || isMissing(opens, i) || isMissing(highs, i) || isMissing(lows, i)
                        || isMissing(closes, i)) {
                    continue;
                }
                double rawClose = closes.get(i).getAsDouble();
                double adjustedClose = isMissing(adjustedCloses, i) ? rawClose : adjustedCloses.get(i).getAsDouble();
                double volume = isMissing(volumes, i) ? 0.0 : volumes.get(i).getAsDouble();
                try {
                    bars.add(ReferenceBar.yahooAdjusted(timestamps.get(i).getAsLong(), opens.get(i).getAsDouble(),
                            highs.get(i).getAsDouble(), lows.get(i).getAsDouble(), rawClose, adjustedClose, volume));
                } catch (IllegalArgumentException exception) {
                    LOG.warn("Skipping invalid Yahoo bar for {} at index {}", ticker, i, exception);
                }
            }
            return List.copyOf(bars);
        }

        private static JsonArray adjustedCloses(JsonObject indicators) {
            JsonArray groups = indicators.getAsJsonArray("adjclose");
            if (groups == null || groups.isEmpty()) {
                return null;
            }
            return groups.get(0).getAsJsonObject().getAsJsonArray("adjclose");
        }

        private static boolean isMissing(JsonArray values, int index) {
            return values == null || index >= values.size() || values.get(index).isJsonNull();
        }
    }
}
