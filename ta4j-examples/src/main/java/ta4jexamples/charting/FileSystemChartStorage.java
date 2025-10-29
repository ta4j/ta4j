/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package ta4jexamples.charting;

import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Objects;

/**
 * Persists charts to the filesystem as JPEG images.
 *
 * <p>
 * This storage implementation saves charts directly to a configurable root
 * directory. When a chart title is provided (non-null and non-empty), it is
 * used as the filename (sanitized and with .jpg extension). Otherwise,
 * filenames are automatically generated using the format:
 * {@code <sanitized bar series name>_<start date>_to_<end date>_<current datetime>.jpg}.
 * Bar series start and end dates are formatted as dates only (without time
 * portion). Filenames are sanitized to ensure filesystem compatibility.
 * </p>
 *
 * @since 0.19
 */
final class FileSystemChartStorage implements ChartStorage {

    private static final Logger LOG = LogManager.getLogger(FileSystemChartStorage.class);

    private final Path rootDirectory;

    FileSystemChartStorage(Path rootDirectory) {
        Objects.requireNonNull(rootDirectory, "Root directory must be provided");
        this.rootDirectory = rootDirectory;
    }

    @Override
    public Optional<Path> save(JFreeChart chart, BarSeries series, String chartTitle, int width, int height) {
        Objects.requireNonNull(chart, "Chart cannot be null");
        Objects.requireNonNull(series, "Series cannot be null");

        Path targetPath = (chartTitle != null && !chartTitle.trim().isEmpty()) ? buildSavePath(series, chartTitle)
                : buildSavePath(series);
        try {
            Files.createDirectories(targetPath.getParent());
            ChartUtils.saveChartAsJPEG(targetPath.toFile(), chart, width, height);
            LOG.debug("Saved chart to {}", targetPath.toAbsolutePath());
            return Optional.of(targetPath.toAbsolutePath());
        } catch (IOException ex) {
            LOG.error("Failed to save chart {} to {}", chartTitle, targetPath, ex);
            return Optional.empty();
        }
    }

    private Path buildSavePath(BarSeries series) {
        String sanitizedSeriesName = sanitizePathComponent(series.getName());

        // Get start and end dates (date only, no time)
        String startDate = "unknown";
        String endDate = "unknown";
        if (!series.isEmpty()) {
            startDate = formatDateOnly(series.getFirstBar().getEndTime());
            endDate = formatDateOnly(series.getLastBar().getEndTime());
        }

        // Get current datetime for filename
        String currentDateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        // Build filename: <sanitized bar series name>_<start date>_to_<end
        // date>_<current datetime>.jpg
        String filename = String.format("%s_%s_to_%s_%s.jpg", sanitizedSeriesName, startDate, endDate, currentDateTime);

        return rootDirectory.resolve(filename);
    }

    private Path buildSavePath(BarSeries series, String filename) {
        String sanitizedFilename = sanitizePathComponent(filename);
        return rootDirectory.resolve(sanitizedFilename + ".jpg");
    }

    private String formatDateOnly(Instant instant) {
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String sanitizePathComponent(String component) {
        if (component == null || component.trim().isEmpty()) {
            return "unknown";
        }

        return component.replace(":", "-")
                .replace("/", "_")
                .replace("\\", "_")
                .replace("?", "_")
                .replace("*", "_")
                .replace("<", "(")
                .replace(">", ")")
                .replace("|", "_")
                .replace("\"", "")
                .trim()
                .replaceAll("^\\.+|\\.+$", "")
                .replaceAll("\\s+", "_");
    }
}
