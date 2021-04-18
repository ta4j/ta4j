/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.zip.DataFormatException;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.mocks.MockTradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

public class XlsTestsUtils {

    /**
     * Returns the first Sheet (mutable) from a workbook with the file name in the
     * test class's resources.
     *
     * @param clazz    class containing the file resources
     * @param fileName file name of the file containing the workbook
     * @return Sheet number zero from the workbook (mutable)
     * @throws IOException if inputStream returned by getResourceAsStream is null or
     *                     if HSSFWorkBook constructor throws IOException or if
     *                     close throws IOException
     */
    private static Sheet getSheet(Class<?> clazz, String fileName) throws IOException {
        InputStream inputStream = clazz.getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new IOException("Null InputStream for file " + fileName);
        }
        HSSFWorkbook workbook = new HSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);
        workbook.close();
        return sheet;
    }

    /**
     * Writes the parameters into the second column of the parameters section of a
     * mutable sheet. The parameters section starts after the parameters section
     * header. There must be at least params.size() rows between the parameters
     * section header and the data section header or part of the data section will
     * be overwritten.
     *
     * @param sheet  mutable Sheet
     * @param params parameters to write
     * @throws DataFormatException if the parameters section header is not found
     */
    private static void setParams(Sheet sheet, Num... params) throws DataFormatException {
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Iterator<Row> iterator = sheet.rowIterator();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            // skip rows with an empty first cell
            if (row.getCell(0) == null) {
                continue;
            }
            // parameters section header is first row with "Param" in first cell
            if (evaluator.evaluate(row.getCell(0)).formatAsString().contains("Param")) {
                // stream parameters into the second column of subsequent rows
                // overwrites data section if there is not a large enough gap
                Arrays.stream(params)
                        .mapToDouble(Num::doubleValue)
                        .forEach(d -> iterator.next().getCell(1).setCellValue(d));
                return;
            }
        }
        // the parameters section header was not found
        throw new DataFormatException("\"Param\" header row not found");
    }

    /**
     * Gets the BarSeries from a file.
     *
     * @param clazz    class containing the file resources
     * @param fileName file name of the file resource
     * @return BarSeries of the data
     * @throws IOException         if getSheet throws IOException
     * @throws DataFormatException if getSeries throws DataFormatException
     */
    public static BarSeries getSeries(Class<?> clazz, String fileName, Function<Number, Num> numFunction)
            throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        return getSeries(sheet, numFunction);
    }

    /**
     * Gets a BarSeries from the data section of a mutable Sheet. Data follows a
     * data section header and appears in the first six columns to the end of the
     * file. Empty cells in the data are forbidden.
     *
     * @param sheet mutable Sheet
     * @return BarSeries of the data
     * @throws DataFormatException if getData throws DataFormatException or if the
     *                             data contains empty cells
     */
    private static BarSeries getSeries(Sheet sheet, Function<Number, Num> numFunction) throws DataFormatException {
        BarSeries series = new BaseBarSeriesBuilder().withNumTypeOf(numFunction).build();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        List<Row> rows = getData(sheet);
        int minInterval = Integer.MAX_VALUE;
        int previousNumber = Integer.MAX_VALUE;
        // find the minimum interval in days
        for (Row row : rows) {
            int currentNumber = (int) evaluator.evaluate(row.getCell(0)).getNumberValue();
            if (previousNumber != Integer.MAX_VALUE) {
                int interval = currentNumber - previousNumber;
                if (interval < minInterval) {
                    minInterval = interval;
                }
            }
            previousNumber = currentNumber;
        }
        Duration duration = Duration.ofDays(minInterval);
        // parse the bars from the data section
        for (Row row : rows) {
            CellValue[] cellValues = new CellValue[6];
            for (int i = 0; i < 6; i++) {
                // empty cells in the data section are forbidden
                if (row.getCell(i) == null) {
                    throw new DataFormatException("empty cell in xls bar series data");
                }
                cellValues[i] = evaluator.evaluate(row.getCell(i));
            }
            // add a bar to the series
            Date endDate = DateUtil.getJavaDate(cellValues[0].getNumberValue());
            ZonedDateTime endDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endDate.getTime()),
                    ZoneId.systemDefault());
            series.addBar(duration, endDateTime,
                    // open, high, low, close, volume
                    numFunction.apply(new BigDecimal(cellValues[1].formatAsString())),
                    numFunction.apply(new BigDecimal(cellValues[2].formatAsString())),
                    numFunction.apply(new BigDecimal(cellValues[3].formatAsString())),
                    numFunction.apply(new BigDecimal(cellValues[4].formatAsString())),
                    numFunction.apply(new BigDecimal(cellValues[5].formatAsString())), numFunction.apply(0));
        }
        return series;
    }

    /**
     * Converts Object parameters into Num parameters and calls getValues on a
     * column of a mutable sheet.
     *
     * @param sheet  mutable Sheet
     * @param column column number of the values to get
     * @param params Object parameters to convert to Num
     * @return List<Num> of values from the column
     * @throws DataFormatException if getValues returns DataFormatException
     */
    private static List<Num> getValues(Sheet sheet, int column, Function<Number, Num> numFunction, Object... params)
            throws DataFormatException {
        Num[] NumParams = Arrays.stream(params)
                .map(p -> numFunction.apply(new BigDecimal(p.toString())))
                .toArray(Num[]::new);
        return getValues(sheet, column, numFunction, NumParams);
    }

    /**
     * Writes the parameters to a mutable Sheet then gets the values from the
     * column.
     *
     * @param sheet  mutable Sheet
     * @param column column number of the values to get
     * @param params Num parameters to write to the Sheet
     * @return List<Num> of values from the column after the parameters have been
     *         written
     * @throws DataFormatException if setParams or getValues throws
     *                             DataFormatException
     */
    private static List<Num> getValues(Sheet sheet, int column, Function<Number, Num> numFunction, Num... params)
            throws DataFormatException {
        setParams(sheet, params);
        return getValues(sheet, column, numFunction);
    }

    /**
     * Gets the values in a column of the data section of a sheet. Rows with an
     * empty first cell are ignored.
     *
     * @param sheet  mutable Sheet
     * @param column column number of the values to get
     * @return List<Num> of values from the column
     * @throws DataFormatException if getData throws DataFormatException
     */
    private static List<Num> getValues(Sheet sheet, int column, Function<Number, Num> numFunction)
            throws DataFormatException {
        List<Num> values = new ArrayList<>();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        // get all of the data from the data section of the sheet
        List<Row> rows = getData(sheet);
        for (Row row : rows) {
            // skip rows where the first cell is empty
            if (row.getCell(column) == null) {
                continue;
            }
            String s = evaluator.evaluate(row.getCell(column)).formatAsString();
            if (s.equals("#DIV/0!")) {
                values.add(NaN.NaN);
            } else {
                values.add(numFunction.apply(new BigDecimal(s)));
            }
        }
        return values;
    }

    /**
     * Gets all data rows in the data section, following the data section header to
     * the end of the sheet. Skips rows that start with "//" as data comments.
     *
     * @param sheet mutable Sheet
     * @return List<Row> of the data rows
     * @throws DataFormatException if the data section header is not found.
     */
    private static List<Row> getData(Sheet sheet) throws DataFormatException {
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Iterator<Row> iterator = sheet.rowIterator();
        boolean noHeader = true;
        List<Row> rows = new ArrayList<Row>();
        // iterate through all rows of the sheet
        while (iterator.hasNext()) {
            Row row = iterator.next();
            // skip rows with an empty first cell
            if (row.getCell(0) == null) {
                continue;
            }
            // after the data section header is found, add all rows that don't
            // have "//" in the first cell
            if (!noHeader) {
                if (evaluator.evaluate(row.getCell(0)).formatAsString().compareTo("\"//\"") != 0) {
                    rows.add(row);
                }
            }
            // if the data section header is not found and this row has "Date"
            // in its first cell, then mark the header as found
            if (noHeader && evaluator.evaluate(row.getCell(0)).formatAsString().contains("Date")) {
                noHeader = false;
            }
        }
        // if the header was not found throw an exception
        if (noHeader) {
            throw new DataFormatException("\"Date\" header row not found");
        }
        return rows;
    }

    /**
     * Gets an Indicator from a column of an XLS file parameters.
     *
     * @param clazz    class containing the file resource
     * @param fileName file name of the file resource
     * @param column   column number of the indicator values
     * @param params   indicator parameters
     * @return Indicator<Num> as calculated by the XLS file given the parameters
     * @throws IOException         if getSheet throws IOException
     * @throws DataFormatException if getSeries or getValues throws
     *                             DataFormatException
     */
    public static Indicator<Num> getIndicator(Class<?> clazz, String fileName, int column,
            Function<Number, Num> numFunction, Object... params) throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        return new MockIndicator(getSeries(sheet, numFunction), getValues(sheet, column, numFunction, params));
    }

    /**
     * Gets the final criterion value from a column of an XLS file given parameters.
     *
     * @param clazz    test class containing the file resources
     * @param fileName file name of the file resource
     * @param column   column number of the calculated criterion values
     * @param params   criterion parameters
     * @return Num final criterion value as calculated by the XLS file given the
     *         parameters
     * @throws IOException         if getSheet throws IOException
     * @throws DataFormatException if getValues throws DataFormatException
     */
    public static Num getFinalCriterionValue(Class<?> clazz, String fileName, int column,
            Function<Number, Num> numFunction, Object... params) throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        List<Num> values = getValues(sheet, column, numFunction, params);
        return values.get(values.size() - 1);
    }

    /**
     * Gets the trading record from an XLS file.
     *
     * @param clazz    the test class containing the file resources
     * @param fileName file name of the file resource
     * @param column   column number of the trading record
     * @return TradingRecord from the file
     * @throws IOException         if getSheet throws IOException
     * @throws DataFormatException if getValues throws DataFormatException
     */
    public static TradingRecord getTradingRecord(Class<?> clazz, String fileName, int column,
            Function<Number, Num> numFunction) throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        return new MockTradingRecord(getValues(sheet, column, numFunction));
    }

}