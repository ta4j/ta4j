/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource;
import ta4jexamples.datasources.http.DefaultHttpClientWrapper;
import ta4jexamples.datasources.http.HttpClientWrapper;
import ta4jexamples.datasources.http.HttpResponseWrapper;

final class SectorLPPLReferenceDataUpdater {

    private static final Logger LOG = LogManager.getLogger(SectorLPPLReferenceDataUpdater.class);
    private static final Gson GSON = new Gson();
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime DAILY_DATA_SETTLED_TIME = LocalTime.of(18, 0);

    private final YahooDailyBarFetcher fetcher;

    SectorLPPLReferenceDataUpdater() {
        this(null);
    }

    SectorLPPLReferenceDataUpdater(YahooDailyBarFetcher fetcher) {
        this.fetcher = fetcher;
    }

    RefreshSummary refresh(List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> universe, Settings settings)
            throws IOException {
        if (universe == null || universe.isEmpty()) {
            return new RefreshSummary(List.of(), settings.outputDataDirectory(), settings.responseCacheDirectory());
        }
        Files.createDirectories(settings.outputDataDirectory());
        Files.createDirectories(settings.responseCacheDirectory());

        YahooDailyBarFetcher effectiveFetcher = fetcher != null ? fetcher
                : new YahooFinanceAdjustedDailyBarFetcher(settings.responseCacheDirectory());
        List<TickerRefresh> refreshes = new ArrayList<>(universe.size());
        for (SectorLPPLExhaustionMapDemo.InstrumentDefinition definition : universe) {
            refreshes.add(refreshTicker(definition, settings, effectiveFetcher));
        }
        long distinctEndDates = refreshes.stream().map(TickerRefresh::newLastDate).distinct().count();
        if (distinctEndDates > 1) {
            throw new IOException("LPPL reference refresh did not produce a common final session");
        }
        PromotionResult promotion = promoteReferenceData(universe, settings, refreshes);
        return new RefreshSummary(promotion.refreshes(), promotion.analysisDataDirectory(),
                settings.responseCacheDirectory());
    }

    private TickerRefresh refreshTicker(SectorLPPLExhaustionMapDemo.InstrumentDefinition definition, Settings settings,
            YahooDailyBarFetcher effectiveFetcher) {
        Path sourcePath = settings.referenceDataDirectory().resolve(definition.resource());
        Path outputPath = settings.outputPath(definition.resource());
        List<ReferenceBar> existingBars = List.of();
        LocalDate previousLastDate = null;

        try {
            if (Files.exists(sourcePath) || SectorLPPLReferenceDataUpdater.class.getClassLoader()
                    .getResource(definition.resource()) != null) {
                existingBars = readReferenceBars(sourcePath, definition.resource());
            }
            if (!existingBars.isEmpty()) {
                ReferenceBar lastExisting = existingBars.get(existingBars.size() - 1);
                previousLastDate = lastExisting.localDate();
            }
            Instant fetchStart = definition.historyStart().atStartOfDay(MARKET_ZONE).toInstant();
            Instant fetchEnd = settings.now();
            List<ReferenceBar> fetchedBars = effectiveFetcher.fetch(definition.ticker(), fetchStart, fetchEnd)
                    .stream()
                    .filter(bar -> bar.isCompleteFor(settings.now()))
                    .toList();
            if (fetchedBars.size() < 810) {
                throw new IOException("Yahoo refresh returned fewer than 810 complete bars for " + definition.ticker());
            }
            MergeResult merge = replaceAdjustedHistory(existingBars, fetchedBars);
            writeReferenceBars(outputPath, merge.bars());
            LOG.info("Refreshed LPPL reference data for {}: added={} revised={} bars={}", definition.ticker(),
                    merge.addedBars(), merge.revisedBars(), merge.bars().size());
            return new TickerRefresh(definition.ticker(), definition.group(), outputPath, previousLastDate,
                    merge.lastDate(), existingBars.size(), fetchedBars.size(), merge.bars().size(), merge.addedBars(),
                    merge.revisedBars(), false, "");
        } catch (IOException | RuntimeException exception) {
            LOG.warn("Unable to refresh LPPL reference data for {}", definition.ticker(), exception);
            if (!existingBars.isEmpty()) {
                try {
                    writeReferenceBars(outputPath, existingBars);
                } catch (IOException writeException) {
                    exception.addSuppressed(writeException);
                    LOG.warn("Unable to restore existing LPPL reference data for {}", definition.ticker(),
                            writeException);
                }
            }
            return new TickerRefresh(definition.ticker(), definition.group(), outputPath, previousLastDate,
                    previousLastDate, existingBars.size(), 0, existingBars.size(), 0, 0, true,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    static MergeResult replaceAdjustedHistory(List<ReferenceBar> existingBars, List<ReferenceBar> fetchedBars)
            throws IOException {
        if (fetchedBars.isEmpty()) {
            throw new IOException("Yahoo refresh did not return adjusted history");
        }
        if (!existingBars.isEmpty() && (fetchedBars.size() < existingBars.size()
                || fetchedBars.get(0).localDate().isAfter(existingBars.get(0).localDate()))) {
            throw new IOException("Yahoo refresh did not return the complete adjusted history");
        }

        TreeMap<Long, ReferenceBar> existingByStart = new TreeMap<>();
        for (ReferenceBar bar : existingBars) {
            existingByStart.put(bar.startEpochSecond(), bar);
        }

        TreeMap<Long, ReferenceBar> refreshedByStart = new TreeMap<>();
        int addedBars = 0;
        int revisedBars = 0;
        for (ReferenceBar fetched : fetchedBars) {
            if (refreshedByStart.put(fetched.startEpochSecond(), fetched) != null) {
                throw new IOException("Yahoo refresh returned duplicate timestamps");
            }
            ReferenceBar previous = existingByStart.get(fetched.startEpochSecond());
            if (previous == null) {
                addedBars++;
            } else if (!previous.hasSameValues(fetched)) {
                revisedBars++;
            }
        }
        return new MergeResult(List.copyOf(refreshedByStart.values()), addedBars, revisedBars);
    }

    private static PromotionResult promoteReferenceData(List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> universe,
            Settings settings, List<TickerRefresh> refreshes) throws IOException {
        if (!settings.updateReferenceData() || refreshes.stream().anyMatch(TickerRefresh::skipped)) {
            return new PromotionResult(List.copyOf(refreshes), settings.outputDataDirectory());
        }

        List<Path> temporaryFiles = new ArrayList<>(universe.size());
        List<Path> targetFiles = new ArrayList<>(universe.size());
        try {
            for (SectorLPPLExhaustionMapDemo.InstrumentDefinition definition : universe) {
                TickerRefresh refresh = refreshes.stream()
                        .filter(candidate -> candidate.ticker().equals(definition.ticker()))
                        .findFirst()
                        .orElseThrow(() -> new IOException("Missing LPPL refresh result for " + definition.ticker()));
                Path target = settings.referenceDataDirectory().resolve(definition.resource());
                Path targetDirectory = target.getParent();
                if (targetDirectory == null) {
                    throw new IOException("LPPL reference target must have a parent directory: " + target);
                }
                Files.createDirectories(targetDirectory);
                Path temporary = Files.createTempFile(targetDirectory, ".lppl-reference-", ".json");
                temporaryFiles.add(temporary);
                Files.copy(refresh.outputPath(), temporary, StandardCopyOption.REPLACE_EXISTING);
                targetFiles.add(target);
            }
            promoteFilesAtomically(temporaryFiles, targetFiles);
        } finally {
            for (Path temporary : temporaryFiles) {
                Files.deleteIfExists(temporary);
            }
        }

        List<TickerRefresh> promoted = refreshes.stream()
                .map(refresh -> refresh.withOutputPath(settings.referenceDataDirectory()
                        .resolve(universe.stream()
                                .filter(definition -> definition.ticker().equals(refresh.ticker()))
                                .findFirst()
                                .orElseThrow()
                                .resource())))
                .toList();
        return new PromotionResult(promoted, settings.referenceDataDirectory());
    }

    static void promoteFilesAtomically(List<Path> stagedFiles, List<Path> targetFiles) throws IOException {
        if (stagedFiles.size() != targetFiles.size()) {
            throw new IllegalArgumentException("stagedFiles and targetFiles must have equal sizes");
        }
        List<Path> backupFiles = new ArrayList<>(targetFiles.size());
        int promotedCount = 0;
        try {
            for (Path target : targetFiles) {
                Path targetDirectory = target.getParent();
                if (targetDirectory == null) {
                    throw new IOException("LPPL reference target must have a parent directory: " + target);
                }
                if (Files.exists(target)) {
                    Path backup = Files.createTempFile(targetDirectory, ".lppl-backup-", ".json");
                    backupFiles.add(backup);
                    Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    backupFiles.add(null);
                }
            }
            for (int i = 0; i < stagedFiles.size(); i++) {
                Files.move(stagedFiles.get(i), targetFiles.get(i), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                promotedCount++;
            }
        } catch (IOException promotionFailure) {
            for (int i = promotedCount - 1; i >= 0; i--) {
                try {
                    Path backup = backupFiles.get(i);
                    if (backup == null) {
                        Files.deleteIfExists(targetFiles.get(i));
                    } else {
                        Files.move(backup, targetFiles.get(i), StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException rollbackFailure) {
                    promotionFailure.addSuppressed(rollbackFailure);
                }
            }
            throw promotionFailure;
        } finally {
            for (Path backup : backupFiles) {
                if (backup != null) {
                    Files.deleteIfExists(backup);
                }
            }
        }
    }

    static List<ReferenceBar> readReferenceBars(Path path, String fallbackResource) throws IOException {
        String json;
        if (Files.exists(path)) {
            json = Files.readString(path, StandardCharsets.UTF_8);
        } else {
            try (InputStream stream = SectorLPPLReferenceDataUpdater.class.getClassLoader()
                    .getResourceAsStream(fallbackResource)) {
                if (stream == null) {
                    throw new IOException("Missing LPPL reference resource: " + fallbackResource);
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
            throw new IOException("LPPL reference JSON must contain candles");
        }

        List<ReferenceBar> bars = new ArrayList<>(candles.size());
        long previousStart = Long.MIN_VALUE;
        for (JsonElement element : candles) {
            ReferenceBar bar = ReferenceBar.fromCoinbaseStyleJson(element.getAsJsonObject());
            if (bar.startEpochSecond() <= previousStart) {
                throw new IOException("LPPL reference candles must have unique, strictly increasing start times");
            }
            bars.add(bar);
            previousStart = bar.startEpochSecond();
        }
        return List.copyOf(bars);
    }

    static void writeReferenceBars(Path path, List<ReferenceBar> bars) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonArray candles = new JsonArray();
        for (ReferenceBar bar : bars) {
            JsonObject candle = new JsonObject();
            candle.addProperty("start", Long.toString(bar.startEpochSecond()));
            candle.addProperty("low", formatNumber(bar.low()));
            candle.addProperty("high", formatNumber(bar.high()));
            candle.addProperty("open", formatNumber(bar.open()));
            candle.addProperty("close", formatNumber(bar.close()));
            candle.addProperty("volume", formatNumber(bar.volume()));
            candles.add(candle);
        }
        JsonObject root = new JsonObject();
        root.add("candles", candles);
        Files.writeString(path, GSON.toJson(root) + '\n', StandardCharsets.UTF_8);
    }

    private static String formatNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    record Settings(Path referenceDataDirectory, Path outputDirectory, Path responseCacheDirectory,
            boolean updateReferenceData, Instant now) {

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
            if (now == null) {
                throw new IllegalArgumentException("now must not be null");
            }
            referenceDataDirectory = referenceDataDirectory.toAbsolutePath().normalize();
            outputDirectory = outputDirectory.toAbsolutePath().normalize();
            responseCacheDirectory = responseCacheDirectory.toAbsolutePath().normalize();
        }

        Path outputDataDirectory() {
            return outputDirectory.resolve("reference-data");
        }

        Path outputPath(String resource) {
            return outputDataDirectory().resolve(resource);
        }
    }

    record RefreshSummary(List<TickerRefresh> tickers, Path analysisDataDirectory, Path responseCacheDirectory) {

        RefreshSummary {
            tickers = tickers == null ? List.of() : List.copyOf(tickers);
        }

        String sourceFor(SectorLPPLExhaustionMapDemo.InstrumentDefinition definition) {
            return tickers.stream()
                    .filter(refresh -> refresh.ticker().equals(definition.ticker()))
                    .map(refresh -> refresh.outputPath().toString())
                    .findFirst()
                    .orElse(definition.resource());
        }

    }

    record TickerRefresh(String ticker, String group, Path outputPath, LocalDate previousLastDate,
            LocalDate newLastDate, int existingBars, int fetchedBars, int mergedBars, int addedBars, int revisedBars,
            boolean skipped, String message) {

        TickerRefresh withOutputPath(Path outputPath) {
            return new TickerRefresh(ticker, group, outputPath, previousLastDate, newLastDate, existingBars,
                    fetchedBars, mergedBars, addedBars, revisedBars, skipped, message);
        }
    }

    private record PromotionResult(List<TickerRefresh> refreshes, Path analysisDataDirectory) {
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
            if (high.compareTo(low) < 0 || high.compareTo(open) < 0 || high.compareTo(close) < 0
                    || low.compareTo(open) > 0 || low.compareTo(close) > 0) {
                throw new IllegalArgumentException("LPPL reference OHLC values must satisfy low <= open/close <= high");
            }
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
                throw new IOException("Missing required LPPL reference field: " + field);
            }
            return element.getAsLong();
        }

        private static BigDecimal requiredDecimal(JsonObject object, String field) throws IOException {
            JsonElement element = object.get(field);
            if (element == null || element.isJsonNull()) {
                throw new IOException("Missing required LPPL reference field: " + field);
            }
            return new BigDecimal(element.getAsString());
        }

        private static BigDecimal normalizedPrice(BigDecimal value, String field) {
            if (value == null || value.signum() <= 0) {
                throw new IllegalArgumentException("LPPL reference " + field + " must be positive");
            }
            return value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        private static BigDecimal normalizedVolume(BigDecimal value) {
            if (value == null || value.signum() < 0) {
                throw new IllegalArgumentException("LPPL reference volume must be non-negative");
            }
            return value.setScale(0, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        private static BigDecimal price(double value) {
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException("LPPL Yahoo price must be finite and positive");
            }
            return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        }

        private static BigDecimal volume(double value) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("LPPL Yahoo volume must be finite and non-negative");
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

        Path cachePath(String ticker, Instant start, Instant end) {
            String fileName = "YahooFinance-" + ticker.toUpperCase() + "-PT1D-" + start.getEpochSecond() + "_"
                    + end.getEpochSecond() + "-lppl-reference.json";
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
