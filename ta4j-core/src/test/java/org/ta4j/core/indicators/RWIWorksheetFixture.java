/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

/**
 * Loads the Excel-compatible TSV once for both RWI tests. For each lookback n,
 * ATR starts with the true range at bar n after warm-up, then follows
 * {@code previousATR + (trueRange - previousATR) / n}. The RWI columns select
 * the maximum of the n=2 and n=3 values. The expected columns were calculated
 * independently and rounded to 12 decimal places; the tests compare both the
 * formulas and indicators to those values.
 */
final class RWIWorksheetFixture {

    record Observation(double high, double low, double close, double formulaHigh, double formulaLow,
            double expectedHigh, double expectedLow) {
    }

    private static final List<Observation> ROWS = load();

    private RWIWorksheetFixture() {
    }

    static List<Observation> rows() {
        return ROWS;
    }

    static BarSeries series(NumFactory numFactory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (Observation row : ROWS) {
            series.barBuilder()
                    .openPrice(row.close())
                    .highPrice(row.high())
                    .lowPrice(row.low())
                    .closePrice(row.close())
                    .add();
        }
        return series;
    }

    private static List<Observation> load() {
        InputStream input = RWIWorksheetFixture.class.getResourceAsStream("RWIFormulaReference.tsv");
        if (input == null) {
            throw new IllegalStateException("Missing RWIFormulaReference.tsv");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                HSSFWorkbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("RWI");
            reader.readLine(); // The first line is the spreadsheet header (row 1).
            String line;
            int rowIndex = 1;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                Row row = sheet.createRow(rowIndex++);
                for (int column = 0; column < fields.length; column++) {
                    String field = fields[column];
                    if (!field.isEmpty()) {
                        Cell cell = row.createCell(column);
                        if (field.startsWith("=")) {
                            cell.setCellFormula(field.substring(1));
                        } else {
                            try {
                                cell.setCellValue(Double.parseDouble(field));
                            } catch (NumberFormatException ex) {
                                throw new IllegalStateException("Invalid RWI worksheet value at row "
                                        + (row.getRowNum() + 1) + ", column " + (column + 1) + ": " + field, ex);
                            }
                        }
                    }
                }
            }
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<Observation> observations = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                observations.add(new Observation(value(row, 0, evaluator), value(row, 1, evaluator),
                        value(row, 2, evaluator), value(row, 6, evaluator), value(row, 7, evaluator),
                        value(row, 8, evaluator), value(row, 9, evaluator)));
            }
            return List.copyOf(observations);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double value(Row row, int column, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(column);
        return cell == null ? Double.NaN : evaluator.evaluate(cell).getNumberValue();
    }
}
